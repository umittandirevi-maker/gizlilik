package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.audio.AlertOverrideManager
import com.example.ui.MainViewModel
import com.example.ui.SmartFilterScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SmartFilterScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkNotificationPermission(this)
        viewModel.checkInitialStatus(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Defensive cleanup: Ensure any active hardware alert audio objects are force-released
        // when the activity finishes or process is torn down.
        AlertOverrideManager.silenceAlert(this)
    }
}
