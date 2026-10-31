@file:OptIn(ExperimentalMaterial3Api::class)

package app.fittracker.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.data.Exercise
import app.fittracker.ui.BackButton
import app.fittracker.ui.exerciseName
import app.fittracker.ui.figure.MoveFigure
import app.fittracker.ui.figure.Poses
import app.fittracker.ui.setAmount

@Composable
fun TutorialScreen(exerciseId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as FitApp
    var exercise by remember { mutableStateOf<Exercise?>(null) }
    LaunchedEffect(exerciseId) { exercise = app.repository.exercise(exerciseId) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(exercise?.let { exerciseName(it) }.orEmpty()) }, navigationIcon = { BackButton(onBack) })
        },
    ) { padding ->
        val e = exercise ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                MoveFigure(
                    Poses.forKey(e.builtIn?.key),
                    contentDescription = exerciseName(e),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(stringResource(R.string.tutorial_one_set, setAmount(e)), style = MaterialTheme.typography.titleMedium)
            val tutorial = e.builtIn?.tutorial
            if (tutorial == null) {
                Text(stringResource(R.string.tutorial_custom_move), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Section(stringResource(R.string.tutorial_how_to)) {
                stringArrayResource(tutorial.steps).forEachIndexed { i, step ->
                    Row { Text("${i + 1}.", Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold); Text(step) }
                }
            }
            Section(stringResource(R.string.tutorial_avoid)) {
                stringArrayResource(tutorial.avoid).forEach { Row { Text("•", Modifier.padding(end = 8.dp)); Text(it) } }
            }
            Variation(stringResource(R.string.tutorial_easier), stringResource(tutorial.easier))
            Variation(stringResource(R.string.tutorial_harder), stringResource(tutorial.harder))
            Text(
                stringResource(R.string.tutorial_safety),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Variation(label: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
