package app.fittracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import app.fittracker.R

/** Plain arrow instead of an icon library: the app needs only a handful of symbols. */
@Composable
fun BackButton(onBack: () -> Unit) {
    val label = stringResource(R.string.back)
    TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = label }) { Text("←", style = MaterialTheme.typography.headlineSmall) }
}
