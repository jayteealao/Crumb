package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Crumbs top app bar with scroll-aware collapse and inline expanding search
 *
 * Features:
 * - **Normal state**: Logo + app name + search icon
 * - **Collapsed state**: Compact logo + search icon (when scrolled down)
 * - **Search active**: Back arrow + search field + clear button
 * - Bottom-start cut corner (8dp, anchored aesthetic)
 * - Search suggestions shown below when active
 *
 * Component variants for testing:
 * - Normal expanded (not scrolled)
 * - Collapsed (scrolled down)
 * - Search active with empty query
 * - Search active with text
 * - Light/dark theme
 *
 * @param scrollBehavior Material 3 scroll behavior for collapse/expand
 * @param searchQuery Current search query text
 * @param onSearchQueryChange Callback when search text changes
 * @param isSearchActive Whether search is currently active/expanded
 * @param onSearchActiveChange Callback when search state changes
 * @param logoResId Optional logo drawable resource (null uses placeholder)
 * @param modifier Modifier to be applied to the top bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrumbsTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    isSearchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
    logoResId: Int? = null
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    // Determine if collapsed based on scroll behavior
    val isCollapsed = scrollBehavior?.state?.collapsedFraction ?: 0f > 0.5f

    // Animate corner cut size based on collapsed state
    val cornerCut by animateDpAsState(
        targetValue = if (isCollapsed) 6.dp else 8.dp,
        animationSpec = spring(),
        label = "cornerCut"
    )

    // Animate logo size based on collapsed state
    val logoSize by animateDpAsState(
        targetValue = if (isCollapsed) 24.dp else 28.dp,
        animationSpec = spring(),
        label = "logoSize"
    )

    Surface(
        modifier = modifier,
        shape = CutCornerShape(bottomStart = cornerCut),
        color = colors.surface
    ) {
        TopAppBar(
            title = {
                AnimatedVisibility(
                    visible = !isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    // Normal state: Logo + App Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = spacing.sm)
                    ) {
                        // Logo icon (placeholder or provided)
                        if (logoResId != null) {
                            Icon(
                                painter = painterResource(id = logoResId),
                                contentDescription = "Crumbs logo",
                                modifier = Modifier
                                    .size(logoSize)
                                    .padding(end = spacing.sm),
                                tint = colors.accent
                            )
                        } else {
                            // Placeholder: Search icon styled as logo
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Crumbs logo",
                                modifier = Modifier
                                    .size(logoSize)
                                    .padding(end = spacing.sm),
                                tint = colors.accent
                            )
                        }

                        // App name (hide when collapsed)
                        AnimatedVisibility(
                            visible = !isCollapsed,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = "crumbs",
                                style = typography.titleLarge,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    // Search state: Text field
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Search crumbs...",
                                style = typography.bodyLarge,
                                color = colors.textSecondary
                            )
                        },
                        textStyle = typography.bodyLarge.copy(color = colors.textPrimary),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = colors.accent
                        ),
                        singleLine = true
                    )
                }
            },
            navigationIcon = {
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(onClick = {
                        onSearchActiveChange(false)
                        onSearchQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close search",
                            tint = colors.textPrimary
                        )
                    }
                }
            },
            actions = {
                if (isSearchActive && searchQuery.isNotEmpty()) {
                    // Clear button when search has text
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = colors.textSecondary
                        )
                    }
                } else if (!isSearchActive) {
                    // Search icon in normal state
                    IconButton(onClick = { onSearchActiveChange(true) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.textPrimary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                titleContentColor = colors.textPrimary,
                actionIconContentColor = colors.textPrimary
            ),
            scrollBehavior = scrollBehavior
        )
    }
}

// Previews
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Normal Expanded Light", showBackground = true)
@Composable
private fun PreviewTopBarNormalLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTopBar(
            isSearchActive = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Normal Expanded Dark", showBackground = true)
@Composable
private fun PreviewTopBarNormalDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsTopBar(
            isSearchActive = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Search Active Empty", showBackground = true)
@Composable
private fun PreviewTopBarSearchEmpty() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTopBar(
            isSearchActive = true,
            searchQuery = ""
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Search Active With Query", showBackground = true)
@Composable
private fun PreviewTopBarSearchWithQuery() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTopBar(
            isSearchActive = true,
            searchQuery = "design patterns"
        )
    }
}
