package app.livosphere.hub.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.livosphere.R

/** Doze exemption is a system fact, not a claim about every manufacturer's background policy. */
@Composable
internal fun BatterySettingsHelp() {
    val context = LocalContext.current
    fun exemption(): Boolean? = runCatching {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrNull()
    var exempt by remember { mutableStateOf(exemption()) }
    LifecycleResumeEffect(context) {
        exempt = exemption()
        onPauseOrDispose { }
    }
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, R.string.settings_battery_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.settings_battery_unavailable, Toast.LENGTH_LONG).show()
        }
    }
    Text(stringResource(R.string.settings_battery_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(when (exempt) {
        true -> R.string.settings_battery_exempt
        false -> R.string.settings_battery_optimized
        null -> R.string.settings_battery_unknown
    }), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.settings_battery_help), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.settings_battery_open))
    }
    TextButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null))) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.settings_battery_app))
    }
}
