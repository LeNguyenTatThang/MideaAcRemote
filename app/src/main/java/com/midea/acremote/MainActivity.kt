package com.midea.acremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import com.midea.acremote.ui.AppRoot
import com.midea.acremote.ui.RemoteViewModel
import com.midea.acremote.ui.theme.MideaAcRemoteTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as MideaApp).container

    private val viewModel: RemoteViewModel by viewModels { RemoteViewModel.factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MideaAcRemoteTheme {
                val settings by viewModel.settings.collectAsState()
                KeepScreenOn(enabled = settings.keepScreenOn)
                AppRoot(viewModel)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = (view.context as? android.app.Activity)?.window
        if (enabled) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
