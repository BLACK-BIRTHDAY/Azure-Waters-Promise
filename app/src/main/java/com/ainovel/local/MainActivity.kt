package com.ainovel.local

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.activity.viewModels
import com.ainovel.local.ui.AppViewModel
import com.ainovel.local.ui.LocalNovelApp
import com.ainovel.local.ui.theme.LocalNovelTheme

class MainActivity : ComponentActivity() {
    private val appViewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalNovelTheme {
                Surface {
                    LocalNovelApp(appViewModel)
                }
            }
        }
    }

    override fun onPause() {
        appViewModel.flushPendingAutosave()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) appViewModel.stopGeneration()
        super.onDestroy()
    }
}
