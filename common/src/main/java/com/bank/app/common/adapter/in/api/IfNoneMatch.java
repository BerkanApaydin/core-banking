package com.bank.app.common.adapter.in.api;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 9110 {@code If-None-Match} matching for conditional GET responses.
 *
 * <p>The header is a comma-separated list of entity-tags (or {@code *}), and
 * GET/HEAD use the weak comparison function. Comparing the raw header with the
 * current ETag only works for a single exact value; a valid list such as
 * {@code W/"old", W/"current"} would incorrectly return 200 instead of 304.
 */
public final class IfNoneMatch {

    private IfNoneMatch() {
    }

    public static boolean matches(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || currentEtag == null) {
            return false;
        }
        String header = ifNoneMatch.trim();
        if (header.isEmpty()) {
            return false;
        }
        String currentOpaqueTag = opaqueTag(currentEtag);
        if (currentOpaqueTag == null) {
            return false;
        }
        if ("*".equals(header)) {
            return true;
        }
        for (String candidate : splitEntityTags(header)) {
            String candidateOpaqueTag = opaqueTag(candidate);
            if (candidateOpaqueTag != null && candidateOpaqueTag.equals(currentOpaqueTag)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitEntityTags(String header) {
        List<String> tags = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                current.append(c);
            } else if (c == ',' && !quoted) {
                tags.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        tags.add(current.toString());
        return tags;
    }

    private static String opaqueTag(String entityTag) {
        String value = entityTag.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2).trim();
        }
        if (value.length() < 2 || value.charAt(0) != '"'
                || value.charAt(value.length() - 1) != '"') {
            return null;
        }
        return value;
    }
}
