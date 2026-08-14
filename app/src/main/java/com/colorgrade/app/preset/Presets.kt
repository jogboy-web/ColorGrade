package com.colorgrade.app.preset

/**
 * Definisi Preset (Tahap 9, diperluas di Tahap 14 menjadi 12 preset).
 *
 * Sebuah preset hanyalah kumpulan nilai adjustment (Int) untuk kelima engine
 * yang sudah ada. Preset TIDAK menyimpan Bitmap dan tidak memiliki engine
 * image-processing sendiri; ia hanya layer konfigurasi di atas pipeline:
 *
 *   originalPhoto -> Brightness -> Contrast -> Saturation -> Temperature -> Tint -> preview
 *
 * Nilai semua dijamin berada dalam rentang -100..+100 agar kompatibel dengan
 * rentang slider yang sudah ada.
 *
 * [id]    kunci unik preset (digunakan untuk pencarian & indikator selected).
 * [name]  label tampilan yang ditulis kecil di bawah thumbnail preset.
 *
 * Setiap preset hanyalah kombinasi nilai — TIDAK ada engine khusus. Semua
 * preset melewati pipeline yang sama. Nilai Target sengaja tidak ekstrem agar
 * foto tetap terlihat alami, namun setiap preset tetap memiliki karakter yang
 * berbeda dari yang lain.
 */
data class Preset(
    val id: String,
    val name: String,
    val brightness: Int,
    val contrast: Int,
    val saturation: Int,
    val temperature: Int,
    val tint: Int
)

/**
 * Satu-satunya tempat definisi preset.
 *
 * Preset sengaja dibuat sedrhana (tidak ekstrem) agar hasil tetap terlihat
 * alami dan setiap preset berbeda satu sama lain.
 */
object Presets {

    /** Tanpa perubahan: semua nilai 0 (sama dengan foto asli / mode manual murni). */
    val ORIGINAL = Preset(
        id = "original",
        name = "Original",
        brightness = 0,
        contrast = 0,
        saturation = 0,
        temperature = 0,
        tint = 0
    )

    /** Hangat: temperatur dinaikkan, saturasi & kontras sedikit naik. */
    val WARM = Preset(
        id = "warm",
        name = "Hangat",
        brightness = 5,
        contrast = 8,
        saturation = 8,
        temperature = 35,
        tint = 3
    )

    /** Dingin: temperatur diturunkan, saturasi & kontras sedikit naik. */
    val COOL = Preset(
        id = "cool",
        name = "Dingin",
        brightness = 3,
        contrast = 8,
        saturation = 5,
        temperature = -35,
        tint = -3
    )

    /** Vivid: warna pekat & kontras lebih kuat. */
    val VIVID = Preset(
        id = "vivid",
        name = "Vivid",
        brightness = 3,
        contrast = 18,
        saturation = 28,
        temperature = 2,
        tint = 0
    )

    /** Film: kontras & saturasi sedikit turun, temperatur sedikit hangat. */
    val FILM = Preset(
        id = "film",
        name = "Film",
        brightness = 4,
        contrast = -5,
        saturation = -8,
        temperature = 12,
        tint = 4
    )

    /** B&W: saturasi -100, tanpa perubahan lain (hitam-putih). */
    val BNW = Preset(
        id = "bw",
        name = "Hitam Putih",
        brightness = 2,
        contrast = 12,
        saturation = -100,
        temperature = 0,
        tint = 0
    )

    /** Cerah: lebih terang & ringan, kontras sedikit berkurang. */
    val BRIGHT = Preset(
        id = "bright",
        name = "Cerah",
        brightness = 22,
        contrast = -5,
        saturation = 5,
        temperature = 5,
        tint = 0
    )

    /** Moody: gelap, kontras, dan sedikit muted. */
    val MOODY = Preset(
        id = "moody",
        name = "Moody",
        brightness = -12,
        contrast = 22,
        saturation = -12,
        temperature = -5,
        tint = 3
    )

    /** Vintage: hangat, faded, dan sedikit retro. */
    val VINTAGE = Preset(
        id = "vintage",
        name = "Vintage",
        brightness = 5,
        contrast = -12,
        saturation = -18,
        temperature = 25,
        tint = 8
    )

    /** Teal & Oranye: kontras warna hangat/dingin berkesan teal-oranye. */
    val TEAL_ORANGE = Preset(
        id = "teal_orange",
        name = "Teal & Oranye",
        brightness = 3,
        contrast = 18,
        saturation = 15,
        temperature = 18,
        tint = -12
    )

    /** Lembut: ringan, tidak terlalu kontras. */
    val SOFT = Preset(
        id = "soft",
        name = "Lembut",
        brightness = 8,
        contrast = -18,
        saturation = -5,
        temperature = 8,
        tint = 0
    )

    /** Dramatis: kontras kuat dengan suasana lebih dramatis. */
    val DRAMATIC = Preset(
        id = "dramatic",
        name = "Dramatis",
        brightness = -8,
        contrast = 30,
        saturation = 8,
        temperature = -8,
        tint = 4
    )

    /** Daftar preset yang ditampilkan (urutan tampilan di panel). Satu sumber kebenaran. */
    val ALL = listOf(
        ORIGINAL, WARM, COOL, VIVID, FILM, BNW,
        BRIGHT, MOODY, VINTAGE, TEAL_ORANGE, SOFT, DRAMATIC
    )

    /** Cari preset by id, atau null bila tidak ditemukan. */
    fun byId(id: String): Preset? = ALL.firstOrNull { it.id == id }
}
