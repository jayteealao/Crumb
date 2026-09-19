package com.github.jayteealao.twitter.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Single, thread-safe formatter for the legacy X/Twitter v1.1 timestamp form
 * (`Wed Sep 16 17:51:39 +0000 2020`). `xx` parses the `+0000` offset; English locale is
 * required for the weekday/month names.
 */
private val V1_1_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss xx yyyy", Locale.ENGLISH)

/**
 * Parses the timestamp formats present in the bookmark corpus into epoch millis.
 *
 * The X v2 API emits ISO-8601 (`2026-05-13T12:22:37.000Z`); legacy v1.1 docs use
 * `Wed Sep 16 17:51:39 +0000 2020`. A single formatter cannot parse both, so this tries
 * ISO (with and without an explicit offset) first, then the v1.1 form, and returns `null`
 * when neither matches — the caller maps `null` to the unknown-time sentinel rather than
 * fabricating "now".
 *
 * Relies on `java.time`, available on minSdk 24 via core library desugaring (see
 * `feature/twitter/build.gradle`).
 */
fun parseTweetTimestamp(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null

    // ISO-8601 with a trailing 'Z' (the X v2 form). Instant.parse is the strictest, cheapest path.
    try {
        return Instant.parse(raw).toEpochMilli()
    } catch (_: DateTimeParseException) {
        // not a Zulu instant — try the other shapes
    }

    // ISO-8601 carrying an explicit numeric offset (e.g. `+00:00`).
    try {
        return OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        // not offset-ISO — try the legacy form
    }

    // Legacy v1.1 (`EEE MMM dd HH:mm:ss xx yyyy`).
    return try {
        OffsetDateTime.parse(raw, V1_1_FORMATTER).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
