package app.linkphone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.linkphone.ui.HomeScreen
import app.linkphone.ui.LinkPhoneTheme
import app.linkphone.ui.LinkViewModel
import app.linkphone.ui.OnboardingScreen
import app.linkphone.ui.PairScreen
import app.linkphone.ui.QrScannerActivity
import app.linkphone.ui.Screen

class MainActivity : ComponentActivity() {
    private val viewModel: LinkViewModel by viewModels()

    private val scanner = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val raw = result.data?.getStringExtra(QrScannerActivity.EXTRA_RAW)
            if (!raw.isNullOrBlank()) {
                viewModel.onScannedPair(raw)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LinkPhoneTheme {
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                BackHandler(enabled = screen != Screen.Home) { viewModel.goHome() }
                when (screen) {
                    Screen.Onboarding -> OnboardingScreen(
                        vm = viewModel,
                        onPair = {
                            viewModel.completeOnboarding()
                            viewModel.openPair()
                        },
                        onFinish = viewModel::completeOnboarding,
                    )
                    Screen.Pair -> PairScreen(
                        vm = viewModel,
                        onScan = {
                            scanner.launch(Intent(this, QrScannerActivity::class.java))
                        },
                        onBack = viewModel::goHome,
                    )
                    Screen.Home -> HomeScreen(
                        vm = viewModel,
                        onPair = viewModel::openPair,
                        onSetup = viewModel::openOnboarding,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        if (viewModel.shouldAutoStart()) {
            viewModel.start()
        }
    }
}
