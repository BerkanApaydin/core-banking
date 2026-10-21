package com.bank.app.common.adapter.in.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IfNoneMatchTest {

    private static final String CURRENT = "W/\"transfer-7-5\"";

    @Test
    void shouldMatchSingleWeakEntityTag() {
        assertThat(IfNoneMatch.matches(CURRENT, CURRENT)).isTrue();
    }

    @Test
    void shouldMatchAnyValue() {
        assertThat(IfNoneMatch.matches("*", CURRENT)).isTrue();
    }

    @Test
    void shouldMatchEntryInCommaSeparatedList() {
        assertThat(IfNoneMatch.matches("W/\"old\", " + CURRENT + ", W/\"other\"", CURRENT))
                .isTrue();
    }

    @Test
    void shouldUseWeakComparisonForGet() {
        assertThat(IfNoneMatch.matches("\"transfer-7-5\"", CURRENT)).isTrue();
        assertThat(IfNoneMatch.matches("W/\"transfer-7-5\"", "\"transfer-7-5\"")).isTrue();
    }

    @Test
    void shouldNotSplitOnCommaInsideQuotedOpaqueTag() {
        String etag = "W/\"report-a,b\"";
        assertThat(IfNoneMatch.matches("W/\"other\", " + etag, etag)).isTrue();
    }

    @Test
    void shouldRejectMissingEmptyOrMalformedHeaders() {
        assertThat(IfNoneMatch.matches(null, CURRENT)).isFalse();
        assertThat(IfNoneMatch.matches("", CURRENT)).isFalse();
        assertThat(IfNoneMatch.matches("transfer-7-5", CURRENT)).isFalse();
        assertThat(IfNoneMatch.matches("W/\"other\"", CURRENT)).isFalse();
    }

    @Test
    void shouldRejectNullOrMalformedCurrentEtag() {
        assertThat(IfNoneMatch.matches("*", null)).isFalse();
        assertThat(IfNoneMatch.matches("*", "transfer-7-5")).isFalse();
    }
}
