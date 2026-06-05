package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.BookmarkSource

// Brutalist CrumbsIndexStrip — 24dp four-cell header used by BookmarkCard,
// search-hit headers, and detail screens. Cells: index (accent fill, or
// inverted ink fill for search hits), source label, author, and a free-form
// trailing string ("5H AGO" / "4 hits · 12ms"). Hairline ink dividers.
//
// Signature matches handoff-components.jsx:333-355. `index` is a String so
// callers can pass "247" or "HIT/01"; `source` is the BookmarkSource enum.

@Composable
fun CrumbsIndexStrip(
    index: String,
    source: BookmarkSource,
    author: String,
    trailing: String,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    val indexBg = if (inverted) colors.ink else colors.accent
    val indexFg = if (inverted) colors.accent else colors.onAccent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .testTag("index-strip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IndexStripCell(
            modifier = Modifier
                .background(indexBg)
                .testTag("index-strip-index"),
        ) {
            Text(
                text = index,
                style = typography.metaMono,
                color = indexFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VerticalHairline(stroke.hairline, colors.ink)
        IndexStripCell(modifier = Modifier.testTag("index-strip-source")) {
            Text(
                text = source.displayName().uppercase(),
                style = typography.metaMono,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VerticalHairline(stroke.hairline, colors.ink)
        IndexStripCell(
            modifier = Modifier
                .weight(1f)
                .testTag("index-strip-author"),
            arrangement = Arrangement.Start,
        ) {
            Text(
                text = author,
                style = typography.metaMono,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VerticalHairline(stroke.hairline, colors.ink)
        IndexStripCell(modifier = Modifier.testTag("index-strip-trailing")) {
            val isUnknownTimestamp = trailing == "_"
            Text(
                text = trailing.uppercase(),
                style = typography.metaMono,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isUnknownTimestamp) {
                    Modifier.semantics { contentDescription = "Timestamp unavailable" }
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.IndexStripCell(
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.Center,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = arrangement,
    ) {
        content()
    }
}

@Composable
private fun VerticalHairline(width: androidx.compose.ui.unit.Dp, color: Color) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(color),
    )
}

@Preview(name = "IndexStrip Light", showBackground = true)
@Composable
private fun PreviewIndexStripLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsIndexStrip(
            index = "247",
            source = BookmarkSource.Twitter,
            author = "@architectpatterns",
            trailing = "5h ago",
        )
    }
}

@Preview(name = "IndexStrip Dark", showBackground = true)
@Composable
private fun PreviewIndexStripDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsIndexStrip(
            index = "247",
            source = BookmarkSource.Twitter,
            author = "@architectpatterns",
            trailing = "5h ago",
        )
    }
}

@Preview(name = "IndexStrip Inverted Search Hit", showBackground = true)
@Composable
private fun PreviewIndexStripInverted() {
    CrumbsTheme(darkTheme = false) {
        CrumbsIndexStrip(
            index = "HIT/01",
            source = BookmarkSource.Reddit,
            author = "u/androiddev",
            trailing = "4 hits · 12ms",
            inverted = true,
        )
    }
}
