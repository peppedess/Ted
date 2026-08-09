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

    /** Avatar: 64 px bastano per un cerchio da 32 dp anche su schermi densi. */
    fun avatar(path: String): ByteArray? = scale(path, 64, 65)?.bytes

    fun thumbnail(path: String): Thumb? = scale(path, MAX_DIM, QUALITY)

    private fun scale(path: String, maxDim: Int, quality: Int): Thumb? = runCatching {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        // Prima passata: leggiamo solo le dimensioni, senza allocare i pixel.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > maxDim * 2) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val scale = maxDim.toFloat() / max(decoded.width, decoded.height)
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
        target.compress(Bitmap.CompressFormat.JPEG, quality, out)

        val thumb = Thumb(out.toByteArray(), target.width, target.height)
        if (target !== decoded) target.recycle()
        decoded.recycle()
        thumb
    }.getOrNull()
}
