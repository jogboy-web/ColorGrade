package com.colorgrade.app.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menyimpan hasil edit (bytes JPEG) ke galeri perangkat.
 *
 * Strategi (mendukung semua perangkat dari Android 7 = API 24 ke atas):
 *  - Android 10 ke atas (API 29+): menulis lewat [MediaStore] langsung ke
 *    koleksi gambar. TIDAK memerlukan izin storage.
 *  - Android 7-9 (API 24-28): menulis ke file publik Pictures/ColorGrade
 *    lalu memicu MediaScanner. Membutuhkan izin WRITE_EXTERNAL_STORAGE
 *    (dideklarasikan dengan maxSdkVersion=28 di manifest agar tidak mengganggu
 *    perangkat modern).
 *
 * Hasil disimpan sebagai:
 *   DCIM  -> (Android 10+) gambar tersimpan di koleksi Pictures.
 *   Nama  -> ColorGrade_<timestamp>.jpg  (nama unik agar tidak menimpa).
 *
 * [save] memblokir thread pemanggil ketika menulis ke disk. Sebaiknya dipanggil
 * dari thread latar (bukan main thread) agar tidak memblokir UI.
 */
object PhotoSaver {

    /** Menyimpan JPEG [bytes] ke galeri. Mengembalikan Uri hasil, atau null bila gagal. */
    fun save(context: Context, bytes: ByteArray, displayName: String = defaultDisplayName()): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bytes, displayName)
            } else {
                saveViaFile(context, bytes, displayName)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Membuat nama file unik berbasis waktu. */
    fun defaultDisplayName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "ColorGrade_$stamp.jpg"
    }

    /** Android 10+ : tulis langsung ke MediaStore (tanpa izin). */
    private fun saveViaMediaStore(
        context: Context,
        bytes: ByteArray,
        displayName: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ColorGrade")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val resolver = context.contentResolver

        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null

            // Tandai selesai agar galeri menampilkannya.
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            uri
        } catch (e: Exception) {
            // Batal jika gagal menulis.
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Android 7-9 : tulis file publik lalu pindai dengan MediaScanner. */
    private fun saveViaFile(
        context: Context,
        bytes: ByteArray,
        displayName: String
    ): Uri? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "ColorGrade"
        )
        if (!dir.exists()) {
            if (!dir.mkdirs()) return null
        }

        val file = File(dir, displayName)
        file.writeBytes(bytes)

        // Beri tahu galeri bahwa ada file baru.
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)

        return Uri.fromFile(file)
    }
}
