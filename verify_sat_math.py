#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Verifikasi matematika SaturationEngine terhadap pixel warna yang diketahui."""
import random


def compute_factor(value: int) -> float:
    t = value / 100.0
    strength = abs(t) ** 1.2
    if t < 0:
        return 1.0 - strength
    else:
        return 1.0 + strength * (2.4 - 1.0)


def apply_pixel(r, g, b, value):
    factor = compute_factor(value)
    luma = 0.299 * r + 0.587 * g + 0.114 * b
    nr = int(round(luma + (r - luma) * factor))
    ng = int(round(luma + (g - luma) * factor))
    nb = int(round(luma + (b - luma) * factor))
    return (max(0, min(255, nr)), max(0, min(255, ng)), max(0, min(255, nb)))


def hsv_sat(r, g, b):
    mx = max(r, g, b) / 255.0
    mn = min(r, g, b) / 255.0
    return 0.0 if mx == 0 else (mx - mn) / mx


# Warna-warna kuat yang diketahui
colors = {
    "red":     (230, 40, 40),
    "green":   (40, 200, 60),
    "blue":    (40, 80, 230),
    "skin":    (225, 172, 132),
    "cyan":    (30, 170, 220),
    "orange":  (250, 200, 60),
    "purple":  (200, 120, 220),
}

print("Pixel  | orig sat | sat@-100 | sat@+100 | kelakuan")
print("-" * 66)
allok = True
for name, c in colors.items():
    s0 = hsv_sat(*c)
    c_neg = apply_pixel(*c, -100)
    c_pos = apply_pixel(*c, 100)
    s_neg = hsv_sat(*c_neg)
    s_pos = hsv_sat(*c_pos)
    ok = (s_neg <= s0 + 1e-6) and (s_pos >= s0 - 1e-6)
    if not ok:
        allok = False
    print(f"{name:9} | {s0:.3f}   | {s_neg:.3f}    | {s_pos:.3f}    | {'OK' if ok else 'FAIL'}")

print("-" * 66)
print("Grayscale check (R=G=B harus tetap abu-abu):")
gray_ok = True
for v in [30, 90, 150, 210]:
    for val in [-100, 100]:
        out = apply_pixel(v, v, v, val)
        if not (out[0] == out[1] == out[2]):
            gray_ok = False
print("Semua pixel grayscale tetap grayscale pada -100 & +100:",
      "PASS" if gray_ok else "FAIL")

brightness_stable_ok = True
def luma(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b
for name, c in colors.items():
    for val in [-100, -50, 50, 100]:
        out = apply_pixel(*c, val)
        if abs(luma(*out) - luma(*c)) > 0.51:  # toleransi pembulatan 1 level
            brightness_stable_ok = False
print("Brightness (luma) dipertahankan oleh Saturation:",
      "PASS" if brightness_stable_ok else "FAIL")

print("\n", "SEMUA TES MESIN:", "PASS" if (allok and gray_ok and brightness_stable_ok) else "FAIL")
