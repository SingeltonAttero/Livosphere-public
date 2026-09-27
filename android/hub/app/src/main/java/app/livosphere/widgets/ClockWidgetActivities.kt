package app.livosphere.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.livosphere.contract.*
import app.livosphere.hub.LivosphereTheme
import app.livosphere.R
import app.livosphere.hub.theme.NativeWidgetPreview
import app.livosphere.widgets.runtime.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

/** Debug test seams wrap complete platform boundaries; production defaults remain strict. */
object ClockActivityHooks {
    var ownProvider: (Context, Int) -> ComponentName? = ClockWidgetRuntime::ownProvider
    var loadPreferences: suspend (Context, Int) -> WidgetPreferences? = ::loadCurrentPreferences
    var configure: suspend (Context, Int, String, WidgetSize, ClockTarget?) -> ConfigurationCommitResult =
        ClockWidgetRuntime::configureAndUpdate
    var pin: suspend (Context, String, WidgetSize, ClockTarget?) -> PinRequestResult =
        { context, widgetId, size, target -> WidgetPinLauncher.request(context, widgetId, size, target) }
    var resolves: (Context, ClockTarget) -> Boolean = { context, target -> ClockTargetResolver(context).resolves(target) }
    var launch: (Activity, Intent) -> Unit = { activity, intent -> activity.startActivity(intent) }
    var recover: (Activity, Int) -> Unit =
        { activity, id -> activity.startActivity(ClockWidgetRuntime.configurationIntent(activity, id)) }

    fun reset() {
        ownProvider = ClockWidgetRuntime::ownProvider
        loadPreferences = ::loadCurrentPreferences
        configure = ClockWidgetRuntime::configureAndUpdate
        pin = { context, widgetId, size, target -> WidgetPinLauncher.request(context, widgetId, size, target) }
        resolves = { context, target -> ClockTargetResolver(context).resolves(target) }
        launch = { activity, intent -> activity.startActivity(intent) }
        recover = { activity, id -> activity.startActivity(ClockWidgetRuntime.configurationIntent(activity, id)) }
    }
}

private suspend fun loadCurrentPreferences(context: Context, id: Int): WidgetPreferences? =
    (ClockWidgetRuntime.repository(context).widgets(ClockWidgetRuntime.catalog()::contains)
        .observe(id).first() as? SettingsOutcome.Success)?.value

class ClockWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val provider = ClockActivityHooks.ownProvider(this, appWidgetId)
        if (provider == null) { finish(); return }
        setContent { LivosphereTheme {
            ClockConfiguration(
                prePin = false, requestedWidgetId = null, appWidgetId = appWidgetId,
                fixedSize = provider.className.let(ClockWidgetRuntime::sizeForProvider),
                onCancel = { finish() }, onOpenHome = ::openHome,
                onSave = { widgetId, size, target ->
                    val commit = ClockActivityHooks.configure(this, appWidgetId, widgetId, size, target)
                    val result = when (WidgetConfigurationFlow.failureState(commit)) {
                        null -> SaveResult.SUCCESS
                        ConfigurationFailureState.PREVIOUS_PRESERVED -> SaveResult.UPDATE_FAILED_ROLLED_BACK
                        ConfigurationFailureState.STATE_UNKNOWN -> SaveResult.UPDATE_FAILED_STATE_UNKNOWN
                        ConfigurationFailureState.WRITE_REJECTED -> SaveResult.SAVE_REJECTED
                    }
                    if (result == SaveResult.SUCCESS) {
                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                        finish()
                    }
                    result
                },
            )
        } }
    }

    private fun openHome() = startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
}

/** Hub-only pin entry. The system configuration contract cannot select this non-exported component. */
class ClockWidgetPrePinActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requested = intent.getStringExtra(ClockWidgetRuntime.EXTRA_WIDGET_ID)
            ?.takeIf(ClockWidgetRuntime.catalog()::contains)
        if (requested == null) { finish(); return }
        setContent { LivosphereTheme {
            ClockConfiguration(
                prePin = true, requestedWidgetId = requested, appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
                fixedSize = null, onCancel = { finish() },
                onOpenHome = { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) },
                onSave = { widgetId, size, target ->
                    when (ClockActivityHooks.pin(this, widgetId, size, target)) {
                        PinRequestResult.REQUESTED -> SaveResult.SUCCESS.also { finish() }
                        PinRequestResult.UNSUPPORTED -> SaveResult.PIN_UNAVAILABLE
                        PinRequestResult.FAILED -> SaveResult.PIN_REJECTED
                    }
                },
            )
        } }
    }
}

private enum class SaveResult {
    SUCCESS,
    UPDATE_FAILED_ROLLED_BACK,
    UPDATE_FAILED_STATE_UNKNOWN,
    SAVE_REJECTED,
    PIN_UNAVAILABLE,
    PIN_REJECTED,
    LOAD_FAILED,
}

