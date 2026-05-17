package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.BookmarkSource

// Brutalist UserProfileDisplay — square avatar (RectangleShape, NOT CircleShape)
// with 1.5dp ink border; mono kicker handle; Funnel Display name; optional
// stats kicker. Click handler optional.

enum class ProfileSize(val avatarSize: Dp) {
    Small(40.dp),
    Medium(56.dp),
    Large(80.dp),
}

data class UserProfile(
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val source: BookmarkSource,
    val verified: Boolean = false,
    val followerCount: Int? = null,
    val postCount: Int? = null,
)

@Composable
fun UserProfileDisplay(
    profile: UserProfile,
    modifier: Modifier = Modifier,
    size: ProfileSize = ProfileSize.Medium,
    showStats: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    var rowMod: Modifier = modifier.testTag("user-profile")
    if (onClick != null) rowMod = rowMod.clickable { onClick() }

    Row(modifier = rowMod, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(size.avatarSize)
                .background(colors.surface)
                .border(stroke.regular, colors.ink, RectangleShape),
        ) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = "${profile.displayName} avatar",
                modifier = Modifier.size(size.avatarSize),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(modifier = Modifier.width(spacing.md))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = profile.displayName,
                style = when (size) {
                    ProfileSize.Small -> typography.bodyMono
                    ProfileSize.Medium, ProfileSize.Large -> typography.displaySmall
                },
                color = colors.ink,
                modifier = Modifier.testTag("user-profile-name"),
            )
            Text(
                text = "@${profile.username}",
                style = typography.captionMono,
                color = colors.onSurfaceVariant,
                modifier = Modifier.testTag("user-profile-handle"),
            )
            if (showStats && (profile.followerCount != null || profile.postCount != null)) {
                Spacer(modifier = Modifier.height(spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    profile.followerCount?.let { count ->
                        Text(
                            text = "${formatCount(count)} FOLLOWERS",
                            style = typography.metaMono,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    profile.postCount?.let { count ->
                        Text(
                            text = "${formatCount(count)} POSTS",
                            style = typography.metaMono,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

@Preview(name = "Medium No Stats Light", showBackground = true)
@Composable
private fun PreviewUserProfileMediumLight() {
    CrumbsTheme(darkTheme = false) {
        UserProfileDisplay(
            profile = UserProfile(
                username = "johndoe",
                displayName = "John Doe",
                avatarUrl = "https://via.placeholder.com/150",
                source = BookmarkSource.Twitter,
            ),
        )
    }
}

@Preview(name = "Medium No Stats Dark", showBackground = true)
@Composable
private fun PreviewUserProfileMediumDark() {
    CrumbsTheme(darkTheme = true) {
        UserProfileDisplay(
            profile = UserProfile(
                username = "johndoe",
                displayName = "John Doe",
                avatarUrl = "https://via.placeholder.com/150",
                source = BookmarkSource.Twitter,
            ),
        )
    }
}

@Preview(name = "Medium With Stats Light", showBackground = true)
@Composable
private fun PreviewUserProfileMediumWithStatsLight() {
    CrumbsTheme(darkTheme = false) {
        UserProfileDisplay(
            profile = UserProfile(
                username = "johndoe",
                displayName = "John Doe",
                avatarUrl = "https://via.placeholder.com/150",
                source = BookmarkSource.Twitter,
                followerCount = 1234,
                postCount = 567,
            ),
            showStats = true,
        )
    }
}
