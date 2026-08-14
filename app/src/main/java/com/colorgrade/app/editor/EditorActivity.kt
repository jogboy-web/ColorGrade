package com.colorgrade.app.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.colorgrade.app.R
import com.colorgrade.app.databinding.ActivityEditorBinding
import com.colorgrade.app.image.PhotoManager
import com.colorgrade.app.preset.Preset
import com.colorgrade.app.preset.PresetRepository
import com.colorgrade.app.preset.Presets
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Layar Editor — Redesain Tahap 9.
 *
 * Editor dibagi dua area:
 *   - Area atas (~3/4 layar): foto preview (fokus utama, fitCenter, tidak crop).
 *   - Panel kontrol bawah (~1/4 layar): hanya satu mode aktif per waktu.
 *
 * DUA MODE:
 *   1. MODE PRESET (default) — daftar thumbnail GAMBAR. Setiap thumbnail
 *      memakai foto referensi yang SAMA + color grading masing-masing preset,
 *      sehingga user dapat membandingkan efek secara langsung. Ketuk thumbnail
 *      -> preview utama langsung menampilkan hasil (sementara). Belum permanen
 *      sampai tombol "Pakai Preset" ditekan.
 *   2. MODE ADJUSTMENT — lima slider manual (Brightness/Contrast/Saturation/
 *      Temperature/Tint), berupa fine-tuning setelah preset diterapkan, plus
 *      tombol Reset, Kembali (ke mode Preset), dan Selesai.
 *
 * Pipeline render TIDAK berubah (menggunakan engine yang sudah ada):
 *   originalPhoto -> BrightnessEngine -> ContrastEngine -> SaturationEngine
 *                   -> TemperatureEngine -> TintEngine -> preview.
 *
 * Prinsip:
 *   - originalPhoto TIDAK pernah diubah. Sumber kebenaran semua render.
 *   - Setiap render dihitung dari originalPhoto, bukan hasil sebelumnya.
 *   - Anti-lag memakai renderGeneration + renderExecutor single-thread.
 *   - Preset hanya menyimpan kombinasi nilai; TIDAK ada engine khusus preset.
 *   - Saat "Selesai", hasil render terakhir dikirim kembali ke Home sebagai
 *     editedPhoto; originalPhoto tetap utuh di Home (Before/After).
 */
