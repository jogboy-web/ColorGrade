package com.colorgrade.app.preset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.colorgrade.app.editor.BrightnessEngine
import com.colorgrade.app.editor.ContrastEngine
import com.colorgrade.app.editor.SaturationEngine
import com.colorgrade.app.editor.TemperatureEngine
import com.colorgrade.app.editor.TintEngine
import java.io.IOException

/**
 * Sumber data preset + pembangkit thumbnail (Tahap 9).
 *
 * Tugas:
 *  1) Memuat satu foto referensi kecil yang DIGUNAKAN OLEH SEMUA thumbnail.
 *     Dengan foto dasar yang SAMA, user dapat membandingkan efek warna
 *     antar-preset secara langsung.
 *  2) Mengubah foto referensi itu menjadi beberapa Bitmap kecil — satu untuk
 *     tiap preset — dengan memakai pipeline engine yang sudah ada.
 *
 * Sumber foto referensi:
 *  - Prioritas: asset "presets/reference.jpg". Cukup ganti file ini untuk
 *    menukar foto referensi tanpa mengubah sistem preset.
 *  - Fallback: jika asset tidak terbaca, dibuat placeholder bergradasi aman
 *    sehingga thumbnail tetap tampil.
 */
object PresetRepository {

    /** Lokasi foto referensi di dalam asset aplikasi. */
    private const val REFERENCE_ASSET = "presets/reference.jpg"

    /** Ukuran sisi terpanjang thumbnail (agar hemat RAM; jauh dari resolusi penuh). */
    const val THUMB_MAX_DIM = 220

    /**
     * Memuat satu Bitmap kecil (foto referensi) dari asset.
     * Mengembalikan null bila gagal sehingga caller bisa menampilkan placeholder.
     */
    fun loadReferencePhoto(context: Context): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(REFERENCE_ASSET).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            var dim = maxOf(bounds.outWidth, bounds.outHeight)
            while (dim / 2 >= THUMB_MAX_DIM) {
                dim /= 2
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.assets.open(REFERENCE_ASSET).use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Membuat daftar thumbnail (Bitmap kecil) untuk setiap [Presets.ALL].
     * Setiap thumbnail dihasilkan dari [reference] yang sama + color grading preset.
     *
     * Diharapkan ukurannya kecil (sekitar [THUMB_MAX_DIM] pada sisi terpanjang).
     * Thumbnail yang dihasilkan TIDAK pernah mengubah [reference].
     *
     * @return daftar (Preset, Bitmap thumbnail) dengan urutan sama seperti [Presets.ALL].
     */
    fun buildThumbnails(reference: Bitmap): List<Pair<Preset, Bitmap>> {
        return Presets.ALL.map { preset ->
            preset to applyPipeline(reference, preset)
        }
    }

    /**
     * Menjalankan pipeline 5-engine terhadap [source] untuk nilai dari [preset].
     *
     *   source -> Brightness -> Contrast -> Saturation -> Temperature -> Tint -> hasil
     *
     * Mengembalikan Bitmap BARU (source tidak pernah diubah). Fungsi ini dipakai
     * untuk membangun thumbnail maupun untuk pratinjau preset di panel editor,
     * sehingga konsisten dengan pipeline render utama.
     */
    fun applyPipeline(source: Bitmap, preset: Preset): Bitmap {
        val b1 = BrightnessEngine.apply(source, preset.brightness)
        val b2 = ContrastEngine.apply(b1, preset.contrast)
        val b3 = SaturationEngine.apply(b2, preset.saturation)
        val b4 = TemperatureEngine.apply(b3, preset.temperature)
        val result = TintEngine.apply(b4, preset.tint)

        // Lepas intermediate yang berbeda dari sumber & hasil akhir dengan aman.
        for (bmp in listOf(b1, b2, b3, b4)) {
            if (bmp !== source && bmp !== result) bmp.recycle()
        }
        return result
    }

    /**
     * Placeholder aman yang dipakai apabila foto referensi gagal dimuat.
     * Bergradasi hangat-dingin agar tetap dapat memperlihatkan efek preset.
     */
    fun buildFallbackReference(w: Int = THUMB_MAX_DIM, h: Int = (THUMB_MAX_DIM * 0.75f).toInt()): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { isAntiAlias = true }
        val rows = 6
        val cols = 8
        val cellW = w / cols
        val cellH = h / rows
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val hue = (x * 45 + y * 15) % 360
                paint.color = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.85f))
                canvas.drawRect(
                    (x * cellW).toFloat(), (y * cellH).toFloat(),
                    ((x + 1) * cellW).toFloat(), ((y + 1) * cellH).toFloat(),
                    paint
                )
            }
        }
        return bmp
    }
}
