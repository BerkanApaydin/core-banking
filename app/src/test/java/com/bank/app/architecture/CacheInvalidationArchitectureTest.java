package com.bank.app.architecture;

import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.account.domain.Account;
import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Guards the snapshot-cache invalidation invariant (F-04): {@link Account}
 * status is cached (TTL) at the transfer boundary, so every status-changing
 * path must evict. Manual eviction at each call site is a latent trap — a
 * future SuspendAccountUseCase that forgets to evict would authorize suspended
 * accounts from stale snapshots for up to the TTL.
 *
 * <p>Structural answer: invalidation is owned by the Account boundary
 * ({@code AccountApiAdapter} evicts on every mutation), and these rules fail
 * the build if a new mutation path bypasses it.
 */
@SuppressWarnings("null")
class CacheInvalidationArchitectureTest extends ArchitectureTest {

    @Test
    void accountStatusMutatorsMustEvictSnapshots() {
        // Any production code that suspends/closes an Account must also evict
        // the snapshot cache in the same class — otherwise a stale ACTIVE
        // snapshot keeps authorizing a suspended account until TTL expiry.
        ArchRule rule = classes()
                .that(callAccountStatusMutator())
                .should(evictSnapshots())
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void accountStatusMutatorsMustDeferEvictionToAfterCommit() {
        // Evicting before commit lets a concurrent read-through repopulate the
        // cache from pre-commit (still ACTIVE) state; the stale snapshot then
        // survives the eviction for the full TTL. Status mutators must route
        // eviction through TransactionBoundaryPort.runAfterCommit (which runs
        // immediately when no transaction is active, so tests and schedulers
        // keep working).
        ArchRule rule = classes()
                .that(callAccountStatusMutator())
                .should(deferEvictionToAfterCommit())
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void accountApiMutationsMustEvictSnapshots() {
        // Every non-read AccountApi implementation method must evict: this is
        // what makes invalidation structural instead of manual. A future
        // suspend/close exposure on the published language cannot forget it.
        ArchRule rule = classes()
                .that().implement(AccountApi.class)
                .should(new ArchCondition<>("evict AccountSnapshotCache in every non-read method") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        javaClass.getMethods().stream()
                                .filter(method -> method.getOwner().equals(javaClass))
                                .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                                .filter(method -> !method.getName().startsWith("get"))
                                .forEach(method -> {
                                    if (!callsEvict(method)) {
                                        events.add(SimpleConditionEvent.violated(method,
                                                method.getFullName()
                                                        + " mutates through AccountApi"
                                                        + " without evicting snapshots"));
                                    }
                                });
                    }
                })
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    @Test
    void balanceMutationMayOnlyBeInvokedThroughTheAccountBoundary() {
        // The balance-mutation use case is a public port.in bean, so a future
        // caller could invoke it directly and BYPASS the snapshot eviction
        // that AccountApiAdapter owns. A stale snapshot would then authorize
        // a suspended/moved account for up to the TTL.
        //
        // Structural answer: the use case is reachable only through the
        // Account boundary, which is where the invalidation lives.
        ArchRule rule = classes()
                .that(callBalanceMutationUseCase())
                .should(new ArchCondition<>("invoke the balance mutation only through the Account boundary") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        if (!ACCOUNT_BOUNDARY.equals(javaClass.getName())) {
                            events.add(SimpleConditionEvent.violated(javaClass,
                                    javaClass.getName()
                                            + " calls AdjustAccountBalancesUseCase directly; the AccountApi"
                                            + " boundary owns the snapshot eviction and must be the only caller"));
                        }
                    }
                })
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    /** The Account boundary adapter that owns cache invalidation. */
    private static final String ACCOUNT_BOUNDARY =
            "com.bank.app.account.adapter.out.api.AccountApiAdapter";

