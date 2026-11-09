// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.WindowCompat
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import java.lang.ref.WeakReference

/**
 * From Android 15 (API 35) on, apps targeting API 35+ can't set the navigation bar color any more:
 * Window.setNavigationBarColor is ignored and the area below the keyboard stays transparent.
 * The keyboard layout itself is left untouched; instead we paint the part of the IME window that
 * lies behind the navigation bar:
 *  - with gesture navigation the IME window draws the navigation bar itself, in a NavigationBarFrame view
 *  - otherwise a parent of the input view is padded by the navigation bar height, and we paint that padding
 */
object NavBarBackground {
    private var active = false
    private var color = Color.TRANSPARENT
    private var inputRootRef = WeakReference<View>(null)
    private var listenedDecorRef = WeakReference<View>(null)

    /** Call whenever the keyboard is shown, so changed settings (colors, floating mode, the option itself) apply. */
    fun apply(window: Window?, inputRoot: View?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || window == null) return
        val values = Settings.getValues()
        active = values.mCustomNavBarColor && !values.mIsFloatingKeyboard
        color = values.mColors.get(ColorType.NAVIGATION_BAR)
        inputRootRef = WeakReference(inputRoot)
        if (active) // dark navigation buttons on bright keyboard colors (the old SYSTEM_UI_FLAG may be ignored)
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = isBrightColor(color)
        val decor = window.decorView
        if (listenedDecorRef.get() !== decor) {
            listenedDecorRef = WeakReference(decor)
            // the system may reset the frame background or change paddings, so re-apply after layout changes
            decor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> paint(decor, inputRootRef.get()) }
        }
        decor.post { paint(decor, inputRootRef.get()) }
    }

    private fun paint(decor: View, inputRoot: View?) {
        findNavigationBarFrame(decor)?.let { frame ->
            val wanted = if (active) color else Color.TRANSPARENT
            if ((frame.background as? ColorDrawable)?.color != wanted)
                frame.setBackgroundColor(wanted)
        }
        var v: View? = inputRoot?.parent as? View
        while (v != null) {
            val bg = v.background
            if (bg == null || bg is BottomStripDrawable) {
                val pad = v.paddingBottom
                val wanted = if (active && pad > 0) color else null
                if (wanted == null) {
                    if (bg != null) v.background = null
                } else if (bg !is BottomStripDrawable || bg.color != wanted || bg.height != pad) {
                    v.background = BottomStripDrawable(wanted, pad)
                }
            }
            if (v === decor) break
            v = v.parent as? View
        }
    }

    private fun findNavigationBarFrame(view: View): View? {
        if (view.javaClass.name.endsWith("NavigationBarFrame")) return view
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount)
            findNavigationBarFrame(view.getChildAt(i))?.let { return it }
        return null
    }

    /** Paints only the bottom [height] pixels of its bounds, i.e. the part behind the navigation bar. */
    private class BottomStripDrawable(val color: Int, val height: Int) : Drawable() {
        private val paint = Paint().apply { this.color = this@BottomStripDrawable.color }
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
