package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class ProfileComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun profile_medium_noStats_light() {
        composeTestRule.setContent {
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

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/UserProfileDisplay_medium_noStats_light.png")
    }

    @Test
    fun profile_medium_noStats_dark() {
        composeTestRule.setContent {
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

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/UserProfileDisplay_medium_noStats_dark.png")
    }

    @Test
    fun profile_medium_withStats_light() {
        composeTestRule.setContent {
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

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/UserProfileDisplay_medium_withStats_light.png")
    }

    @Test
    fun profile_medium_withStats_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
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

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/UserProfileDisplay_medium_withStats_dark.png")
    }

    @Test
    fun profile_small_light() {
        composeTestRule.setContent {
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

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/UserProfileDisplay_small_light.png")
    }

    @Test
    fun profile_small_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
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

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/UserProfileDisplay_small_dark.png")
    }

    @Test
    fun profile_large_withStats_light() {
        composeTestRule.setContent {
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

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/UserProfileDisplay_large_withStats_light.png")
    }

    @Test
    fun profile_large_withStats_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
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

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/UserProfileDisplay_large_withStats_dark.png")
    }
}
