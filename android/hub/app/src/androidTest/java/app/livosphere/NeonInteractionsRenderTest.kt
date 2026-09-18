package app.livosphere

import android.graphics.*
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.DayPhase
import app.livosphere.contract.WallpaperMotionMode
import app.livosphere.wallpapers.engine.*
import app.livosphere.wallpapers.neon.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class NeonInteractionsRenderTest {
    @Test fun allPhaseMatchedBlinksRenderWithNativeMasks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var holder: SurfaceHolder
        instrumentation.runOnMainSync { holder = SurfaceView(context).holder }
        val motion = EffectiveMotionPolicy().evaluate(NeonScenePolicy.definition, AuthoredEffectLevel.FULL,
            WallpaperMotionMode.NORMAL, false, ScenePowerFacts(false,80,false))
        for (theme in NeonTheme.entries) {
            val sheet = Bitmap.createBitmap(960, 4 * 320, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet); canvas.drawColor(Color.DKGRAY)
            val label = Paint().apply { color=Color.WHITE; textSize=24f }
            val renderer = NeonSceneRenderer(context,theme,holder) { error("Direct Canvas render") }
            try {
                for ((row,phase) in DayPhase.entries.withIndex()) {
                    val resource = context.resources.getIdentifier(theme.resourcePrefix + "_blink_" + phase.name.lowercase(), "drawable", context.packageName)
                    assertTrue(resource != 0)
                    for ((column,blink) in listOf(0f, .5f, 1f).withIndex()) {
                        val bitmap = Bitmap.createBitmap(941,1672,Bitmap.Config.ARGB_8888)
                        renderer.draw(Canvas(bitmap), NeonFrame(phase,phase,0,0,motion,NeonInteractionFrame(blink=blink)),10_000)
                        val face = when(theme) {
                            NeonTheme.SAKURA -> Rect(340,660,450,770)
                            NeonTheme.HARBOR -> Rect(450,605,560,715)
                            NeonTheme.SUNSET -> Rect(315,520,425,630)
                        }
                        canvas.drawBitmap(bitmap,face,Rect(column*320,row*320+35,column*320+300,row*320+315),Paint(Paint.FILTER_BITMAP_FLAG))
                        canvas.drawText("${phase.name} $blink",column*320f+8,row*320f+28,label)
                        bitmap.recycle()
                    }
                }
                val file = File(context.getExternalFilesDir("neon-interactions-review"),theme.setId+"-native-blinks.png")
                file.outputStream().use { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG,100,it)) }
            } finally { renderer.close(); sheet.recycle() }
        }
    }
}
