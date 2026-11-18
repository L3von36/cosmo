package com.loypos.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Global UI zoom ("make everything bigger or smaller").
 * The value scales BOTH density (dp — cards, buttons, paddings) and fontScale
 * (sp — all text), so the whole app zooms like a browser page. Read by
 * MainActivity (wraps the whole composition in a scaled LocalDensity) and
 * written live from the Settings → Appearance slider. Persisted in Room
 * settings under the key "ui_scale".
 */
object UiScale {
    const val MIN = 0.8f
    const val MAX = 1.5f
    const val DEFAULT = 1.0f

    var scale by mutableFloatStateOf(DEFAULT)
        private set

    /** Live preview while dragging the slider — recomposes the whole app. */
    fun preview(value: Float) {
        scale = value.coerceIn(MIN, MAX)
    }

    /** Reset from persisted settings at app start (ViewModel init). */
    fun hydrate(raw: Float?) {
        scale = (raw ?: DEFAULT).coerceIn(MIN, MAX)
    }

    fun reset() {
        scale = DEFAULT
    }
}
