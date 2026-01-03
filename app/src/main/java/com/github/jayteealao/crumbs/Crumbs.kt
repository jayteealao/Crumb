package com.github.jayteealao.crumbs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.BackdropScaffold
import androidx.compose.material.BackdropValue
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.rememberBackdropScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.github.jayteealao.twitter.data.IdForThread
import com.github.jayteealao.crumbs.screens.HomeScreen
import com.github.jayteealao.crumbs.screens.SplashScreen
import com.github.jayteealao.crumbs.screens.login.LoginScreen
import com.github.jayteealao.twitter.screens.LoginViewModel
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterialApi::class, ExperimentalTextApi::class, ExperimentalPagerApi::class)
@Composable
fun CrumbsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screens.SPLASHSCREEN.name,
    loginViewModel: LoginViewModel = hiltViewModel(),
    bookmarksViewModel: BookmarksViewModel = hiltViewModel()
) {
    val isAccessTokenAvailable by loginViewModel.isAccessTokenAvailable.collectAsState()
    val snackbarHostState = SnackbarHostState()
    val scaffoldState = rememberBackdropScaffoldState(snackbarHostState = snackbarHostState, initialValue = BackdropValue.Concealed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine if app bar should be shown based on current route
    val showAppBar = currentRoute?.startsWith(Screens.HOMESCREEN.name) == true

    BackdropScaffold(
        scaffoldState = scaffoldState,
        frontLayerShape = RectangleShape,
        appBar = {
            if (showAppBar) {
                TopAppBar(
                modifier = Modifier
                    .fillMaxWidth(),
                elevation = 0.dp
            ) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                    IconButton(onClick = { scope.launch { if (scaffoldState.isConcealed) scaffoldState.reveal() else scaffoldState.conceal() } }) {
                        Icon(imageVector = Icons.Default.Navigation, contentDescription = "")
                    }
                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = "Crumbs",
                        style = TextStyle(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color(0x80F12711),
                                    Color(0x80F5AF19)
                                )
                            )
                        )
                    )
                }
            }
            }
        },
        backLayerContent = { Box(modifier = Modifier.fillMaxSize()) },
        frontLayerContent = {
            NavHost(
                navController = navController,
                modifier = modifier,
                startDestination = startDestination
            ) {
                composable(
                    Screens.LOGINSCREEN.name,
                    deepLinks = listOf(navDeepLink { uriPattern = "crumbs://graphitenerd.xyz?code={code}" })
                ) {
                    LoginScreen(
                        viewModel = loginViewModel,
                        navController = navController,
                        authorizationCode = navController
                            .currentBackStackEntry?.arguments?.getString("code")
                    )
                }
                
                
                composable(
                    "${Screens.HOMESCREEN.name}/{refreshed}",
                    arguments = listOf(navArgument(name = "refreshed") { type = NavType.BoolType })
                ) {
                    HomeScreen(
                        scope = scope,
                        navController = navController,
                        loginViewModel = loginViewModel,
                        bookmarksViewModel = bookmarksViewModel,
                        twitterAuthCode = navController
                            .currentBackStackEntry?.arguments?.getString("code") ?: ""
                    )
                }

                composable(
                    Screens.SPLASHSCREEN.name
                ) {
                    SplashScreen(
                        isLoggedIn = isAccessTokenAvailable,
                        navController = navController,
                        loginViewModel = loginViewModel
                    )
                }
            }
        }
    )
}

enum class Screens {
    SPLASHSCREEN,
    LOGINSCREEN,
    HOMESCREEN {
        override fun screenRoute(refreshed: Boolean) = "${this.name}/$refreshed"
    };
    open fun screenRoute(refreshed: Boolean) = this.name
}
