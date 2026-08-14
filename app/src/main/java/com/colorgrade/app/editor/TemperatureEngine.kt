package com.colorgrade.app.editor

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Mesin penyesuaian suhu warna (Temperature / warm-cool) untuk ColorGrade.
 *
 * Engine ini terpisah dari UI. Tugasnya:
 *   menerima Bitmap --> nilai temperature (-100..100)
 *   --> menghasilkan Bitmap BARU (Bitmap sumber tidak pernah diubah).
 *
 * Konsep yang dirasakan pengguna:
 *   - nilai negatif -> foto lebih DINGIN / kebiruan.
 *   - 0             -> warna asli.
 *   - nilai positif -> foto lebih HANGAT / kekuningan.
 *
 * Algoritma berbasis RGB yang sederhana dan stabil:
 *   - Green (G) TIDAK dijadikan channel utama dan tidak diubah.
 *   - Temperature positif: naikkan R, turunkan B.
 *   - Temperature negatif: turunkan R, naikkan B.
 *
 * Rumus:
 *   t     = value / 100f        (-1.0 .. +1.0)
 *   shift = t * MAX_SHIFT       (MAX_SHIFT = 45)
 *   R'    = clamp(R + shift)    dengan MAX_SHIFT bertanda positif = hangat
 *   G'    = G
 *   B'    = clamp(B - shift)
 *
 * Jadi saat value > 0: shift positif -> R naik, B turun (hangat).
 * Saat value < 0: shift negatif -> R turun, B naik (dingin).
 * Nilai hasil dibatasi 0..255 agar tidak keluar rentang.
 *
 * Performa: karena G tidak berubah, hanya dibutuhkan dua LUT kecil
 * (redLut[256], blueLut[256]) yang dibangun sekali per render, lalu dipakai
 * untuk seluruh pixel. Nilai 0 menghasilkan identitas langsung (tanpa Bitmap
 * baru) untuk hemat RAM/CPU.
 */
object TemperatureEngine {

    /** Rentang nilai slider temperature yang diterima. */
    const val MIN_VALUE = -100
    const val MAX_VALUE = 100

    /**
     * Kekuatan shift maksimum (dalam level 8-bit) pada value ekstrem ±100.
     * 45 cukup terlihat (hangat/dingin jelas) namun tidak membuat foto rusak
     * atau membalikkan warna terlalu jauh.
     */
    private const val MAX_SHIFT = 45f

    /**
     * Menghasilkan Bitmap baru dari [source] yang suhu warnanya diubah [value].
     *
     * @return Bitmap hasil, atau [source] itu sendiri bila [value] == 0.
     */
    fun apply(source: Bitmap, value: Int): Bitmap {
        val temperature = value.coerceIn(MIN_VALUE, MAX_VALUE)
        if (temperature == 0) return source

        // Tabel pemetaan: red dan blue.
        val shift = temperature / 100f * MAX_SHIFT
        val redLut = IntArray(256) { n ->
            (n + shift).roundToInt().coerceIn(0, 255)
        }
        val blueLut = IntArray(256) { n ->
            (n - shift).roundToInt().coerceIn(0, 255)
        }

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
            // G tidak berubah; hanya R dan B yang dipetakan lewat LUT.
            pixels[i] = (a shl 24) or (redLut[r] shl 16) or (g shl 8) or blueLut[b]
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }
}
