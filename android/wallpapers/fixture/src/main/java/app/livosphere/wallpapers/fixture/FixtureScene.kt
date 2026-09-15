package app.livosphere.wallpapers.fixture

import android.content.res.Resources
import android.util.Xml
import app.livosphere.contract.WallpaperMotionMode
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import app.livosphere.wallpapers.engine.EffectiveMotion
import app.livosphere.wallpapers.engine.EffectiveMotionPolicy
import app.livosphere.wallpapers.engine.SceneDefinition
import app.livosphere.wallpapers.engine.SceneEffectDefinition
import app.livosphere.wallpapers.engine.SceneFrame
import app.livosphere.wallpapers.engine.SceneMotionRuntime
import app.livosphere.wallpapers.engine.SceneObjectDefinition
import app.livosphere.wallpapers.engine.ScenePowerFacts
import app.livosphere.wallpapers.engine.SceneTrigger
import app.livosphere.wallpapers.engine.TriggerDispatch
import app.livosphere.wallpapers.engine.authoredEffectLevels
import app.livosphere.wallpapers.engine.effectStopRule
import app.livosphere.wallpapers.engine.sceneEffectType
import app.livosphere.wallpapers.engine.sceneTrigger
import org.xmlpull.v1.XmlPullParser

internal fun loadFixtureScene(resources: Resources): SceneDefinition {
    val objects = mutableListOf<SceneObjectDefinition>()
    parse(resources, R.raw.ls_isolation_fixture_wallpaper_scene, "scene") { parser ->
        if (parser.name == "object") {
            objects += SceneObjectDefinition(
                id = parser.required("id"),
                x = parser.required("x").toFloat(),
                y = parser.required("y").toFloat(),
                size = parser.required("size").toFloat(),
            )
        }
    }
    val effects = mutableListOf<SceneEffectDefinition>()
    var declaredTriggers: Set<SceneTrigger>? = null
    parse(resources, R.raw.ls_isolation_fixture_wallpaper_effects, "effects") { parser ->
        if (parser.name == "effects") {
            declaredTriggers = parser.required("declaredTriggers").split(',').mapTo(linkedSetOf()) { sceneTrigger(it.trim()) }
        } else if (parser.name == "effect") {
            effects += SceneEffectDefinition(
                id = parser.required("id"),
                objectId = parser.required("objectRef"),
                trigger = sceneTrigger(parser.required("trigger")),
                priority = parser.required("priority").toInt(),
                durationMillis = parser.required("durationMillis").toLong(),
                amplitudeX = parser.required("amplitudeX").toFloat(),
                amplitudeY = parser.required("amplitudeY").toFloat(),
                levels = authoredEffectLevels(parser.required("levels")),
                type = sceneEffectType(parser.required("type")),
                stopRule = effectStopRule(parser.required("stop")),
                reducedSafe = parser.required("reduced").toBooleanStrict(),
            )
        }
    }
    return SceneDefinition(objects, effects, requireNotNull(declaredTriggers) { "Missing declaredTriggers" })
}
private inline fun parse(resources: Resources, resourceId: Int, root: String, visit: (XmlPullParser) -> Unit) {
    resources.openRawResource(resourceId).use { input ->
        val parser = Xml.newPullParser().apply { setInput(input, Charsets.UTF_8.name()) }
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) visit(parser)
            parser.next()
        }
        require(parser.depth == 0) { "Malformed $root fixture declaration" }
    }
}

private fun XmlPullParser.required(name: String) = requireNotNull(getAttributeValue(null, name)) { "Missing $name on <$this>" }

internal class FixtureSceneController(val definition: SceneDefinition) {
    private val policy = EffectiveMotionPolicy()
    private val runtime = SceneMotionRuntime(definition)
    var requestedLevel: AuthoredEffectLevel = AuthoredEffectLevel.FULL
        private set
    var motionMode: WallpaperMotionMode = WallpaperMotionMode.REDUCED
        private set
    var systemReduced: Boolean = true
        private set
    var powerFacts: ScenePowerFacts = ScenePowerFacts(null, null, null)
        private set
    var interactionsEnabled: Boolean = true
        private set
    var lastFrame: SceneFrame? = null
        private set
    var lastDispatch: TriggerDispatch? = null
        private set

    fun setRequestedLevel(value: AuthoredEffectLevel) { requestedLevel = value }
    fun setMotionMode(value: WallpaperMotionMode?) { motionMode = value ?: WallpaperMotionMode.REDUCED }
    fun setSystemReduced(value: Boolean) { systemReduced = value }
    fun setPowerFacts(value: ScenePowerFacts) { powerFacts = value }
    fun setInteractionsEnabled(value: Boolean?) { interactionsEnabled = value ?: false }
    fun motion(): EffectiveMotion = policy.evaluate(definition, requestedLevel, motionMode, systemReduced, powerFacts)

    fun frame(nowMillis: Long): SceneFrame = runtime.frame(nowMillis, motion()).also { lastFrame = it }

    fun trigger(trigger: SceneTrigger, nowMillis: Long, signalAvailable: Boolean, magnitude: Float = 1f): TriggerDispatch =
        runtime.trigger(trigger, nowMillis, motion(), interactionsEnabled, signalAvailable, magnitude)
            .also { lastDispatch = it }

    fun stop() { runtime.stop() }
    fun resume() { runtime.resume() }
    fun pendingTriggerCount(): Int = runtime.pendingTriggerCount()
}
