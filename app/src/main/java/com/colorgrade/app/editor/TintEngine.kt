package com.colorgrade.app.editor

import android.graphics.Bitmap

/**
 * Mesin penyesuaian Tint (Hijau <-> Magenta) untuk ColorGrade.
 *
 * Engine terpisah dari UI. Tugasnya:
 *   menerima Bitmap --> nilai tint (-100..100)
 *   --> menghasilkan Bitmap BARU (Bitmap sumber tidak pernah diubah).
 *
 * Konsep yang dirasakan pengguna:
 *   - nilai negatif -> foto lebih HIJAU.
 *   - 0             -> warna asli.
 *   - nilai positif -> foto lebih MAGENTA (merah + biru, dominasi hijau berkurang).
 *
 * Algoritma berbasis RGB sederhana & stabil:
 *   magenta direpresentasikan melalui RED + BLUE, dikurangi pada GREEN.
 *   Untuk t > 0 :  R + shift,  G - shift,  B + shift   -> lebih magenta.
 *   Untuk t < 0 :  R - shift,  G + shift,  B - shift   -> lebih hijau.
 *
 * Rumus:
 *   t     = value / 100f        (-1.0 .. +1.0)
 *   shift = |t| * MAX_SHIFT
 *
 * MAX_SHIFT konservatif (32) agar efek tidak ekstrem; tujuan utamanya
 * color correction manual, bukan membanjiri foto dengan hijau/magenta.
 *
 * PERFORMANCE: karena seluruh 3 channel berubah, digunakan LUT untuk RED,
 * GREEN, dan BLUE (masing-masing 256 elemen). LUT dibangun sekali per render,
 * lalu dipakai untuk seluruh pixel tanpa perhitungan float per pixel.
 *
 * Saat value == 0, engine mengembalikan [source] langsung (tanpa Bitmap baru)
 * untuk hemat RAM/CPU dan menjaga konsistensi engine Brightness/Contrast/
 * Saturation/Temperature.
 */
object TintEngine {

    /** Rentang nilai slider tint yang diterima. */
    const val MIN_VALUE = -100
    const val MAX_VALUE = 100

    /**
     * Kekuatan shift maksimum (level 8-bit) pada value ekstrem ±100.
     * Konservatif agar hasil tetap terlihat sebagai koreksi warna halus.
     */
    private const val MAX_SHIFT = 32f

    /**
     * Menghasilkan Bitmap baru dari [source] yang tint-nya diubah [value].
     *
     * @return Bitmap hasil, atau [source] itu sendiri bila [value] == 0.
     */
    fun apply(source: Bitmap, value: Int): Bitmap {
        val tint = value.coerceIn(MIN_VALUE, MAX_VALUE)
        if (tint == 0) return source

        val shift = kotlin.math.abs(tint) / 100f * MAX_SHIFT
        // Delta per channel tergantung arah (positif = magenta, negatif = hijau).
        val redDelta = if (tint > 0) shift else -shift
        val greenDelta = if (tint > 0) -shift else shift
        val blueDelta = redDelta

        // LUT untuk tiga channel.
        val redLut = IntArray(256) { n -> (n + redDelta).toInt().coerceIn(0, 255) }
        val greenLut = IntArray(256) { n -> (n + greenDelta).toInt().coerceIn(0, 255) }
        val blueLut = IntArray(256) { n -> (n + blueDelta).toInt().coerceIn(0, 255) }

        val width = source.width
        val height = source.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            pixels[i] = (a shl 24) or (redLut[r] shl 16) or (greenLut[g] shl 8) or blueLut[b]
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }
}
