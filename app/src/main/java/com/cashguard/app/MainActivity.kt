package com.cashguard.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashguard.app.data.CashGuardRepository
import com.cashguard.app.ui.i18n.LocalStrings
import com.cashguard.app.ui.i18n.stringsFor
import com.cashguard.app.ui.navigation.CashGuardNavGraph
import com.cashguard.app.ui.screens.OnboardingScreen
import com.cashguard.app.ui.theme.CashGuardTheme
import com.cashguard.app.viewmodel.MainViewModel
import com.cashguard.app.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val repository = CashGuardRepository(applicationContext)
        val viewModel = MainViewModel(repository)

        setContent {
            CashGuardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CashGuardApp(viewModel = viewModel, onRequestNotificationAccess = {
                        openNotificationAccessSettings(this)
                    })
                }
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            context.startActivity(intent)
        }
    }
}

@Composable
fun CashGuardApp(viewModel: MainViewModel, onRequestNotificationAccess: () -> Unit) {
    val settings by viewModel.settingsOrNull.collectAsStateWithLifecycle()
    val loaded = settings ?: return // blank surface until DataStore loads (a few ms)

    CompositionLocalProvider(LocalStrings provides stringsFor(loaded.language)) {
        if (!loaded.onboardingDone) {
            OnboardingScreen(
                selectedLanguage = loaded.language,
                onLanguageSelected = { viewModel.setLanguage(it) },
                onDone = { viewModel.completeOnboarding() }
            )
        } else {
            CashGuardNavGraph(
                viewModel = viewModel,
                onRequestNotificationAccess = onRequestNotificationAccess
            )
        }
    }
}
