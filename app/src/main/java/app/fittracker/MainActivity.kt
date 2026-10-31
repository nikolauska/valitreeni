package app.fittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.fittracker.ui.today.TodayScreen
import app.fittracker.ui.theme.FitTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ draws apps edge-to-edge anyway; opting in explicitly keeps insets handling in our hands.
        enableEdgeToEdge()
        setContent {
            FitTrackerTheme {
                TodayScreen(onStartRound = {}, onOpenTutorial = {})
            }
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_ROUND = "round"
    }
}
