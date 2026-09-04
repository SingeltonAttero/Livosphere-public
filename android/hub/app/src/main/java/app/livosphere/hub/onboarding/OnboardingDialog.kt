package app.livosphere.hub.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.livosphere.R

@Composable
internal fun OnboardingDialog(onDismiss: () -> Unit, onGo: () -> Unit) {
    // Native Dialog provides Back/outside dismissal and restores the underlying window's focus.
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp).testTag("onboarding"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.hub_onboarding_title), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.hub_onboarding_description), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onGo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("onboarding-go")) {
                    Text(stringResource(R.string.hub_onboarding_go))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("onboarding-skip")) {
                    Text(stringResource(R.string.hub_onboarding_skip))
                }
            }
        }
    }
}
