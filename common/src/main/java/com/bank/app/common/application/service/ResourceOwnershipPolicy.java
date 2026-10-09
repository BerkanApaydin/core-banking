package com.bank.app.common.application.service;

import com.bank.app.common.domain.exception.AuthorizationException;

/**
 * Shared resource-ownership policy behind the bounded-context authorization
 * services.
 *
 * <p>{@code TransferAuthorizationService} and
 * {@code AccountAuthorizationService} were thin duplicate wrappers over the
 * same {@code UserContextService.checkUserAuthorization} calls. Ownership and
 * participation checks now live here once; the context services keep only
 * their domain-specific lookups and message literals (client-visible texts
 * stay owned by the bounded context that emits them).
 */
public class ResourceOwnershipPolicy {

    private final UserContextService userContextService;

    public ResourceOwnershipPolicy(UserContextService userContextService) {
        this.userContextService = userContextService;
    }

    /**
     * Throws unless the current user owns the resource.
     */
    public void requireOwner(Long resourceUserId, String errorMessage) {
        userContextService.checkUserAuthorization(resourceUserId, errorMessage);
    }

    /**
     * Returns the current user id or throws the caller-supplied failure
     * (message literals stay with the bounded context).
     */
    public Long currentUserIdOrThrow(AuthorizationException unauthenticated) {
        return userContextService.getCurrentUserId().orElseThrow(() -> unauthenticated);
    }

    /**
     * Throws unless the current user is one of the two participants.
     */
    public void requireParticipant(Long firstUserId, Long secondUserId,
                                   AuthorizationException unauthenticated,
                                   AuthorizationException notParticipant) {
        Long currentUserId = currentUserIdOrThrow(unauthenticated);
        if (!currentUserId.equals(firstUserId) && !currentUserId.equals(secondUserId)) {
            throw notParticipant;
        }
    }

    /**
     * Username for audit trails and cache keys ({@code "system"} fallback).
     */
    public String currentUsernameOrSystem() {
        return userContextService.getCurrentUsernameOrSystem();
    }
}
