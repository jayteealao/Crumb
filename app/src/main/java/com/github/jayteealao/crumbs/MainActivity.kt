package com.github.jayteealao.crumbs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.twitter.oauth.TwitterOAuthCoordinator
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var twitterOAuthCoordinator: TwitterOAuthCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dispatchDebugIntent(intent)
        dispatchOAuthDeepLink(intent)
        setContent {
            CrumbsTheme {
                CrumbsNavHost(
                    modifier = Modifier.fillMaxSize(),
                    twitterOAuthCoordinator = twitterOAuthCoordinator,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchDebugIntent(intent)
        dispatchOAuthDeepLink(intent)
    }

    /**
     * Forward the X OAuth deep-link URI to the coordinator. The path
     * `crumbs://graphitenerd.xyz/x-oauth-*` is owned end-to-end here, not by
     * navDeepLink, so the coordinator can run before any nav transition.
     */
    private fun dispatchOAuthDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val path = data.path ?: return
        if (path == TwitterOAuthCoordinator.PATH_COMPLETE || path == TwitterOAuthCoordinator.PATH_ERROR) {
            twitterOAuthCoordinator.handleDeepLink(data)
        }
    }

    /**
     * Reflectively dispatches to com.github.jayteealao.crumbs.debug.DebugIntentHandler
     * when present (debug builds only). Release builds throw ClassNotFoundException
     * here, which is silently caught, keeping release bytecode stable.
     */
    private fun dispatchDebugIntent(intent: Intent?) {
        if (intent == null) return
        try {
            val cls = Class.forName("com.github.jayteealao.crumbs.debug.DebugIntentHandler")
            val method = cls.getMethod(
                "handleIntent",
                ComponentActivity::class.java,
                Intent::class.java,
            )
            method.invoke(null, this, intent)
        } catch (_: ClassNotFoundException) {
            // Release variant — DebugIntentHandler is excluded by AGP source-set rules.
        } catch (e: ReflectiveOperationException) {
            // Narrowed from Throwable: only swallow expected reflection failures
            // (NoSuchMethodException, IllegalAccessException, InvocationTargetException).
            // OOM, ThreadDeath, and other JVM-level errors must propagate.
            Timber.w(e, "Debug intent dispatch failed")
        }
    }
}
