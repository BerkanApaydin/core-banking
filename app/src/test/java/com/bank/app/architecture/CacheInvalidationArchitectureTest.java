package com.bank.app.architecture;

import com.bank.app.account.domain.Account;
import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshotCache;
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
