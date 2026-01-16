package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing

/**
 * Crumbs media carousel with Material 3 HorizontalPager
 *
 * Features:
 * - Horizontal paging for multiple images/videos
 * - Three aspect ratios: Square (1:1), Wide (16:9), Tall (9:16)
 * - Three indicator styles: Dots, Lines, None
 * - Cut-corner shape (bottom-end) for media containers
 * - Theme-aware colors
 * - Coil image loading with placeholder support
 *
 * Component variants for testing:
 * - Aspect ratio: Square/Wide/Tall
 * - Indicator style: Dots/Lines/None
 * - Images only / mixed media
 */

enum class CarouselIndicatorStyle {
    Dots,
    Lines,
    None
}

enum class CarouselAspectRatio(val value: Float) {
    Square(1f),
    Wide(16f / 9f),
    Tall(9f / 16f)
}

enum class MediaType {
    IMAGE,
    VIDEO
}

data class MediaItem(
    val id: String,
    val type: MediaType,
    val url: String,
    val contentDescription: String? = null,
    val duration: Long? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCarousel(
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    onItemClick: ((MediaItem) -> Unit)? = null,
    indicatorStyle: CarouselIndicatorStyle = CarouselIndicatorStyle.Dots,
    aspectRatio: CarouselAspectRatio = CarouselAspectRatio.Wide
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val pagerState = rememberPagerState(pageCount = { items.size })

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val item = items[page]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.value)
                    .then(
                        if (onItemClick != null) {
                            Modifier.clickable { onItemClick(item) }
                        } else Modifier
                    ),
                shape = CrumbsShapes.card,
                color = colors.surfaceVariant
            ) {
                AsyncImage(
                    model = item.url,
                    contentDescription = item.contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Indicator
        if (indicatorStyle != CarouselIndicatorStyle.None && items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                repeat(items.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    when (indicatorStyle) {
                        CarouselIndicatorStyle.Dots -> {
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) colors.accent
                                        else colors.textSecondary.copy(alpha = 0.5f)
                                    )
                            )
                        }

                        CarouselIndicatorStyle.Lines -> {
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = if (isSelected) 24.dp else 16.dp,
                                        height = 4.dp
                                    )
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (isSelected) colors.accent
                                        else colors.textSecondary.copy(alpha = 0.5f)
                                    )
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

// Previews
@Preview(name = "Wide Dots Light", showBackground = true)
@Composable
private fun PreviewCarouselWideDotsLight() {
    CrumbsTheme(darkTheme = false) {
        MediaCarousel(
            items = listOf(
                MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/800x450", "Image 1"),
                MediaItem("2", MediaType.IMAGE, "https://via.placeholder.com/800x450", "Image 2"),
                MediaItem("3", MediaType.IMAGE, "https://via.placeholder.com/800x450", "Image 3")
            ),
            indicatorStyle = CarouselIndicatorStyle.Dots,
            aspectRatio = CarouselAspectRatio.Wide
        )
    }
}

@Preview(name = "Wide Dots Dark", showBackground = true)
@Composable
private fun PreviewCarouselWideDotsDark() {
    CrumbsTheme(darkTheme = true) {
        MediaCarousel(
            items = listOf(
                MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/800x450", "Image 1"),
                MediaItem("2", MediaType.IMAGE, "https://via.placeholder.com/800x450", "Image 2")
            ),
            indicatorStyle = CarouselIndicatorStyle.Dots,
            aspectRatio = CarouselAspectRatio.Wide
        )
    }
}

@Preview(name = "Square Lines Light", showBackground = true)
@Composable
private fun PreviewCarouselSquareLinesLight() {
    CrumbsTheme(darkTheme = false) {
        MediaCarousel(
            items = listOf(
                MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 1"),
                MediaItem("2", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 2"),
                MediaItem("3", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 3")
            ),
            indicatorStyle = CarouselIndicatorStyle.Lines,
            aspectRatio = CarouselAspectRatio.Square
        )
    }
}

@Preview(name = "Tall None Light", showBackground = true)
@Composable
private fun PreviewCarouselTallNoneLight() {
    CrumbsTheme(darkTheme = false) {
        MediaCarousel(
            items = listOf(
                MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/450x800", "Image 1")
            ),
            indicatorStyle = CarouselIndicatorStyle.None,
            aspectRatio = CarouselAspectRatio.Tall
        )
    }
}
