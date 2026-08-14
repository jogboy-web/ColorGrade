package com.colorgrade.app.editor

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Mesin penyesuaian kecerahan (Brightness) untuk ColorGrade.
 *
 * Engine ini terpisah dari UI. Tugasnya sederhana:
 *   menerima Bitmap asli --> menerima nilai brightness (-100..100)
 *   --> menghasilkan Bitmap BARU (Bitmap asli tidak pernah diubah).
 *
 * Algoritma menggunakan kurva "lerp" (interpolasi linear) menuju batas
 * terang (putih) atau gelap (hitam) yang DILEMAH oleh faktor [MAX_EFFECT].
 * Tujuannya:
 *   - nilai hasil selalu di dalam rentang 0..255 (tidak overflow).
 *   - tidak langsung menjadi putih total / hitam total saat mudah +100/-100.
 *   - stabil dan mudah dipahami.
 */
object BrightnessEngine {

    /**
     * Nilai brightness yang diterima (rentang -100..100).
     * Eksternal dijadikan -1..1 [MAX_EFFECT] sehingga efek maksimum dibatasi.
     */
    const val MIN_VALUE = -100
    const val MAX_VALUE = 100

    /**
     * Seberapa kuat efek di nilai paling ekstrem. 0.9 artinya pada +100, gambar
     * mendekati terang tetapi masih menyisakan ~10% nilai asli (tidak putih total).
     * Nilai ini juga berlaku untuk sisi gelap (-100).
     */
    private const val MAX_EFFECT = 0.9f

    /**
     * Menghasilkan Bitmap baru dari [source] yang kecerahannya diubah sebesar
     * [value] (rentang dibatasi ke [-100, 100]).
     *
     * @return Bitmap hasil, atau [source] itu sendiri bila [value] == 0.
     */
    fun apply(source: Bitmap, value: Int): Bitmap {
        val brightness = value.coerceIn(MIN_VALUE, MAX_VALUE)
        if (brightness == 0) return source

        val width = source.width
        val height = source.height

        // Buat Tabel Lihat (LUT): nilai input 0..255 -> nilai output 0..255.
        // Satu LUT cukup untuk ketiga saluran (R, G, B) karena rumus sama.
        val lut = buildLut(brightness)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            pixels[i] = (a shl 24) or (lut[r] shl 16) or (lut[g] shl 8) or lut[b]
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /** Membangun tabel pemetaan 0..255 untuk nilai brightness tertentu. */
    private fun buildLut(brightness: Int): IntArray {
        val t = brightness / 100f // -1.0 .. 1.0
        val strength = kotlin.math.abs(t) * MAX_EFFECT

        return IntArray(256) { n ->
            val normalized = n / 255f
            val result = if (t >= 0f) {
                // Lebih terang: lerp menuju putih (nilai 1).
                normalized + strength * (1f - normalized)
            } else {
                // Lebih gelap: lerp menuju hitam (nilai 0).
                normalized - strength * normalized
            }
            // Keamanan akhir terhadap pembulatan float.
            (result * 255f).roundToInt().coerceIn(0, 255)
        }
    }
}
