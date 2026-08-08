package it.peppedess.ted.bridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Riduce le immagini prima di spedirle sul Data Layer.
 *
 * Il collo di bottiglia e il Bluetooth, non lo schermo: su 450 pixel
 * una miniatura da 220 px e gia sovrabbondante, e pesa venti volte meno.
 */
object MediaScaler {

    private const val MAX_DIM = 220
    private const val QUALITY = 72

    data class Thumb(val bytes: ByteArray, val width: Int, val height: Int)

    fun thumbnail(path: String): Thumb? = runCatching {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        // Prima passata: leggiamo solo le dimensioni, senza allocare i pixel.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > MAX_DIM * 2) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val scale = MAX_DIM.toFloat() / max(decoded.width, decoded.height)
        val target = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)

        val thumb = Thumb(out.toByteArray(), target.width, target.height)
        if (target !== decoded) target.recycle()
        decoded.recycle()
        thumb
    }.getOrNull()
}
