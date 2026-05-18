package com.github.jayteealao.crumbs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dispatchDebugIntent(intent)
        setContent {
            CrumbsTheme {
                CrumbsNavHost(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchDebugIntent(intent)
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
        } catch (_: Throwable) {
            // Any reflective failure is debug-only noise; swallow.
        }
    }
}
