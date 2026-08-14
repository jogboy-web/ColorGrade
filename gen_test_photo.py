#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Buat foto uji berwarna untuk manual test ColorGrade Tahap 6."""
from PIL import Image, ImageDraw

W, H = 1200, 1600  # resolusi cukup tinggi untuk tes RAM
img = Image.new("RGB", (W, H))
d = ImageDraw.Draw(img)

# Background gradasi langit (biru atas -> oranye bawah ala sunset)
for y in range(H):
    t = y / H
    r = int(30 + t * 200)
    g = int(60 + t * 130)
    b = int(220 - t * 190)
    d.line([(0, y), (W, y)], fill=(r, g, b))

# Blok merah pekat
d.rectangle([80, 120, 420, 420], fill=(230, 40, 40))
# Blok hijau
d.rectangle([480, 120, 820, 420], fill=(40, 200, 60))
# Blok biru
d.rectangle([880, 120, 1120, 420], fill=(40, 80, 230))

# Warna kulit (skin tone)
d.rectangle([80, 480, 520, 720], fill=(225, 172, 132))
# Area gelap (detail rendah)
d.rectangle([560, 480, 1120, 720], fill=(40, 45, 50))
# Area terang (highlight)
d.rectangle([80, 780, 520, 1080], fill=(245, 245, 230))
# Gradasi abu-abu (grayscale tetap abu-abu saat saturation dinaikkan)
for i in range(16):
    v = 20 + i * 14
    d.rectangle([560 + i * 35, 780, 560 + (i + 1) * 35, 1080], fill=(v, v, v))

# Lingkaran + detail warna kecil di tengah bawah
d.ellipse([400, 1150, 800, 1550], fill=(200, 120, 220))
d.ellipse([520, 1270, 680, 1430], fill=(250, 200, 60))
d.ellipse([560, 1310, 640, 1390], fill=(30, 170, 220))

# Detail halus (kotak kecil) untuk cek ketajaman
for i in range(5):
    for j in range(5):
        c = ((i * 50) % 256, (j * 50) % 256, (i * j * 40) % 256)
        d.rectangle([60 + i * 12, 1560 + j * 12, 68 + i * 12, 1568 + j * 12], fill=c)

img.save("test_photo.png")
print("saved test_photo.png", img.size)
