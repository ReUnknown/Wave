package app.wave.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.toPath
import androidx.media3.common.Player
import androidx.media3.session.MediaButtonReceiver
import app.wave.music.MainActivity
import app.wave.music.R
import app.wave.music.playback.QueueStore
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.atSize
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen player: the cover cut into a scalloped cookie shape, previous/next in a pill,
 * and a round play button, all in your wallpaper's colors. Shows the last song even when Wave
 * isn't running; play brings the last queue back.
 */
class WaveWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, null)

    companion object {
        private const val ART_PX = 480
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var artFor: String? = null
        private var art: Bitmap? = null
        private var loading: Job? = null

        /** Redraws every Wave widget for what [player] is doing (or the last saved song). */
        fun update(context: Context, player: Player?) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, WaveWidget::class.java))
            if (ids.isEmpty()) return
            val item = player?.currentMediaItem ?: QueueStore.read(app)?.let { it.items[it.index] }
            val id = item?.mediaId
            val artUrl = item?.mediaMetadata?.artworkUri?.toString()
            val playing = player?.isPlaying == true

            push(app, manager, ids, playing, art.takeIf { artFor == id && id != null } ?: placeholder(app))
            if (id != null && id != artFor && artUrl != null) {
                loading?.cancel()
                loading = scope.launch {
                    val shaped = shapedArt(app, artUrl) ?: return@launch
                    artFor = id
                    art = shaped
                    push(app, manager, manager.getAppWidgetIds(ComponentName(app, WaveWidget::class.java)), playing, shaped)
                }
            }
        }

        private fun push(context: Context, manager: AppWidgetManager, ids: IntArray, playing: Boolean, cover: Bitmap) {
            val views = RemoteViews(context.packageName, R.layout.widget_player).apply {
                setImageViewBitmap(R.id.widget_art, cover)
                setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                setContentDescription(R.id.widget_play, context.getString(if (playing) R.string.widget_pause else R.string.widget_play))
                setOnClickPendingIntent(R.id.widget_art, openApp(context))
                setOnClickPendingIntent(R.id.widget_play, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 1))
                setOnClickPendingIntent(R.id.widget_previous, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 2))
                setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, 3))
            }
            manager.updateAppWidget(ids, views)
        }

        private fun openApp(context: Context) = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        /** Buttons act like headphone buttons; Media3 starts playback (or resumes the last queue). */
        private fun mediaButton(context: Context, keyCode: Int, request: Int) = PendingIntent.getBroadcast(
            context, request,
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .setClass(context, MediaButtonReceiver::class.java)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        private fun cookie(size: Int) = MaterialShapes.Cookie12Sided.normalized().toPath().apply {
            transform(Matrix().apply { setScale(size.toFloat(), size.toFloat()) })
        }

        /** The cover, center-cropped and cut into the cookie shape with smooth edges. */
        private suspend fun shapedArt(context: Context, url: String): Bitmap? {
            val result = SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context).data(url.atSize(ArtSize.CARD)).size(ART_PX).allowHardware(false).build(),
            )
            val source = (result as? SuccessResult)?.image?.toBitmap() ?: return null
            return withContext(Dispatchers.Default) {
                val out = Bitmap.createBitmap(ART_PX, ART_PX, Bitmap.Config.ARGB_8888)
                val scale = ART_PX / minOf(source.width, source.height).toFloat()
                val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply {
                        setScale(scale, scale)
                        postTranslate((ART_PX - source.width * scale) / 2f, (ART_PX - source.height * scale) / 2f)
                    })
                }
                Canvas(out).drawPath(cookie(ART_PX), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
                out
            }
        }

        private var placeholderCache: Bitmap? = null

        /** Nothing played yet: the cookie in your wallpaper's color with Wave's two waves. */
        private fun placeholder(context: Context): Bitmap = placeholderCache ?: run {
            val out = Bitmap.createBitmap(ART_PX, ART_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawPath(cookie(ART_PX), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(android.R.color.system_accent1_700) })
            ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.apply {
                setTint(context.getColor(android.R.color.system_accent1_100))
                setBounds(0, 0, ART_PX, ART_PX)
                draw(canvas)
            }
            out.also { placeholderCache = it }
        }
    }
}
