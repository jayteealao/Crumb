package com.github.jayteealao.crumbs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.BackdropScaffold
import androidx.compose.material.BackdropValue
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.rememberBackdropScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.github.jayteealao.crumbs.screens.SplashScreen
import com.github.jayteealao.crumbs.screens.login.LoginScreen
import com.github.jayteealao.crumbs.screens.login.LoginViewModel
import com.github.jayteealao.crumbs.screens.twitter.BookmarksListScreen
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterialApi::class, ExperimentalTextApi::class)
@Composable
fun CrumbsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    val snackbarHostState = SnackbarHostState()
    val scaffoldState = rememberBackdropScaffoldState(snackbarHostState = snackbarHostState, initialValue = BackdropValue.Concealed)
    BackdropScaffold(
        scaffoldState = scaffoldState,
        frontLayerShape = RectangleShape,
        backLayerContent = { Box(modifier = Modifier.fillMaxSize()) },
        frontLayerContent = {
            NavHost(
                navController = navController,
                modifier = modifier,
                startDestination = startDestination
            ) {
                }
            }
        }
    )
}