class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding

    /** Kunci Intent extra untuk mengirim hasil edit (JPEG bytes) ke Home. */
    companion object {
        const val EXTRA_EDITED_BYTES = "com.colorgrade.app.extra.EDITED_BYTES"
    }

    private var photoUri: Uri? = null

    /** Bitmap asli (preview kecil). Sumber kebenaran semua render. JANGAN direcycle. */
    private var originalPhoto: Bitmap? = null

    /** Bitmap hasil render yang sedang tampil. */
    private var previewPhoto: Bitmap? = null

    /** Nilai adjustment aktif (-100..100). Ini adalah state manual yang sesungguhnya. */
    private var brightnessValue: Int = 0
    private var contrastValue: Int = 0
    private var saturationValue: Int = 0
    private var temperatureValue: Int = 0
    private var tintValue: Int = 0

    /** Preset yang telah diterapkan (null = nilai sudah menyimpang => Custom). */
    private var activePreset: Preset? = null

    /**
     * Preset yang sementara dipilih user di mode Preset (belum diterapkan).
     * Nilai state BELUM diubah sampai "Pakai Preset" ditekan.
     */
    private var selectedPreset: Preset = Presets.ORIGINAL

    /** Apakah mode saat ini ADJUSTMENT (true) atau PRESET (false). Default PRESET. */
    private var isAdjustMode = false

    /** Set true saat "Selesai"; hasil render terakhir akan dikirim lalu Activity ditutup. */
    private var doneRequested = false

    /** Referensi foto kecil untuk thumbnail preset (dari asset). Bisa null -> pakai fallback. */
    private var referencePhoto: Bitmap? = null

    /** Pasangan (Preset, View) untuk menerapkan indikator selected (+check pada label). */
    private data class ThumbHolder(
        val preset: Preset,
        val wrapper: LinearLayout,
        val label: TextView
    )
    private val thumbHolders: MutableList<ThumbHolder> = ArrayList()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Worker tunggal untuk render di thread latar (mencegah penumpukan thread). */
    private val renderExecutor = Executors.newSingleThreadExecutor()

    /** Nomor generasi render. Hanya hasil paling baru yang boleh tampil. */
    private var renderGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photoUri = intent.data

        // Header "Kembali" bersifat kontekstual (Butir 5):
        //   - Mode Preset  -> keluar dari Editor (kembali ke Home).
        //   - Mode Adjustment -> kembali ke Mode Preset.
        binding.btnBack.setOnClickListener {
            if (isAdjustMode) switchToPresetMode() else finish()
        }
        binding.btnReset.setOnClickListener { resetAll() }
        binding.btnApplyPreset.setOnClickListener { applySelectedPreset() }
        binding.btnBackToPreset.setOnClickListener { switchToPresetMode() }
        binding.btnDone.setOnClickListener { onDone() }

        binding.sliderBrightness.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            onBrightnessChanged(value.toInt())
        }
        binding.sliderContrast.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            onContrastChanged(value.toInt())
        }
        binding.sliderSaturation.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            onSaturationChanged(value.toInt())
        }
        binding.sliderTemperature.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            onTemperatureChanged(value.toInt())
        }
        binding.sliderTint.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            onTintChanged(value.toInt())
        }

        // Siapkan foto referensi thumbnail di thread latar, lalu bangun panel preset.
        preparePresetPanel()

        // Mode awal: PRESET.
        switchToPresetMode()

        loadPhoto()
    }

    /**
     * Memuat foto referensi kecil untuk thumbnail, membangun thumbnail ber-graded,
     * lalu menaruhnya ke panel preset (scroll horizontal).
     */
    private fun preparePresetPanel() {
        binding.progressBar.visibility = View.VISIBLE
        renderExecutor.execute {
            val ref = PresetRepository.loadReferencePhoto(this) ?: PresetRepository.buildFallbackReference()
            val thumbnails = PresetRepository.buildThumbnails(ref)
            mainHandler.post {
                binding.progressBar.visibility = View.GONE
                referencePhoto = ref
                buildPresetThumbnails(thumbnails)
            }
        }
    }

    /** Membangun daftar thumbnail (gambar + label + indikator selected). */
    private fun buildPresetThumbnails(thumbnails: List<Pair<Preset, Bitmap>>) {
        val container = binding.containerPresetThumbs
        container.removeAllViews()
        thumbHolders.clear()

        val wrap = 8.dp
        val itemWidth = 88.dp

        for ((preset, thumb) in thumbnails) {
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(wrap, 4.dp, wrap, 4.dp)
                background = roundedBg(false)
            }
            val lp = LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            wrapper.layoutParams = lp

            val img = ImageView(this).apply {
                setImageBitmap(thumb)
                contentDescription = preset.name
            }
            val imgLp = LinearLayout.LayoutParams((itemWidth - wrap * 2), 64.dp)
            img.layoutParams = imgLp
            wrapper.addView(img)

            val label = TextView(this).apply {
                text = preset.name
                textSize = 11f
                setTextColor(getColor(R.color.cg_text_primary))
                gravity = Gravity.CENTER
            }
            val labelLp = LinearLayout.LayoutParams(
                (itemWidth - wrap * 2), LinearLayout.LayoutParams.WRAP_CONTENT
            )
            labelLp.topMargin = 4.dp
            label.layoutParams = labelLp
            wrapper.addView(label)

            wrapper.setOnClickListener { onPresetClicked(preset.id) }
            container.addView(wrapper)
            thumbHolders.add(ThumbHolder(preset, wrapper, label))
        }
        applyPresetSelection(selectedPreset.id)
    }

    /**
     * Latar card pada item thumbnail; border aksen + isi tipis bila [selected]
     * agar pilihan langsung terlihat (Tahap 15). Isi dipakai warna accent dengan
     * alpha lembut, tanpa mengganti palet yang sudah ada.
     */
    private fun roundedBg(selected: Boolean): android.graphics.drawable.Drawable {
        val accent = getColor(R.color.cg_accent)
        val fill = if (selected) {
            // Tint lembut accent pada isi card agar selected tidak hanya dibedakan border.
            androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 42)
        } else {
            getColor(R.color.cg_surface)
        }
        val g = GradientDrawable().apply {
            cornerRadius = 12.dp.toFloat()
            setColor(fill)
            setStroke(3.dp, if (selected) accent else Color.TRANSPARENT)
        }
        return g
    }

    /** Memberi indikator selected pada wrapper thumbnail + check pada label yang sesuai id. */
    private fun applyPresetSelection(id: String) {
        for (holder in thumbHolders) {
            val isSel = holder.preset.id == id
            holder.wrapper.background = roundedBg(isSel)
            // Check mark sebelum nama + aksen warna label = indikator pilihan yang jelas (Tahap 11/15).
            holder.label.text = if (isSel) "✓ " + holder.preset.name else holder.preset.name
            holder.label.setTextColor(
                if (isSel) getColor(R.color.cg_accent) else getColor(R.color.cg_text_primary)
            )
        }
    }

    /**
     * User mengetuk thumbnail -> hanya menyoroti + menampilkan PREVIEW hasil preset
     * pada preview utama. Nilai state BELUM diubah sampai "Pakai Preset" ditekan.
     */
    private fun onPresetClicked(id: String) {
        val preset = Presets.byId(id) ?: Presets.ORIGINAL
        selectedPreset = preset
        applyPresetSelection(preset.id)
        // Status jelaskan ini baru PRAINJAU (belum diterapkan), Tahap 11 Butir 11/12.
        binding.tvPreviewStatus.text =
            getString(R.string.preset_status_preview, preset.name)
        requestPresetPreview(preset)
    }

    /**
     * Menampilkan pratinjau sementara hasil [preset] pada preview utama,
     * berangkat dari originalPhoto (pipeline yang sama). Tidak mengubah state.
     */
    private fun requestPresetPreview(preset: Preset) {
        val original = originalPhoto ?: return
        val generation = ++renderGeneration

        // Foto asli untuk Original: langsung tampil tanpa pipeline.
        if (preset.id == Presets.ORIGINAL.id) {
            binding.progressBar.visibility = View.GONE
            setPreview(original)
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        renderExecutor.execute {
            if (generation != renderGeneration) return@execute
            val b1 = BrightnessEngine.apply(original, preset.brightness)
            if (generation != renderGeneration) { safeRecycle(b1, original, null); return@execute }
            val b2 = ContrastEngine.apply(b1, preset.contrast)
            val b3 = SaturationEngine.apply(b2, preset.saturation)
            val b4 = TemperatureEngine.apply(b3, preset.temperature)
            val result = TintEngine.apply(b4, preset.tint)

            // Gagal generation -> lepas SEMUA (yang bukan original) termasuk result.
            if (generation != renderGeneration) {
                listOf(b1, b2, b3, b4, result).forEach { safeRecycle(it, original, null) }
                return@execute
            }
            mainHandler.post {
                if (generation != renderGeneration) {
                    listOf(b1, b2, b3, b4, result).forEach { safeRecycle(it, original, null) }
                    return@post
                }
                binding.progressBar.visibility = View.GONE
                // Sukses: recycle intermediate; simpan [result] untuk ditampilkan.
                listOf(b1, b2, b3, b4).forEach { safeRecycle(it, original, result) }
                setPreview(result)
            }
        }
    }

    /**
     * Tombol "Pakai Preset": menerapkan nilai preset ke state adjustment,
     * render, lalu pindah ke mode ADJUSTMENT.
     */
    private fun applySelectedPreset() {
        val p = selectedPreset
        activePreset = p
        brightnessValue = p.brightness
        contrastValue = p.contrast
        saturationValue = p.saturation
        temperatureValue = p.temperature
        tintValue = p.tint

        applyValuesToUi()
        requestRender() // render penuh dengan state yang baru (Original -> tanpa pipeline via requestRender path)

        switchToAdjustMode()
    }

    /** Mode awal = PRESET. */
    private fun switchToPresetMode() {
        isAdjustMode = false
        // Sinkronkan pilihan thumbnail dgn hasil aktual saat kembali dari Adjustment
        // agar indikator & status konsisten (Tahap 11 Butir 20).
        // Custom -> kembali mulai dari Original; preset aktif yang utuh -> tetap terpilih.
        val active = activePreset
        selectedPreset = active ?: Presets.ORIGINAL
        applyPresetSelection(selectedPreset.id)
        binding.panelAdjust.visibility = View.GONE
        binding.panelPreset.visibility = View.VISIBLE
        // Kembali ke mode Preset = memulai memilih preset dari titik awal yang bersih.
        updatePresetPreviewStatus()
        updatePresetStatusLabel()
        // Pastikan thumbnail yang aktif terlihat saat kembali ke Preset (Tahap 16).
        scrollToSelectedThumb()
    }

    /**
     * Menggulir HorizontalScrollView sehingga thumbnail [selectedPreset] tampak.
     * Berguna saat kembali dari Adjustment dan preset aktif berada di ujung daftar
     * (mis. Dramatis). Memakai API sederhana smoothScrollTo, tanpa library baru.
     * Aman dipanggil sebelum/meski layout belum selesai karena akan no-op bila
     * item tidak ditemukan.
     */
    private fun scrollToSelectedThumb() {
        val id = selectedPreset.id
        val holder = thumbHolders.firstOrNull { it.preset.id == id } ?: return
        val scroll = binding.presetScroll
        val thumbLeft = holder.wrapper.left
        val thumbWidth = holder.wrapper.width
        val viewportWidth = scroll.width
        // Jika item sepenuhnya terlihat, biarkan posisi apa adanya (tanpa lompat).
        if (viewportWidth > 0 && thumbWidth > 0) {
            val visibleFrom = scroll.scrollX
            val visibleTo = visibleFrom + viewportWidth
            if (thumbLeft >= visibleFrom && thumbLeft + thumbWidth <= visibleTo) {
                return
            }
        }
        // Pusatkan bila memungkinkan: allow komponen kanan item tetap di dalam viewport.
        val target = (thumbLeft - (scroll.width - thumbWidth) / 2).coerceAtLeast(0)
        scroll.smoothScrollTo(target, scroll.scrollY)
    }

    /** Pindah ke mode ADJUSTMENT. */
    private fun switchToAdjustMode() {
        isAdjustMode = true
        binding.panelPreset.visibility = View.GONE
        binding.panelAdjust.visibility = View.VISIBLE
        updatePresetStatusLabel()
    }

    /** Set posisi slider + nilainya sesuai state (tanpa memicu listener). */
    private fun applyValuesToUi() {
        binding.sliderBrightness.value = brightnessValue.toFloat()
        binding.sliderContrast.value = contrastValue.toFloat()
        binding.sliderSaturation.value = saturationValue.toFloat()
        binding.sliderTemperature.value = temperatureValue.toFloat()
        binding.sliderTint.value = tintValue.toFloat()
        binding.tvBrightnessValue.text = formatValue(brightnessValue)
        binding.tvContrastValue.text = formatValue(contrastValue)
        binding.tvSaturationValue.text = formatValue(saturationValue)
        binding.tvTemperatureValue.text = formatValue(temperatureValue)
        binding.tvTintValue.text = formatValue(tintValue)
    }

    /** Label status preset di panel Adjustment ("Diterapkan: X" / "Khusus"). */
    private fun updatePresetStatusLabel() {
        val active = activePreset
        if (active != null) {
            binding.tvPresetStatus.text =
                getString(R.string.preset_status_applied, active.name)
        } else {
            binding.tvPresetStatus.text = getString(R.string.preset_custom)
        }
    }

    /** Status praktis di panel Preset (pratinjau thumbnail / instruksi). */
    private fun updatePresetPreviewStatus() {
        val sel = selectedPreset
        // Original tidak dianggap "pratinjau" karena sama dengan foto asli.
        if (sel.id == Presets.ORIGINAL.id) {
            binding.tvPreviewStatus.setText(R.string.preset_status_initial)
        } else {
            binding.tvPreviewStatus.text =
                getString(R.string.preset_status_preview, sel.name)
        }
    }

    /** Jika nilai menyimpang dari preset aktif -> status menjadi Custom. */
    private fun refreshPresetStatus() {
        val current = activePreset ?: return
        val unchanged =
            brightnessValue == current.brightness &&
                contrastValue == current.contrast &&
                saturationValue == current.saturation &&
                temperatureValue == current.temperature &&
                tintValue == current.tint
        if (!unchanged) {
            activePreset = null
        }
        updatePresetStatusLabel()
    }

    /** Memuat foto asli (preview kecil) dari Uri. */
    private fun loadPhoto() {
        val uri = photoUri
        if (uri == null) {
            Toast.makeText(this, R.string.error_open_photo, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        Thread {
            val bitmap = PhotoManager.loadBitmap(this, uri, PhotoManager.MAX_PREVIEW_DIMENSION)
            mainHandler.post {
                if (bitmap == null) {
                    Toast.makeText(this, R.string.error_open_photo, Toast.LENGTH_SHORT).show()
                    finish()
                    return@post
                }
                originalPhoto = bitmap
                binding.progressBar.visibility = View.GONE
                setPreview(bitmap)
            }
        }.start()
    }

    /** Menampilkan [bitmap], melepas Bitmap hasil render lama bila aman. */
    private fun setPreview(bitmap: Bitmap) {
        val old = previewPhoto
        binding.ivPreview.setImageBitmap(bitmap)
        previewPhoto = bitmap
        if (old != null && old !== originalPhoto) {
            old.recycle()
        }
    }

    private fun onBrightnessChanged(value: Int) {
        brightnessValue = value
        binding.tvBrightnessValue.text = formatValue(value)
        refreshPresetStatus()
        requestRender()
    }

    private fun onContrastChanged(value: Int) {
        contrastValue = value
        binding.tvContrastValue.text = formatValue(value)
        refreshPresetStatus()
        requestRender()
    }

    private fun onSaturationChanged(value: Int) {
        saturationValue = value
        binding.tvSaturationValue.text = formatValue(value)
        refreshPresetStatus()
        requestRender()
    }

    private fun onTemperatureChanged(value: Int) {
        temperatureValue = value
        binding.tvTemperatureValue.text = formatValue(value)
        refreshPresetStatus()
        requestRender()
    }

    private fun onTintChanged(value: Int) {
        tintValue = value
        binding.tvTintValue.text = formatValue(value)
        refreshPresetStatus()
        requestRender()
    }

    /**
     * Mengantre render 5-engine terbaru di thread latar, berangkat dari
     * [originalPhoto] dengan STATE saat ini. Anti-lag via generation.
     */
    private fun requestRender() {
        val original = originalPhoto ?: return
        val brightness = brightnessValue
        val contrast = contrastValue
        val saturation = saturationValue
        val temperature = temperatureValue
        val tint = tintValue
        val generation = ++renderGeneration

        // Semua nilai 0 -> langsung foto asli tanpa pipeline (hemat CPU/RAM).
        if (brightness == 0 && contrast == 0 && saturation == 0 && temperature == 0 && tint == 0) {
            binding.progressBar.visibility = View.GONE
            setPreview(original)
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        renderExecutor.execute {
            if (generation != renderGeneration) return@execute

            val intermediates = ArrayList<Bitmap>(4)
            var result: Bitmap? = null
            try {
                val b1 = BrightnessEngine.apply(original, brightness)
                if (generation != renderGeneration) { safeRecycle(b1, original, null); return@execute }
                val b2 = ContrastEngine.apply(b1, contrast)
                if (generation != renderGeneration) {
                    safeRecycle(b1, original, null); safeRecycle(b2, original, null); return@execute
                }
                if (b2 !== b1) intermediates.add(b1)

                val b3 = SaturationEngine.apply(b2, saturation)
                if (generation != renderGeneration) {
                    safeRecycle(b1, original, null); safeRecycle(b2, original, null); safeRecycle(b3, original, null); return@execute
                }
                if (b3 !== b2) intermediates.add(b2)

                val b4 = TemperatureEngine.apply(b3, temperature)
                if (generation != renderGeneration) {
                    safeRecycle(b1, original, null); safeRecycle(b2, original, null); safeRecycle(b3, original, null); safeRecycle(b4, original, null); return@execute
                }
                if (b4 !== b3) intermediates.add(b3)

                val b5 = TintEngine.apply(b4, tint)
                result = b5
                if (b5 !== b4) intermediates.add(b4)
            } catch (e: Exception) {
                // Render gagal -> biarkan preview apa adanya.
            }

            if (generation != renderGeneration) {
                safeRecycle(result, original, null)
                intermediates.forEach { safeRecycle(it, original, null) }
                return@execute
            }

            mainHandler.post {
                if (generation != renderGeneration) {
                    safeRecycle(result, original, null)
                    intermediates.forEach { safeRecycle(it, original, null) }
                    return@post
                }
                binding.progressBar.visibility = View.GONE
                intermediates.forEach { safeRecycle(it, original, result) }
                result?.let { setPreview(it) }

                if (doneRequested) {
                    finishWithEditedPhoto()
                }
            }
        }
    }

    /** Reset: semua nilai 0, preset Original, preview kembali foto asli. */
    private fun resetAll() {
        brightnessValue = 0
        contrastValue = 0
        saturationValue = 0
        temperatureValue = 0
        tintValue = 0
        activePreset = Presets.ORIGINAL
        selectedPreset = Presets.ORIGINAL
        applyValuesToUi()
        applyPresetSelection(selectedPreset.id)
        updatePresetStatusLabel()
        // Sinkronkan status panel Preset Juga (berlaku bila Reset dari header).
        updatePresetPreviewStatus()

        // Batalkan render lama yang sedang berjalan agar tidak menimpa preview
        // original setelah Reset (anti-lag, Tahap 10 Butir 19/12).
        renderGeneration++

        val original = originalPhoto ?: return
        binding.progressBar.visibility = View.GONE
        setPreview(original)
    }

    /**
     * Tombol "Selesai": mengirim hasil edit terakhir ke Home lalu menutup Activity.
     * Jika render masih berjalan, tunggu hasil terakhir lalu kirim.
     */
    private fun onDone() {
        if (binding.progressBar.visibility == View.VISIBLE) {
            doneRequested = true
            return
        }
        finishWithEditedPhoto()
    }

    /** Mengirim bitmap yang sedang tampil sebagai hasil edit, lalu menutup Activity. */
    private fun finishWithEditedPhoto() {
        val current = previewPhoto ?: originalPhoto ?: run { finish(); return }
        val bytes = bitmapToBytes(current)
        val intent = intent
        intent.putExtra(EXTRA_EDITED_BYTES, bytes)
        setResult(RESULT_OK, intent)
        finish()
    }

    /** Konversi Bitmap ke JPEG bytes (ramah binder Intent). */
    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    /** Format nilai: +25, 0, -50. */
    private fun formatValue(value: Int): String {
        return if (value > 0) "+$value" else value.toString()
    }

    /** Recycle bitmap bila bukan [source] dan bukan [keep] (hindari recycle bitmap terpakai). */
    private fun safeRecycle(bitmap: Bitmap?, source: Bitmap?, keep: Bitmap?) {
        if (bitmap != null && bitmap !== source && bitmap !== keep && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        renderExecutor.shutdownNow()
        previewPhoto = null
        originalPhoto = null
        referencePhoto = null
        thumbHolders.clear()
        binding.ivPreview.setImageBitmap(null)
    }
}
