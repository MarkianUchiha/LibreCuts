package com.tharunbirla.librecuts.utils

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Checks whether haptic feedback is enabled in user settings.
 */
fun View.isHapticFeedbackEnabled(): Boolean {
    val prefs = context.getSharedPreferences("librecuts_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("haptic_feedback", true)
}

/**
 * Performs haptic feedback only if enabled in user settings.
 */
fun View.performAppHapticFeedback(feedbackConstant: Int): Boolean {
    return if (isHapticFeedbackEnabled()) {
        this.performHapticFeedback(feedbackConstant)
    } else {
        false
    }
}

/**
 * Adds a bounce scale animation and haptic feedback to a view on touch.
 */
fun View.setBounceClickListener(onClick: () -> Unit) {
    this.setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
            }
            MotionEvent.ACTION_UP -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                if (v.isHapticFeedbackEnabled()) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                // If it's a valid click (inside bounds)
                if (event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height) {
                    onClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
        }
        true
    }
}

/**
 * Aparta el contenido de las barras del sistema y del recorte de pantalla, sumando esos insets al
 * padding que la vista ya traía. Con targetSdk 36 la app dibuja de borde a borde sin poder evitarlo
 * (`specs/target-sdk-36.md`, regla 1).
 *
 * El padding original se toma una sola vez: los insets vuelven a llegar en cada giro y cada vez que
 * una vista cambia de padre, y sumarlos al padding ya ajustado los iría acumulando.
 *
 * Los insets se devuelven sin consumir porque el editor detecta el teclado leyéndolos en la raíz.
 */
fun View.applySystemBarsPadding() {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(
            initialLeft + bars.left,
            initialTop + bars.top,
            initialRight + bars.right,
            initialBottom + bars.bottom
        )
        insets
    }
}

/**
 * Muestra el sheet; en una ventana baja abre expandido para que su botón de confirmar se vea sin
 * arrastrarlo (M-254, `specs/ventana-baja.md` regla 4). Con alto suficiente se comporta como siempre.
 */
fun BottomSheetDialog.showFitted(lowWindow: Boolean) {
    if (lowWindow) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }
    show()
}

/**
 * Standardized light haptic feedback for continuous scrubbing or dragging.
 */
fun View.performHapticLight() {
    if (isHapticFeedbackEnabled()) {
        this.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

/**
 * Standardized haptic feedback for selection or important clicks.
 */
fun View.performHapticClick() {
    if (isHapticFeedbackEnabled()) {
        this.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

