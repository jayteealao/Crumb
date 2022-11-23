package com.github.jayteealao.crumbs.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetTextEntityAnnotation

fun TweetData.toAnnotatedString(): AnnotatedString {
    val urls = emptyList<TweetTextEntityAnnotation>() +
            tweetTextAnnotation.filter {
                !it.url.isNullOrBlank() && it.mediaKey.isNullOrBlank() }
                .sortedBy { it.start }
                .distinctBy { it.displayUrl }

    val mediaUrls= tweetTextAnnotation.filter {
                !it.url.isNullOrBlank() && !it.mediaKey.isNullOrBlank() }
                .mapNotNull { it.url }

    var currentIndex = 0
//    val lastIndex = 0
//    val workIndex = 0..0

    return buildAnnotatedString {
        val string = tweet.text
//        Timber.d(string)
//        var indexCorrection = 0
        for (url in urls) {
            if (currentIndex <= url.start) {
                append(tweet.text.substring(currentIndex until url.start))
            }
            pushStyle(style = SpanStyle(color = Color.Blue))
            append(url.displayUrl!!)
            pop()
            currentIndex = url.end
//            Timber.d("stringLength: ${string.length}, urlLength: ${url.displayUrl.length}, urlStart: ${url.start}, urlEnd: ${url.end} indexCorrection: $indexCorrection")
        }
        if (currentIndex <= string.length) {
            var remains = tweet.text.substring(currentIndex until string.length)
            mediaUrls.forEach {
                remains = remains.replace(it, "")
            }
            append(remains)
        }
//            indexCorrection += url.displayUrl?.length!! - 23

        toAnnotatedString()

    }

//        val test = AnnotatedString(tweet.text).replaceRange()
//        if (urls.isEmpty()) {
//            append(tweet.text)
//        } else {
//            while (currentIndex != tweet.text.length) {
//                append(tweet.text.substring(currentIndex..))
//            }
//        }
}
