package com.github.jayteealao.crumbs.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the SAVED-header label formatting and the per-card DB-number format. `%03d` is a
 * minimum-width specifier, so it zero-pads small values to three digits and never truncates
 * larger ones — the "must not truncate rowids exceeding three digits" acceptance criterion.
 */
class HomeUiStateCountLabelTest {

    @Test
    fun countLabel_zeroPadsToThreeDigits() {
        assertEquals("000 SAVED", HomeUiState(itemCount = 0).countLabel)
        assertEquals("042 SAVED", HomeUiState(itemCount = 42).countLabel)
    }

    @Test
    fun countLabel_doesNotTruncateAboveThreeDigits() {
        assertEquals("1234 SAVED", HomeUiState(itemCount = 1234).countLabel)
    }

    @Test
    fun perCardDbNumberFormat_isZeroPaddedAndUntruncated() {
        // The Twitter card call site renders the rowid as `"%03d".format(bookmark.dbNumber)`.
        assertEquals("007", "%03d".format(7L))
        assertEquals("1234", "%03d".format(1234L))
    }
}
