package com.colorgrade.app.editor

import android.graphics.Bitmap
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Mesin penyesuaian kontras (Contrast) untuk ColorGrade.
 *
 * Engine ini terpisah dari UI & Brightness. Tugasnya sederhana:
 *   menerima Bitmap (biasanya hasil Brightness) --> nilai contrast (-100..100)
 *   --> menghasilkan Bitmap BARU (Bitmap input tidak pernah diubah).
 *
 * "Kontras" diartikan sebagai: perbedaan antara bagian terang dan gelap.
 *   - Kontras rendah -> foto lebih datar/lembut (semua nilai didorong ke tengah).
 *   - Kontras 0      -> foto sama persis dengan sumber.
 *   - Kontras tinggi -> bagian terang naik, bagian gelap turun (lebih berbeda).
 *
 * Algoritma berbasis titik tengah 128 (midpoint): setiap saluran warna dihitung
 * ulang terhadap titik tengah, lalu hasilnya dibatasi 0..255.
 *
 * Peta slider -100..+100 ke kekuatan internal yang lebih terkendali:
 *   internalStrength = (|value| / 100f) ^ 1.5
 * Kurva pangkat ini membuat area kecil (10..90) terasa halus/sedikit, sedangkan
 * nilai ekstrem kecil (mendekati 100) tidak meledak. Arah tetap diambil dari
 * tanda value: positif = naikkan kontras, negatif = turunkan kontras.
 */
object ContrastEngine {

    /** Rentang nilai slider kontras yang diterima. */
    const val MIN_VALUE = -100
    const val MAX_VALUE = 100

    /** Batas maksimum intensitas internal (dilemahkan agar ekstrem aman). */
    private const val MAX_STRENGTH = 0.95f

    /** Titik tengah (midpoint) untuk pemetaan kontras pada 8-bit. */
    private const val MIDPOINT = 128f

    /**
     * Menghasilkan Bitmap baru dari [source] dengan kontras [value].
     *
     * @return Bitmap hasil, atau [source] itu sendiri bila [value] == 0.
     */
    fun apply(source: Bitmap, value: Int): Bitmap {
        val contrast = value.coerceIn(MIN_VALUE, MAX_VALUE)
        if (contrast == 0) return source

        val width = source.width
        val height = source.height

        // Satu LUT cukup untuk semua saluran (rumus sama untuk R, G, B).
        val lut = buildLut(contrast)

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

    /** Membangun tabel pemetaan 0..255 untuk kekuatan kontras tertentu. */
    private fun buildLut(contrast: Int): IntArray {
        val t = contrast / 100f // -1.0 .. +1.0
        val strength = kotlin.math.abs(t).pow(1.5f) * MAX_STRENGTH

        // Faktor kontras klasik: >1 menaikkan, <1 menurunkan.
        // Dibatasi agar tidak mencapai 0 (menghilangkan kontras total) saat -100.
        val minFactor = 0.12f
        val maxFactor = 3.2f
        val factor = when {
            t >= 0f -> 1f + strength * (maxFactor - 1f)
            else    -> 1f - strength * (1f - minFactor)
        }

        return IntArray(256) { n ->
            val result = (factor * (n - MIDPOINT) + MIDPOINT)
            result.roundToInt().coerceIn(0, 255)
        }
    }
}
