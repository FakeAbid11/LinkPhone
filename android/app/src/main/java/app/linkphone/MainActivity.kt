package app.linkphone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import app.linkphone.ui.HomeScreen
import app.linkphone.ui.LinkPhoneTheme
import app.linkphone.ui.LinkViewModel
import app.linkphone.ui.PairScreen
import app.linkphone.ui.QrScannerActivity

class MainActivity : ComponentActivity() {
    private val viewModel: LinkViewModel by viewModels()
    private val screen = mutableStateOf("home")

    private val scanner = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val raw = result.data?.getStringExtra(QrScannerActivity.EXTRA_RAW)
            if (!raw.isNullOrBlank()) {
                screen.value = "pair"
                viewModel.pair(raw)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkPhoneTheme {
                val current by screen
                when (current) {
                    "pair" -> PairScreen(
                        vm = viewModel,
                        onScan = {
                            scanner.launch(Intent(this, QrScannerActivity::class.java))
                        },
                        onBack = {
                            screen.value = "home"
                            viewModel.refresh()
                        },
                    )
                    else -> HomeScreen(
                        vm = viewModel,
                        onPair = { screen.value = "pair" },
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
