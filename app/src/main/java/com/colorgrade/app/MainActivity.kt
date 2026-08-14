package com.colorgrade.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.colorgrade.app.databinding.ActivityMainBinding
import com.colorgrade.app.editor.EditorActivity
import com.colorgrade.app.export.PhotoSaver
import com.colorgrade.app.image.PhotoManager
import java.util.concurrent.Executors

/**
 * Layar utama ColorGrade.
 *
 * Home == pusat alur kerja user:
 *   1. Memilih foto dari Gallery / Android Photo Picker.
 *   2. Membuka Editor untuk mengubah warna & mood.
 *   3. Setelah selesai edit, hasil (JPEG bytes) dikirim kembali ke Home.
 *   4. Home menampilkan foto hasil, mendukung perbandingan Before/After,
 *      dan tombol Simpan menyimpan hasil ke galeri perangkat.
 *
 * Transisi "Sebelum" / "Setelah" dilakukan dengan menampilkan foto asli
 * (original) atau foto hasil edit (edited) pada ImageView yang sama,
 * bergantung pada nilai [showingAfter].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Bitmap foto asli (dari galeri). Basis perbandingan "Sebelum". */
    private var originalBitmap: Bitmap? = null

    /** Bitmap hasil edit dari Editor. Basis perbandingan "Setelah". */
    private var editedBitmap: Bitmap? = null

    /** Uri foto asli yang sedang dipilih. Pemicu untuk meluncurkan Editor. */
    private var selectedPhotoUri: Uri? = null

    /** Apakah yang sedang ditampilkan adalah hasil "Setelah" (true) atau "Sebelum" (false). */
    private var showingAfter: Boolean = false

    /** Worker tunggal untuk pemrosesan/penyimpanan di thread latar. */
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    // Photo Picker modern (Android 13+, dan 11-12 via backport). Tanpa izin storage.
    private val pickVisualMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                showPhoto(uri)
            }
            // uri == null artinya pengguna membatalkan pemilihan.
        }

    // Fallback untuk perangkat Android lama yang tidak punya Photo Picker.
    private val pickViaGetContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                showPhoto(uri)
            }
        }

    // Tantang Editor; hasilnya (bytes JPEG) diterima di sini.
    private val openEditor =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val bytes = result.data?.getByteArrayExtra(EditorActivity.EXTRA_EDITED_BYTES)
                if (bytes != null) {
                    showEditedPhoto(bytes)
                }
            }
            // resultCode != OK -> Editor dibatalkan; Home tetap apa adanya.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickPhoto.setOnClickListener { openPhotoPicker() }
        binding.cardPhoto.setOnClickListener { openPhotoPicker() }
        binding.placeholderLayout.setOnClickListener { openPhotoPicker() }

        binding.btnEditPhoto.setOnClickListener { launchEditor() }
        binding.btnSave.setOnClickListener { saveEditedPhoto() }
        binding.btnBeforeAfter.setOnClickListener { toggleBeforeAfter() }
    }

    /** Membuka Android Photo Picker, dengan fallback lama. */
    private fun openPhotoPicker() {
        try {
            pickVisualMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: ActivityNotFoundException) {
            try {
                pickViaGetContent.launch("image/*")
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.error_open_photo, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Menampilkan foto terpilih sebagai "Sebelum" dan mereset status edit. */
    private fun showPhoto(uri: Uri) {
        val bitmap = PhotoManager.loadBitmap(this, uri)
        if (bitmap == null) {
            Toast.makeText(this, R.string.error_open_photo, Toast.LENGTH_SHORT).show()
            return
        }

        // Ganti foto -> hasil edit lama dibuang (tidak relevan lagi).
        editedBitmap?.recycle()
        editedBitmap = null

        selectedPhotoUri = uri
        originalBitmap?.recycle()
        originalBitmap = bitmap
        showingAfter = false

        binding.ivPhoto.setImageBitmap(bitmap)
        binding.ivPhoto.visibility = View.VISIBLE
        binding.placeholderLayout.visibility = View.GONE

        binding.tvSubtitle.visibility = View.GONE
        binding.tvPhotoName.visibility = View.VISIBLE

        val name = PhotoManager.queryDisplayName(this, uri)
        binding.tvPhotoName.text = name ?: getString(R.string.photo_default_name)

        binding.btnPickPhoto.text = getString(R.string.change_photo)
        binding.btnEditPhoto.isEnabled = true

        // Belum ada hasil edit baru -> sembunyikan baris Before/After & Simpan.
        binding.btnCompareRow.visibility = View.GONE
        binding.btnBeforeAfter.text = getString(R.string.before)
    }

    /** Meluncurkan Editor dengan foto asli yang sedang dipilih. */
    private fun launchEditor() {
        val uri = selectedPhotoUri
        if (uri == null) {
            Toast.makeText(this, R.string.error_open_photo, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, EditorActivity::class.java).setData(uri)
        openEditor.launch(intent)
    }

    /** Menerima hasil edit (bytes) dari Editor dan menampilkannya selaku "Setelah". */
    private fun showEditedPhoto(bytes: ByteArray) {
        var editedOriginal =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: run {
                    Toast.makeText(this, R.string.error_open_photo, Toast.LENGTH_SHORT).show()
                    return
                }

        // Pastikan hasil edit tidak lebih besar dari pratinjau asli agar hemat RAM.
        val original = originalBitmap
        if (original != null) {
            val maxDim = maxOf(original.width, original.height)
            val scaled = scaleWithin(editedOriginal, maxDim)
            if (scaled !== editedOriginal) {
                editedOriginal.recycle()
                editedOriginal = scaled
            }
        }

        editedBitmap?.recycle()
        editedBitmap = editedOriginal
        showingAfter = true

        binding.ivPhoto.setImageBitmap(editedOriginal)
        binding.ivPhoto.visibility = View.VISIBLE
        binding.placeholderLayout.visibility = View.GONE

        // Tampilkan baris Before/After + Simpan.
        binding.btnCompareRow.visibility = View.VISIBLE
        refreshCompareButton()
    }

    /** Mengembalikan [bitmap] diskalakan agar sisi terpanjangnya <= [maxDim]. */
    private fun scaleWithin(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** Menukar tampilan antara foto asli (Sebelum) dan hasil edit (Setelah). */
    private fun toggleBeforeAfter() {
        val original = originalBitmap
        val edited = editedBitmap
        if (original == null) return
        if (edited == null) {
            // Belum ada hasil edit -> hanya bisa menampilkan asli.
            showingAfter = false
        } else {
            showingAfter = !showingAfter
        }

        binding.ivPhoto.setImageBitmap(if (showingAfter) edited else original)
        refreshCompareButton()
    }

    /** Memperbarui label tombol Before/After sesuai kondisi saat ini. */
    private fun refreshCompareButton() {
        binding.btnBeforeAfter.text =
            getString(if (showingAfter) R.string.before else R.string.after)
    }

    /** Menyimpan hasil edit ke galeri (di thread latar). */
    private fun saveEditedPhoto() {
        val bytes = editedBytes()
        if (bytes == null) {
            Toast.makeText(this, R.string.save_placeholder, Toast.LENGTH_SHORT).show()
            return
        }

        val context = applicationContext
        Toast.makeText(this, R.string.save_started, Toast.LENGTH_SHORT).show()

        backgroundExecutor.execute {
            val uri = PhotoSaver.save(context, bytes)
            runOnUiThread {
                val msg = if (uri != null) R.string.save_success else R.string.save_failed
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Bytes JPEG hasil edit (editedPhoto) untuk disimpan. */
    private fun editedBytes(): ByteArray? {
        // Penting (Tahap 10, Butir 17): tombol Simpan SELALU menyimpan hasil
        // edit (editedBitmap), bukan foto yang sedang ditampilkan. Jadi walau
        // user sedang menampilkan mode "Sebelum" (original), Simpan tetap
        // menyimpan editedPhoto, bukan originalPhoto.
        val bmp = editedBitmap ?: originalBitmap
        bmp ?: return null
        return PhotoManager.bitmapToJpeg(bmp)
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdownNow()
        originalBitmap?.recycle()
        editedBitmap?.recycle()
        originalBitmap = null
        editedBitmap = null
        binding.ivPhoto.setImageBitmap(null)
    }
}
