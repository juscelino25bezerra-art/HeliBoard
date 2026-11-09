// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * From Android 15 (API 35) on, apps targeting API 35+ can't set the navigation bar color any more:
 * Window.setNavigationBarColor is ignored and the area below the keyboard stays transparent.
 * Instead, we let the keyboard frame extend behind the navigation bar and paint that strip ourselves.
 */
object NavBarBackground {
    private fun isActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val values = Settings.getValues()
        return values.mCustomNavBarColor && !values.mIsFloatingKeyboard
    }

    /** Call once when the input view is created. [frame] is the bottom-aligned keyboard frame. */
    fun setup(frame: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bottom = if (isActive()) insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom else 0
            v.updatePadding(bottom = bottom)
            v.background = if (bottom > 0) BottomStripDrawable(Settings.getValues().mColors.get(ColorType.NAVIGATION_BAR), bottom) else null
            insets
        }
    }

    /** Call whenever the keyboard is shown, so changed settings (color, floating mode, the option itself) apply. */
    fun apply(window: Window?, frame: View?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || window == null) return
        WindowCompat.setDecorFitsSystemWindows(window, !isActive())
        frame?.let { ViewCompat.requestApplyInsets(it) }
    }

    /** Paints only the bottom [height] pixels, i.e. the part behind the navigation bar. */
    private class BottomStripDrawable(color: Int, private val height: Int) : Drawable() {
        private val paint = Paint().apply { this.color = color }
        override fun draw(canvas: Canvas) {
            val b = bounds
            canvas.drawRect(b.left.toFloat(), (b.bottom - height).toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
