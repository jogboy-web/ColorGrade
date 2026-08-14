package com.colorgrade.app.editor

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Mesin penyesuaian saturasi (Saturation / kekuatan warna) untuk ColorGrade.
 *
 * Engine ini terpisah dari UI. Tugasnya:
 *   menerima Bitmap --> nilai saturation (-100..100)
 *   --> menghasilkan Bitmap BARU (Bitmap sumber tidak pernah diubah).
 *
 * Penting: Saturation TIDAK boleh sekadar menaikkan R, G, B secara serempak
 * (itu justru mengubah kecerahan). Yang benar adalah mengukur SEBERAPA JAUH
 * sebuah warna dari "abu-abu" (gray/luma), lalu memperkuat atau menguranginya.
 *
 * Algoritma yang dipilih: SATURATION BERBASIS LUMA (luminance).
 *   - Hitung tingkat kecerahan warna (luma) untuk tiap pixel.
 *   - Luma dihitung dengan bobot persepsi mata: 0.299 R + 0.587 G + 0.114 B.
 *   - Untuk tiap saluran R/G/B:  channel' = luma + (channel - luma) * factor
 *       . factor < 1  -> setiap channel ditarik mendekati luma
 *                        -> warna semakin pucat (mendekati abu-abu).
 *       . factor = 1  -> salinan persis sumber (tidak berubah).
 *       . factor > 1  -> setiap channel dijauhkan dari luma
 *                        -> selisih terhadap luma menguat -> warna pekat.
 *
 * Mengapa ini tidak mengubah brightness:
 *   karena luma hasil tetap sama (rumus linear, bobot tetap), sehingga
 *   selisih terang/gelap tiap pixel tidak berpindah. Hanya perbedaan antar
 *   saluran (yaitu kejenuhan warna) yang diubah.
 *
 * Mengapa aman di grayscale:
 *   jika R = G = B (foto hitam-putih), maka channel == luma dan
 *   channel' = luma + 0 = channel. Tidak ada warna palsu yang dibuat,
 *   sesuai persyaratan pengujian foto hitam-putih.
 *
 * Nilai 0 (kembalian tanpa proses):
 *   saat [value] == 0, langsung mengembalikan [source] utuh tanpa membuat
 *   Bitmap baru, sehingga hemat RAM & waktu.
 */
object SaturationEngine {

    /** Rentang nilai slider saturasi yang diterima. */
    const val MIN_VALUE = -100
    const val MAX_VALUE = 100

    /**
     * Faktor ekstrem maksimum. Pada +100, channel dijauhkan dari luma
     * sebanyak MAX_POSITIVE_FACTOR (= 2.4x terhadap jarak aslinya).
     * Dibatasi agar warna pekat tetapi tidak pecah / kehilangan detail total.
     */
    private const val MAX_POSITIVE_FACTOR = 2.4f

    /**
     * Menghasilkan Bitmap baru dari [source] yang saturasinya diubah [value].
     *
     * @return Bitmap hasil, atau [source] itu sendiri bila [value] == 0.
     */
    fun apply(source: Bitmap, value: Int): Bitmap {
        val saturation = value.coerceIn(MIN_VALUE, MAX_VALUE)
        if (saturation == 0) return source

        // Tabel pemetaan brightness penuh -100..+100.
        val factor = computeFactor(saturation)

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

            // Luma berbobot persepsi mata (tetap dalam 0..255 bentuk float).
            val luma = 0.299f * r + 0.587f * g + 0.114f * b

            // Jauhkan / dekatkan tiap saluran dari abu-abu (luma).
            val nr = (luma + (r - luma) * factor)
            val ng = (luma + (g - luma) * factor)
            val nb = (luma + (b - luma) * factor)

            pixels[i] = (a shl 24) or
                (nr.roundToInt().coerceIn(0, 255) shl 16) or
                (ng.roundToInt().coerceIn(0, 255) shl 8) or
                nb.roundToInt().coerceIn(0, 255)
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Memetakan slider -100..+100 ke sebuah faktor saturasi internal.
     *
     *   value == 0  -> factor 1   (salinan identik).
     *   value  < 0  -> factor turun dari 1 ke 0  (menuju abu-abu penuh).
     *   value  > 0  -> factor naik dari 1 ke MAX_POSITIVE_FACTOR (menuju pekat).
     *
     * Kurva kekuatan memakai pangkat 1.2 pada |value/100| agar perubahan di
     * sekitar tengah slider tidak terlalu agresif, tetapi ekstrem tetap tercapai.
     */
    private fun computeFactor(value: Int): Float {
        val t = value / 100f                    // -1.0 .. +1.0
        val strength = abs(t).pow(1.2f)         // 0.0 .. 1.0 (kurva halus)

        return if (t < 0f) {
            // Sisi negatif: mendekati abu-abu. strength 1 -> factor 0.
            1f - strength
        } else {
            // Sisi positif: menguatkan. strength 1 -> factor MAX_POSITIVE_FACTOR.
            1f + strength * (MAX_POSITIVE_FACTOR - 1f)
        }
    }
}
