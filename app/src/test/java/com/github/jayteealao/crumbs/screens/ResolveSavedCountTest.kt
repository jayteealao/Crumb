package com.github.jayteealao.crumbs.screens

import com.github.jayteealao.crumbs.designsystem.components.BottomNavTab
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the link-state gate on the SAVED-header count: the Twitter-backed tabs (Twitter/All/Map)
 * report the live count only while `sync_status.linked` is true, so leftover cached rows read
 * `000 SAVED` in exactly the states where the feed body shows "CONNECT TO TWITTER". Reddit has
 * no count wired and reports 0 regardless of link state.
 *
 * Also pins the `countLabel` incompleteness signal: when `isSyncIncomplete = true` the header
 * shows "NNN CATCHING UP" instead of "NNN SAVED" so a partial corpus is never presented as
 * authoritative.
 */
class ResolveSavedCountTest {
    @Test
    fun unlinked_twitterBackedTabs_reportZero() {
        for (tab in listOf(BottomNavTab.TWITTER, BottomNavTab.ALL, BottomNavTab.MAP)) {
            assertEquals(0, resolveSavedCount(tab, twitterCount = 12, twitterLinked = false))
        }
        // Zero count renders `000 SAVED` via the existing countLabel formatting.
        assertEquals("000 SAVED", HomeUiState(itemCount = 0).countLabel)
    }

    @Test
    fun linked_twitterBackedTabs_passCountThrough() {
        for (tab in listOf(BottomNavTab.TWITTER, BottomNavTab.ALL, BottomNavTab.MAP)) {
            assertEquals(42, resolveSavedCount(tab, twitterCount = 42, twitterLinked = true))
        }
    }

    @Test
    fun reddit_reportsZeroInBothLinkStates() {
        assertEquals(0, resolveSavedCount(BottomNavTab.REDDIT, twitterCount = 42, twitterLinked = true))
        assertEquals(0, resolveSavedCount(BottomNavTab.REDDIT, twitterCount = 42, twitterLinked = false))
    }

    // AC3: incompleteness signal — countLabel shows "NNN CATCHING UP" when isSyncIncomplete.
    @Test
    fun syncIncomplete_countLabel_showsCatchingUp() {
        assertEquals(
            "042 CATCHING UP",
            HomeUiState(itemCount = 42, isSyncIncomplete = true).countLabel,
        )
    }

    @Test
    fun syncComplete_countLabel_showsSaved() {
        assertEquals(
            "042 SAVED",
            HomeUiState(itemCount = 42, isSyncIncomplete = false).countLabel,
        )
    }

    @Test
    fun syncIncomplete_zeroCount_showsCatchingUp() {
        assertEquals(
            "000 CATCHING UP",
            HomeUiState(itemCount = 0, isSyncIncomplete = true).countLabel,
        )
    }
}
