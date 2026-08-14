package com.colorgrade.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface as AndroidXExif
import java.io.InputStream

/**
 * Membantu memuat dan membaca informasi foto dari [Uri] yang diberikan
 * oleh sistem Photo Picker / Gallery.
 *
 * Menyediakan pemuatan berukuran terkendali (anti-boros RAM) sekaligus
 * memperbaiki orientasi berdasarkan metadata EXIF.
 */
object PhotoManager {

    /** Ukuran penuh pratinjau editor (cukup besar agar terlihat jelas). */
    const val MAX_EDIT_DIMENSION = 2048

    /** Ukuran kecil untuk pratinjau cepat saat menggeser slider. */
    const val MAX_PREVIEW_DIMENSION = 1000

    /**
     * Nama file foto dari [uri] bila tersedia. Mengembalikan null jika tidak diketahui.
     */
    fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Memuat [Bitmap] dari [uri], dikunci agar sisi terpanjangnya <= [maxDimension],
     * dan orientasinya diperbaiki menurut metadata EXIF. Mengembalikan null jika gagal.
     */
    fun loadBitmap(context: Context, uri: Uri, maxDimension: Int = MAX_EDIT_DIMENSION): Bitmap? {
        return try {
            val resolver = context.contentResolver

            // 1) Baca dimensi asli tanpa mendecode penuh.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 2) Hitung sample size agar hasil <= maxDimension.
            var sampleSize = 1
            var maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / 2 >= maxDimension) {
                maxDim /= 2
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            // 3) Perbaiki orientasi.
            val rotation = readRotation(resolver.openInputStream(uri))
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }

            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /** Membaca sudut rotasi (derajat) dari aliran EXIF. 0 bila normal/tidak ada. */
    private fun readRotation(input: InputStream?): Float {
        if (input == null) return 0f
        return try {
            val exif = AndroidXExif(input)
            when (exif.getAttributeInt(AndroidXExif.TAG_ORIENTATION, AndroidXExif.ORIENTATION_NORMAL)) {
                AndroidXExif.ORIENTATION_ROTATE_90 -> 90f
                AndroidXExif.ORIENTATION_ROTATE_180 -> 180f
                AndroidXExif.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }
    }

    /** Nama basis file (tanpa ekstensi) dari [uri]. */
    fun queryBaseName(context: Context, uri: Uri): String {
        val name = queryDisplayName(context, uri) ?: return "ColorGrade"
        return name.substringBeforeLast('.')
    }

    /**
     * Mengonversi [bitmap] menjadi JPEG [ByteArray] (kualitas 90).
     * Mengembalikan null bila bitmap tidak dapat dikompres.
     */
    fun bitmapToJpeg(bitmap: Bitmap): ByteArray? {
        return try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