    private static DescribedPredicate<JavaClass> callBalanceMutationUseCase() {
        return new DescribedPredicate<>("call AdjustAccountBalancesUseCase") {
            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTargetOwner().getName()
                                .equals(AdjustAccountBalancesUseCase.class.getName()));
            }
        };
    }

    @Test
    void onlyTheInfrastructureModuleMayImplementTheSnapshotCache() {
        // The per-JVM cache must never be reachable in production. An
        // earlier version provided it as a
        // @ConditionalOnMissingBean(AccountSnapshotCache.class) fallback: a
        // deployment that wired no shared backend would silently serve
        // per-pod snapshots — stale account status and currency across the
        // cluster, with nothing in the logs to say so. The fallback is GONE,
        // so the invariant is structural: the only module allowed to
        // provide a CONCRETE cache is the one that owns the shared backends.
        //
        // The abstract base (accountapi.AbstractAccountSnapshotCache) stays
        // in the published-language module on purpose: it carries the
        // shared invalidation semantics, not a storage backend. The
        // per-JVM implementation (InMemoryAccountInfoCacheAdapter) now lives
        // in the transfer module's TEST sources, so it is not among the
        // imported production classes at all.
        ArchRule rule = classes()
                .that().implement(AccountSnapshotCache.class)
                .and(new DescribedPredicate<JavaClass>("are concrete implementations") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return !javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
                                && !javaClass.isInterface();
                    }
                })
                .should().resideInAPackage("..infrastructure..")
                .allowEmptyShould(false);

        rule.check(importedClasses);
    }

    private static DescribedPredicate<JavaClass> callAccountStatusMutator() {
        return new DescribedPredicate<>("call Account.suspend/close") {
            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTargetOwner().getName()
                                .equals(Account.class.getName())
                                && (call.getName().equals("suspend")
                                        || call.getName().equals("close")));
            }
        };
    }

    private static ArchCondition<JavaClass> deferEvictionToAfterCommit() {
        return new ArchCondition<>("defer snapshot eviction to after commit") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean defers = javaClass.getMethodCallsFromSelf().stream()
                        .anyMatch(CacheInvalidationArchitectureTest::isRunAfterCommitCall);
                if (!defers) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName()
                                    + " changes Account status without deferring snapshot eviction"
                                    + " to TransactionBoundaryPort.runAfterCommit"));
                }
            }
        };
    }

    private static boolean isRunAfterCommitCall(JavaMethodCall call) {
        return call.getTargetOwner().getName().equals(TransactionBoundaryPort.class.getName())
                && call.getName().equals("runAfterCommit");
    }

    private static ArchCondition<JavaClass> evictSnapshots() {
        return new ArchCondition<>("evict AccountSnapshotCache") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (javaClass.getMethodCallsFromSelf().stream().noneMatch(
                        CacheInvalidationArchitectureTest::isEvictCall)) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName()
                                    + " changes Account status without evicting AccountSnapshotCache"));
                }
            }
        };
    }

    private static boolean callsEvict(JavaMethod method) {
        if (method.getMethodCallsFromSelf().stream()
                .anyMatch(CacheInvalidationArchitectureTest::isEvictCall)) {
            return true;
        }
        // One-hop helper: public AccountApi mutations may evict through a
        // private same-class helper (e.g. evictSnapshotsAfterCommit) so the
        // AFTER_COMMIT boilerplate lives in one place. The helper itself must
        // still call evictById/evictAll directly (else-branch), which keeps the
        // invariant structural instead of manual.
        JavaClass owner = method.getOwner();
        for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
            if (!call.getTargetOwner().equals(owner)) {
                continue;
            }
            String helperName = call.getName();
            boolean helperEvicts = owner.getMethods().stream()
                    .filter(helper -> helper.getName().equals(helperName))
                    .anyMatch(helper -> helper.getMethodCallsFromSelf().stream()
                            .anyMatch(CacheInvalidationArchitectureTest::isEvictCall));
            if (helperEvicts) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEvictCall(JavaMethodCall call) {
        return call.getTargetOwner().isAssignableTo(AccountSnapshotCache.class)
                && (call.getName().equals("evictById") || call.getName().equals("evictAll"));
    }
}
