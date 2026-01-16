package com.github.jayteealao.crumbs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CrumbsTheme {
                CrumbsNavHost(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
