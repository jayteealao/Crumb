package com.github.jayteealao.twitter.models

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * The project's first Room [TypeConverter]. Serializes a tweet media row's HLS /
 * DASH / progressive [Variant] list to a JSON string for the single
 * `video_variants TEXT` column on `tweetMedia` (migration v14 → v15), and back on
 * read.
 *
 * Gson is the existing JSON lib for the Twitter media models, so the on-disk shape
 * matches `{bit_rate, content_type, url}` exactly ([Variant]'s `@SerializedName`
 * keys). An empty list round-trips to `null` so legacy rows (column added with no
 * value) and photo rows (no variants) decode cleanly; the variants-empty backfill
 * query keys off `video_variants IS NULL`. Registered via `@TypeConverters` on
 * `AppDatabase`; the converter must stay deterministic so the exported `15.json`
 * stays stable.
 */
class MediaConverters {
    @TypeConverter
    fun variantsToJson(variants: List<Variant>?): String? =
        if (variants.isNullOrEmpty()) null else GSON.toJson(variants)

    @TypeConverter
    fun jsonToVariants(json: String?): List<Variant>? =
        if (json.isNullOrBlank()) null else GSON.fromJson(json, VARIANT_LIST_TYPE)

    private companion object {
        val GSON = Gson()
        val VARIANT_LIST_TYPE = object : TypeToken<List<Variant>>() {}.type
    }
}
