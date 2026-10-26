package com.bank.app.common.application.outbox;

/**
 * Matching rules for the event type persisted in an outbox row.
 *
 * <p>New rows store the fully qualified class name, which stays unambiguous
 * when two bounded contexts use the same simple event name. Rows written
 * before that change hold the simple class name; both shapes must resolve
 * during a rolling deployment.
 */
public final class OutboxEventTypes {

    private OutboxEventTypes() {
    }

    public static boolean matches(String expectedSimpleName, String storedEventType) {
        if (storedEventType == null) {
            return false;
        }
        return expectedSimpleName.equals(storedEventType)
                || storedEventType.endsWith("." + expectedSimpleName);
    }

    public static String simpleName(String storedEventType) {
        if (storedEventType == null) {
            return null;
        }
        int separator = storedEventType.lastIndexOf('.');
        return separator < 0 ? storedEventType : storedEventType.substring(separator + 1);
    }
}
