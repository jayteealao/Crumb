package com.github.jayteealao.crumbs.screens

import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.TypeFilter
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the filter-overlay chip dispatch + selection logic that [HomeRoute] wires into the
 * Tags filter section. The decision logic lives in the pure helpers [dispatchChipToggle],
 * [filterChipIdsFor], and [tagChipId] in HomeScreen.kt so it is testable without a Compose host;
 * [HomeRoute] supplies only the per-tab ViewModel routing around them.
 *
 * Coverage maps to the slice acceptance criteria:
 *  - AC2 (active chips): [filterChipIdsFor] unions the type id with a namespaced id per selected tag.
 *  - AC3 (instant toggle routing): tag ids reach the tag recorder, type ids reach the type recorder.
 *  - AC4 (no id collision): a tag named like a type id stays independently addressable via the prefix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRouteDispatchTest {

    @Test
    fun tagChipToggle_callsOnTagToggled() {
        val tagToggled = mutableListOf<String>()
        val typeToggled = mutableListOf<String>()

        dispatchChipToggle(
            chipId = "tag:kotlin",
            onType = { typeToggled.add(it) },
            onTag = { tagToggled.add(it) },
        )

        assertEquals(
            "A namespaced tag id must route to onTag with the prefix stripped",
            listOf("kotlin"),
            tagToggled,
        )
        assertTrue("A tag toggle must not reach the type path", typeToggled.isEmpty())
    }

    @Test
    fun typeChipToggle_callsOnTypeChipToggled() {
        val tagToggled = mutableListOf<String>()
        val typeToggled = mutableListOf<String>()

        dispatchChipToggle(
            chipId = "article",
            onType = { typeToggled.add(it) },
            onTag = { tagToggled.add(it) },
        )

        assertEquals(
            "A bare type id must route to onType unchanged",
            listOf("article"),
            typeToggled,
        )
        assertTrue("A type toggle must not reach the tag path", tagToggled.isEmpty())
    }

    @Test
    fun selectedChipIds_unionsTagsAndType() {
        val state = FilterState(
            type = TypeFilter.ARTICLE,
            selectedTags = persistentSetOf("kotlin", "android"),
        )

        assertEquals(
            "selectedChipIds must contain the lowercased type id plus a namespaced id per selected tag",
            setOf("article", "tag:kotlin", "tag:android"),
            filterChipIdsFor(state),
        )
    }

    @Test
    fun selectedChipIds_noTags_containsOnlyTypeId() {
        val state = FilterState(type = TypeFilter.ALL)

        assertEquals(
            "With no selected tags the set is just the type id",
            setOf("all"),
            filterChipIdsFor(state),
        )
    }

    @Test
    fun tagId_doesNotCollide_withTypeId() {
        // A user tag named "text" must not alias the TEXT type chip (id "text").
        val tagId = tagChipId("text")
        assertEquals("tag:text", tagId)
        assertFalse("Namespaced tag id must be distinct from the bare type id", tagId == "text")

        val tagRoute = mutableListOf<String>()
        val typeRoute = mutableListOf<String>()

        // The bare type id routes to the type handler...
        dispatchChipToggle("text", onType = { typeRoute.add(it) }, onTag = { tagRoute.add(it) })
        // ...while the namespaced tag id routes to the tag handler with the raw name recovered.
        dispatchChipToggle(tagId, onType = { typeRoute.add(it) }, onTag = { tagRoute.add(it) })

        assertEquals("Bare 'text' is a type chip", listOf("text"), typeRoute)
        assertEquals("'tag:text' is a tag chip resolving to 'text'", listOf("text"), tagRoute)
    }
}
