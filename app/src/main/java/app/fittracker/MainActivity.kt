package app.fittracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import app.fittracker.ui.AppNav
import app.fittracker.ui.theme.FitTrackerTheme

class MainActivity : ComponentActivity() {
    private val openRoundRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ draws apps edge-to-edge anyway; opting in explicitly keeps insets handling in our hands.
        enableEdgeToEdge()
        // Only a fresh launch counts; after a rotation the round screen is already restored.
        if (savedInstanceState == null) handleIntent(intent)
        (application as FitApp).updater.checkOnOpen()
        setContent {
            FitTrackerTheme {
                AppNav(openRoundRequest.intValue, onRoundRequestHandled = { openRoundRequest.intValue = 0 })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_ROUND) openRoundRequest.intValue++
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_ROUND = "round"
    }
}