@Composable
private fun ClockConfiguration(
    prePin: Boolean,
    requestedWidgetId: String?,
    appWidgetId: Int,
    fixedSize: WidgetSize?,
    onCancel: () -> Unit,
    onOpenHome: () -> Unit,
    onSave: suspend (String, WidgetSize, ClockTarget?) -> SaveResult,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val catalogItems = remember { ClockWidgetRuntime.catalog().items() }
    val targets = remember { ClockTargetResolver(context).targets() }
    var widgetId by rememberSaveable { mutableStateOf(requestedWidgetId ?: catalogItems.first().widgetId) }
    var sizeName by rememberSaveable { mutableStateOf((fixedSize ?: WidgetSize.M).name) }
    var targetKey by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(prePin) }
    // A recreated Activity may retry safely; persisting an in-flight flag would strand the UI.
    var saving by remember { mutableStateOf(false) }
    var resultName by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(appWidgetId, loaded) {
        if (!prePin && !loaded) {
            try {
                ClockActivityHooks.loadPreferences(context, appWidgetId)?.let { current ->
                    widgetId = current.widgetId
                    sizeName = (fixedSize ?: current.size).name
                    targetKey = current.clockTarget?.stableKey.orEmpty()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                resultName = SaveResult.LOAD_FAILED.name
            } finally {
                loaded = true
            }
        }
    }
    val result = resultName?.let(SaveResult::valueOf)
    val selectedItem = catalogItems.singleOrNull { it.widgetId == widgetId }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (prePin) {
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.hub_back))
                }
                Text(selectedItem?.displayName.orEmpty(), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading(); testTag = "widget-detail-title" })
                NativeWidgetPreview(widgetId, WidgetSize.valueOf(sizeName), Modifier.fillMaxWidth()
                    .semantics { testTag = "widget-detail-preview-$sizeName" })
            } else {
                Text("Настройка часов", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                Text("Настройки сохраняются только для этого экземпляра.")
                Text("Вариант часов", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    catalogItems.forEach { item ->
                        TargetRow(item.displayName, widgetId == item.widgetId) { widgetId = item.widgetId }
                    }
                }
            }
            if (fixedSize == null) {
                Text("Размер", style = MaterialTheme.typography.titleMedium)
                if (prePin) {
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WidgetSize.entries.forEach { option ->
                            val selected = sizeName == option.name
                            Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .selectable(selected, role = Role.RadioButton, onClick = { sizeName = option.name })
                                .semantics { testTag = "widget-size-${option.name}" }, contentAlignment = Alignment.Center) {
                                Text(option.name, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Text(WidgetSize.valueOf(sizeName).userLabel, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(Modifier.selectableGroup()) {
                        WidgetSize.entries.forEach { option ->
                            TargetRow(option.userLabel, sizeName == option.name) { sizeName = option.name }
                        }
                    }
                }
            }
            Text("По нажатию", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                TargetRow("Выбрать позже", targetKey.isEmpty()) { targetKey = "" }
                targets.forEach { option ->
                    TargetRow(option.loadLabel(context), targetKey == option.stableKey) { targetKey = option.stableKey }
                }
            }
            if (targets.isEmpty()) Text("Совместимое приложение часов не найдено. Его можно выбрать позже.")
            when (result) {
                SaveResult.UPDATE_FAILED_ROLLED_BACK -> Text(
                    "Не удалось обновить часы. Прежние настройки сохранены; можно повторить или отменить.",
                    color = MaterialTheme.colorScheme.error,
                )
                SaveResult.UPDATE_FAILED_STATE_UNKNOWN -> Text(
                    "Не удалось обновить часы и восстановить прежние настройки. Откройте настройку экземпляра снова и проверьте выбранные значения.",
                    color = MaterialTheme.colorScheme.error,
                )
                SaveResult.SAVE_REJECTED -> Text(
                    "Не удалось сохранить настройки. Текущая конфигурация не изменена; можно повторить или отменить.",
                    color = MaterialTheme.colorScheme.error,
                )
                SaveResult.LOAD_FAILED -> Text(
                    "Не удалось загрузить прежние настройки. Проверьте выбранные значения перед сохранением или отмените настройку.",
                    color = MaterialTheme.colorScheme.error,
                )
                SaveResult.PIN_UNAVAILABLE, SaveResult.PIN_REJECTED -> {
                    Text("Автоматическое добавление недоступно. На главном экране удерживайте свободное место, откройте «Виджеты» и найдите «Живые обои Livosphere».")
                    OutlinedButton(onClick = onOpenHome, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Перейти на главный экран")
                    }
                }
                else -> Unit
            }
            Button(onClick = {
                saving = true
                resultName = null
                scope.launch {
                    try {
                        resultName = onSave(
                            widgetId, WidgetSize.valueOf(sizeName),
                            targets.singleOrNull { it.stableKey == targetKey },
                        ).name
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        resultName = (if (prePin) SaveResult.PIN_REJECTED else SaveResult.UPDATE_FAILED_STATE_UNKNOWN).name
                    } finally {
                        saving = false
                    }
                }
            }, enabled = loaded && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (result != null && result != SaveResult.SUCCESS) "Повторить"
                    else if (prePin) stringResource(R.string.widget_install) else "Сохранить")
            }
            if (!prePin) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Отмена")
            }
        }
    }
}

private val WidgetSize.userLabel get() = when (this) {
    WidgetSize.S -> "Маленькие — время"
    WidgetSize.M -> "Средние — время и дата"
    WidgetSize.L -> "Крупные — время и дата"
}
private val ClockTarget.stableKey get() = "$packageName|$className|$action"
private fun ClockTarget.loadLabel(context: Context): String = runCatching {
    context.packageManager.getActivityInfo(ComponentName(packageName, className), 0)
        .loadLabel(context.packageManager).toString()
}.getOrElse { packageName }

@Composable
private fun TargetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

class ClockLaunchRouterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId <= 0) { finish(); return }
        lifecycleScope.launch {
            val target = ClockActivityHooks.loadPreferences(this@ClockLaunchRouterActivity, appWidgetId)?.clockTarget
            val launched = target != null && ClockActivityHooks.resolves(this@ClockLaunchRouterActivity, target) &&
                runCatching {
                    ClockActivityHooks.launch(
                        this@ClockLaunchRouterActivity,
                        Intent(target.action).setComponent(ComponentName(target.packageName, target.className)),
                    )
                }.isSuccess
            if (!launched) runCatching {
                ClockActivityHooks.recover(this@ClockLaunchRouterActivity, appWidgetId)
            }
            finish()
        }
    }
}
