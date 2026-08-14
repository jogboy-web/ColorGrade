# ATURAN KESELAMATAN PROJECT (Safety Rules)

Dokumen ini berlaku untuk SELURUH proses pengembangan ColorGrade
dan harus diikuti oleh agent coding (Pi Agent) pada setiap tahap.

## Aturan TERLARANG — Jangan Pernah Menjalankan tanpa Persetujuan

1. **JANGAN menjalankan perintah hapus / `rm -rf` / `del` di LUAR folder
   project ColorGrade tanpa persetujuan eksplisit dari pengguna.**

   Termasuk, tetapi tidak terbatas pada:
   - Folder project lain (mis. CineAI, `scraping map`, dan lain-lain)
   - Folder di luar `d:\tugas 3 ibrohim\ColorGrade`
   - Folder sistem, folder kerja pengguna, atau folder temp lain.

2. **JANGAN menghapus project lama atau project milik pengguna.**

3. **JANGAN mengubah / menghapus project CineAI.**

4. Ketika ingin membersihkan file sementara (screenshot, dump, dsb.),
   HANYA hapus file yang jelas dibuat oleh proses ColorGrade dan berada
   DI DALAM folder project ColorGrade.

5. Sebelum menjalankan perintah `rm -rf`, `rm`, `del`, `rmdir`, atau sejenisnya:
   - Periksa dulu path-nya.
   - Pastikan path berada tepat di dalam folder project ColorGrade.
   - Pastikan memang file yang boleh dihapus.
   - Jika ragu -> JANGAN dijalankan, tanyakan dulu ke pengguna.

## Konteks Singkat

Pada Tahap 2, folder `scraping map` di `d:\tugas 3 ibrohim\`
terhapus secara tidak sengaja saat membersihkan file temp. Hal ini
adalah pelajaran nyata: agent wajib menjaga batas folder project.

## Ringkasan
- Proses hanya boleh memengaruhi folder `d:\tugas 3 ibrohim\ColorGrade`.
- Operasi penulisan file boleh di dalam project.
- Operasi baca/lihat boleh di mana saja.
- Operasi hapus HANYA di dalam project ColorGrade (dan hanya file temp yang relevan).
