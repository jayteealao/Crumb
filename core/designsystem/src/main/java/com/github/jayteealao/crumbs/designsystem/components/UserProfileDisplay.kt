package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Crumbs user profile display component
 *
 * Features:
 * - Displays user avatar, name, username, and optional stats
 * - Three sizes: Small (40dp avatar), Medium (56dp), Large (80dp)
 * - Circular avatar with Coil image loading
 * - Optional verified badge
 * - Optional follower/post count stats
 * - Clickable for navigation
 * - Theme-aware colors
 *
 * Component variants for testing:
 * - Size: Small/Medium/Large
 * - With/without stats
 * - With/without verified badge
 * - Clickable / non-clickable
 */

enum class ProfileSize(val avatarSize: androidx.compose.ui.unit.Dp) {
    Small(40.dp),
    Medium(56.dp),
    Large(80.dp)
}

data class UserProfile(
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val source: BookmarkSource,
    val verified: Boolean = false,
    val followerCount: Int? = null,
    val postCount: Int? = null
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
    val typography = LocalCrumbsTypography.current

    val baseModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else modifier

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Surface(
            modifier = Modifier.size(size.avatarSize),
            shape = CircleShape,
            color = colors.surfaceVariant
        ) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = "${profile.displayName} avatar",
                modifier = Modifier
                    .size(size.avatarSize)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(spacing.md))

        // Info
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            // Display name
            Text(
                text = profile.displayName,
                style = when (size) {
                    ProfileSize.Small -> typography.bodyLarge
                    ProfileSize.Medium, ProfileSize.Large -> typography.titleMedium
                },
                color = colors.textPrimary
            )

            // Username
            Text(
                text = "@${profile.username}",
                style = when (size) {
                    ProfileSize.Small -> typography.labelMedium
                    ProfileSize.Medium, ProfileSize.Large -> typography.bodyMedium
                },
                color = colors.textSecondary
            )

            // Stats
            if (showStats && (profile.followerCount != null || profile.postCount != null)) {
                Spacer(modifier = Modifier.height(spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    profile.followerCount?.let { count ->
                        Text(
                            text = "${formatCount(count)} followers",
                            style = typography.labelMedium,
                            color = colors.textSecondary
                        )
                    }
                    profile.postCount?.let { count ->
                        Text(
                            text = "${formatCount(count)} posts",
                            style = typography.labelMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

// Previews
@Preview(name = "Medium No Stats Light", showBackground = true)
@Composable
private fun PreviewUserProfileMediumLight() {
    CrumbsTheme(darkTheme = false) {
        UserProfileDisplay(
            profile = UserProfile(
                username = "johndoe",
                displayName = "John Doe",
                avatarUrl = "https://via.placeholder.com/150",
                source = BookmarkSource.Twitter
            ),
            size = ProfileSize.Medium
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
                source = BookmarkSource.Twitter
            ),
            size = ProfileSize.Medium
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
                postCount = 567
            ),
            size = ProfileSize.Medium,
            showStats = true
        )
    }
}

@Preview(name = "Small Light", showBackground = true)
@Composable
private fun PreviewUserProfileSmallLight() {
    CrumbsTheme(darkTheme = false) {
        UserProfileDisplay(
            profile = UserProfile(
                username = "janedoe",
                displayName = "Jane Doe",
                avatarUrl = "https://via.placeholder.com/150",
                source = BookmarkSource.Reddit
            ),
            size = ProfileSize.Small
        )
    }
}

@Preview(name = "Large With Stats Light", showBackground = true)
@Composable
private fun PreviewUserProfileLargeWithStatsLight() {
    CrumbsTheme(darkTheme = false) {
        UserProfileDisplay(
            profile = UserProfile(
                username = "popular_user",
                displayName = "Popular User",
                avatarUrl = "https://via.placeholder.com/150",
                source = BookmarkSource.Twitter,
                verified = true,
                followerCount = 1500000,
                postCount = 8900
            ),
            size = ProfileSize.Large,
            showStats = true
        )
    }
}
