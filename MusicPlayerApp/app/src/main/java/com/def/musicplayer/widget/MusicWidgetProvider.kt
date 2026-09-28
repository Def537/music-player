package com.def.musicplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.def.musicplayer.MainActivity
import com.def.musicplayer.R
import com.def.musicplayer.data.SettingsStore
import com.def.musicplayer.playback.PlaybackService

/**
 * Віджет на головний екран у стилі застосунку: темний фон, акцентна рамка/кнопка
 * play (той самий колір, що обраний в темі), вінілова платівка як декоративний
 * елемент, білі іконки керування.
 *
 * Адаптивність: Android Widget API (RemoteViews) не підтримує CSS-подібне
 * автомасштабування чи кастомні View, тому "адаптивність" тут реалізована через
 * перемикання між ДВОМА заздалегідь підготовленими layout-ами (повний/компактний)
 * залежно від поточного розміру конкретного інстансу віджета — це стандартний
 * підхід для minSdk нижче за 31 (де з'явився офіційний API "responsive layouts").
 *
 * Кнопки шлють явні broadcast-повідомлення самому провайдеру (не MediaSession
 * напряму), а звідти команда ретранслюється в PlaybackService через Intent.
 */
class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.def.musicplayer.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.def.musicplayer.widget.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.def.musicplayer.widget.ACTION_PREVIOUS"

        // Нижче цієї ширини використовуємо компактний layout (лише платівка + play/pause)
        private const val COMPACT_WIDTH_THRESHOLD_DP = 180

        // Останній відомий стан — потрібен, щоб коректно перемалювати віджет при
        // зміні розміру (onAppWidgetOptionsChanged не отримує title/artist/isPlaying
        // ззовні, лише повідомляє про сам факт ресайзу).
        private var lastTitle: String = "Немає відтворення"
        private var lastArtist: String = ""
        private var lastIsPlaying: Boolean = false

        fun updateWidgets(context: Context, title: String, artist: String, isPlaying: Boolean) {
            lastTitle = title
            lastArtist = artist
            lastIsPlaying = isPlaying

            val manager = AppWidgetManager.getInstance(context)
            val ids = try {
                manager.getAppWidgetIds(ComponentName(context, MusicWidgetProvider::class.java))
            } catch (e: Exception) {
                return
            }
            if (ids.isEmpty()) return

            // Той самий акцентний колір, що й в основній темі застосунку — це і є
            // "widget повинен використовувати ту саму логіку theme colors" у межах
            // того, що дозволяє RemoteViews (дет. пояснення нижче в updateSingleWidget).
            val accentColor = try {
                SettingsStore(context).accentColorArgb
            } catch (e: Exception) {
                DEFAULT_ACCENT
            }

            ids.forEach { id ->
                try {
                    updateSingleWidget(context, manager, id, title, artist, isPlaying, accentColor)
                } catch (e: Exception) {
                    // Не даємо одному проблемному інстансу зламати оновлення решти
                }
            }
        }

        private fun updateSingleWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            title: String,
            artist: String,
            isPlaying: Boolean,
            accentColor: Int
        ) {
            val options = manager.getAppWidgetOptions(widgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val useCompact = minWidthDp in 1 until COMPACT_WIDTH_THRESHOLD_DP

            val layoutRes = if (useCompact) R.layout.widget_music_player_compact else R.layout.widget_music_player
            val views = RemoteViews(context.packageName, layoutRes)

            val density = context.resources.displayMetrics.density

            // Фон — заокруглена картка з тонкою акцентною рамкою. Через обмеження
            // RemoteViews (не можна перефарбувати XML-drawable у довільний колір
            // рантайм) генеруємо його як bitmap із потрібним кольором прямо тут.
            val bgWidthPx = (if (useCompact) 220 else 760)
            val bgHeightPx = (if (useCompact) 220 else 220)
            views.setImageViewBitmap(
                R.id.widget_bg,
                createCardBitmap(accentColor, bgWidthPx, bgHeightPx)
            )

            val playCircleSizePx = (44 * density).toInt()
            views.setImageViewBitmap(
                R.id.widget_play_pause_bg,
                createCircleBitmap(accentColor, playCircleSizePx)
            )
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            // Білі іконки поверх кольорового кола — setColorFilter доступний у RemoteViews
            // через generic reflection-сеттер (стандартний задокументований прийом).
            views.setInt(R.id.widget_play_pause, "setColorFilter", Color.WHITE)

            if (!useCompact) {
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_artist, artist)
                views.setInt(R.id.widget_prev, "setColorFilter", Color.WHITE)
                views.setInt(R.id.widget_next, "setColorFilter", Color.WHITE)
                views.setOnClickPendingIntent(R.id.widget_next, actionPendingIntent(context, ACTION_NEXT))
                views.setOnClickPendingIntent(R.id.widget_prev, actionPendingIntent(context, ACTION_PREVIOUS))
            }

            views.setOnClickPendingIntent(R.id.widget_play_pause, actionPendingIntent(context, ACTION_PLAY_PAUSE))

            val openApp = openAppPendingIntent(context)
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_album_art, openApp)

            manager.updateAppWidget(widgetId, views)
        }

        private val DEFAULT_ACCENT = 0xFFB33A2E.toInt()

        /** Заокруглена картка: напівпрозорий темний фон + тонка акцентна рамка. */
        private fun createCardBitmap(accentColor: Int, widthPx: Int, heightPx: Int): Bitmap {
            val w = widthPx.coerceAtLeast(1)
            val h = heightPx.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cornerRadius = h * 0.24f
            val inset = h * 0.03f
            val rect = RectF(inset, inset, w - inset, h - inset)

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(235, 0x12, 0x0D, 0x0B)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.STROKE
                strokeWidth = h * 0.018f
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

            return bitmap
        }

        private fun createCircleBitmap(color: Int, sizePx: Int): Bitmap {
            val size = sizePx.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            return bitmap
        }

        private fun openAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun actionPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS -> {
                val serviceIntent = Intent(context, PlaybackService::class.java).setAction(intent.action)
                try {
                    // КОРІННИЙ ФІКС Play "з нуля": PlaybackService — foreground-сервіс
                    // (mediaPlayback). Запуск через звичайний startService() з фонового
                    // контексту (віджет, застосунок не на екрані) не дає системі сигналу
                    // "зараз стану foreground" — і Android може не дозволити сервісу
                    // коректно піднятись/почати відтворення. startForegroundService()
                    // саме для такого сценарію і призначений.
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, lastTitle, lastArtist, lastIsPlaying)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Розмір інстансу змінився (користувач розтягнув/стиснув віджет) — перемальовуємо
        // з останнім відомим станом, щоб одразу підхопився компактний/повний layout.
        updateWidgets(context, lastTitle, lastArtist, lastIsPlaying)
    }
}
