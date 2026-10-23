package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object AppIconHelper {

    fun loadAppIcon(context: Context, packageName: String): ImageBitmap? {
        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            drawableToImageBitmap(drawable, 96)
        } catch (e: Exception) {
            null
        }
    }

    fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = 96): ImageBitmap? {
        return try {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else sizePx
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else sizePx
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            val scaled = if (bitmap.width != sizePx || bitmap.height != sizePx) {
                Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
            } else {
                bitmap
            }
            scaled.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates a high-fidelity stylized colored icon bitmap for communication/social apps.
     */
    fun createStylizedIconBitmap(symbol: String, bgColorHex: Long, sizePx: Int = 96): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw rounded squircle / background
        paint.color = (bgColorHex or 0xFF000000).toInt()
        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Draw symbol / letter
        paint.color = android.graphics.Color.WHITE
        paint.textSize = sizePx * 0.44f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true

        val xPos = sizePx / 2f
        val yPos = (sizePx / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(symbol, xPos, yPos, paint)

        return bitmap.asImageBitmap()
    }
}
