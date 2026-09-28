package com.def.musicplayer.data

import android.content.Context

/**
 * Легке сховище налаштувань вигляду застосунку. SharedPreferences тут цілком
 * достатньо — це кілька простих значень, не потрібна повноцінна БД.
 */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var isDarkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    /** Акцентний колір теми, у форматі ARGB Int (як у android.graphics.Color). */
    var accentColorArgb: Int
        get() = prefs.getInt(KEY_ACCENT, DEFAULT_ACCENT)
        set(value) = prefs.edit().putInt(KEY_ACCENT, value).apply()

    /**
     * JSON-знімок черги відтворення (трек, позиція, shuffle/repeat) — щоб застосунок
     * не "забував" усе, коли Android вбиває процес сервісу на паузі (звичайна поведінка
     * ОС для неактивних сервісів, не баг конкретно нашого коду).
     */
    var lastQueueJson: String?
        get() = prefs.getString(KEY_QUEUE, null)
        set(value) = prefs.edit().putString(KEY_QUEUE, value).apply()

    companion object {
        private const val KEY_DARK = "dark_theme"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_QUEUE = "last_queue_snapshot"

        // Той самий бордово-червоний, що був типовим кольором за замовчуванням
        val DEFAULT_ACCENT = 0xFFB33A2E.toInt()

        // Готовий набір акцентів для вибору користувачем
        val PRESET_ACCENTS = listOf(
            0xFFB33A2E.toInt(), // червоно-цегляний (типовий)
            0xFF3A6FB3.toInt(), // синій
            0xFF3A9B5C.toInt(), // зелений
            0xFF8A4FBF.toInt(), // фіолетовий
            0xFFC77A2E.toInt(), // помаранчевий
            0xFF2E9DA3.toInt(), // бірюзовий
            0xFFC24E8A.toInt(), // рожевий
            0xFF6B6B6B.toInt()  // нейтральний сірий
        )
    }
}
