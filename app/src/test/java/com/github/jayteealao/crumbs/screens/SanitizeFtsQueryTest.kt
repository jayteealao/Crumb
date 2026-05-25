package com.github.jayteealao.crumbs.screens

import com.github.jayteealao.crumbs.data.SearchRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Parameterized unit tests for the private `sanitizeFtsQuery` function inside
 * [SearchRepository]. The function is pure (no I/O, no coroutines) so plain
 * JUnit4 is used — no Robolectric overhead needed.
 *
 * Access is via reflection because the function is `private`. The tests assert
 * that every user-supplied string is turned into a safe SQLite FTS MATCH phrase
 * by wrapping it in double-quotes and doubling any inner double-quote characters.
 */
@RunWith(Parameterized::class)
class SanitizeFtsQueryTest(
    private val description: String,
    private val input: String,
    private val expected: String,
) {

    companion object {
        /**
         * Each row: (test description, raw user input, expected sanitized output).
         *
         * Rule applied by sanitizeFtsQuery:
         *   1. Replace every `"` with `""`  (escape inner quotes)
         *   2. Wrap result in `"..."`         (phrase-quote the whole expression)
         */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> = listOf(
            // 1. Normal single word — passes through wrapped in phrase quotes
            arrayOf(
                "normal word is wrapped in phrase quotes",
                "compose",
                "\"compose\"",
            ),
            // 2. Empty string — produces empty phrase quote ("")
            arrayOf(
                "empty string produces empty phrase quote",
                "",
                "\"\"",
            ),
            // 3. FTS wildcard * — literal, not interpreted as prefix operator
            arrayOf(
                "wildcard * is treated as a literal character",
                "compose*",
                "\"compose*\"",
            ),
            // 4. FTS negation - — literal, not interpreted as NOT operator
            arrayOf(
                "negation dash - is treated as a literal character",
                "-android",
                "\"-android\"",
            ),
            // 5. FTS AND keyword — entire string is one literal phrase
            arrayOf(
                "AND keyword is treated as a literal phrase token",
                "compose AND kotlin",
                "\"compose AND kotlin\"",
            ),
            // 6. FTS OR keyword — entire string is one literal phrase
            arrayOf(
                "OR keyword is treated as a literal phrase token",
                "compose OR kotlin",
                "\"compose OR kotlin\"",
            ),
            // 7. Inner double-quote — escaped by doubling ("" inside the phrase)
            arrayOf(
                "inner double-quote is escaped by doubling",
                "foo\"bar",
                "\"foo\"\"bar\"",
            ),
            // 8. Column selector syntax — colon is literal inside phrase quotes
            arrayOf(
                "column selector syntax colon is treated as literal",
                "title:compose",
                "\"title:compose\"",
            ),
            // 9. Unicode / emoji — multi-codepoint input passes through intact
            arrayOf(
                "unicode and emoji input passes through intact",
                "bookmarks 📚 kotlin",
                "\"bookmarks 📚 kotlin\"",
            ),
            // 10. Whitespace-only input — spaces survive inside phrase quotes
            arrayOf(
                "whitespace-only input is wrapped without trimming",
                "   ",
                "\"   \"",
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Infrastructure — reflective access to the private method
    // -----------------------------------------------------------------------

    private val repository: SearchRepository by lazy {
        // DAOs are never called in these tests; mockk stubs satisfy the
        // constructor without triggering any real DB access.
        SearchRepository(
            tweetFtsDao = mockk(relaxed = true),
            redditFtsDao = mockk(relaxed = true),
        )
    }

    private fun invokeSanitize(query: String): String {
        val method = SearchRepository::class.java
            .getDeclaredMethod("sanitizeFtsQuery", String::class.java)
        method.isAccessible = true
        return method.invoke(repository, query) as String
    }

    // -----------------------------------------------------------------------
    // Single parameterized test entry point
    // -----------------------------------------------------------------------

    @Test
    fun sanitizeFtsQuery_producesExpectedOutput() {
        val actual = invokeSanitize(input)
        assertEquals(
            "sanitizeFtsQuery(\"$input\"): expected <$expected> but got <$actual>",
            expected,
            actual,
        )
    }
}
