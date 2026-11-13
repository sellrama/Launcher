"""Membuat tema .zth kecil untuk uji CI (format HiOS/XOS: NamaTema/icons, wallpaper, preview)."""
import io, sys, zipfile
from PIL import Image, ImageDraw

def squircle(sz, color, text=None):
    im = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([6, 6, sz - 6, sz - 6], radius=int(sz * 0.24), fill=color)
    if text:
        d.text((sz * 0.38, sz * 0.36), text, fill=(255, 255, 255, 255))
    return im

def png(im):
    b = io.BytesIO(); im.save(b, "PNG"); return b.getvalue()

def jpg(im):
    b = io.BytesIO(); im.convert("RGB").save(b, "JPEG", quality=85); return b.getvalue()

def gradient(w, h, top, bot, circle=None):
    im = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] * (1 - t) + bot[i] * t) for i in range(3)))
    if circle:
        d.ellipse(circle, fill=(250, 200, 90))
    return im

root = "Uji Tema/"
files = {
    root + "icons/icon_mask.png": png(squircle(240, (255, 255, 255, 255))),
    root + "icons/icon_bg.png": png(Image.new("RGBA", (240, 240), (0, 0, 0, 0))),
    root + "icons/folder_bg.png": png(squircle(240, (230, 230, 240, 120))),
    root + "icons/Chrome.png": png(squircle(240, (255, 120, 40, 255), "C")),
    root + "icons/Settings.png": png(squircle(240, (90, 90, 100, 255), "S")),
    root + "icons/com_google_android_apps_photos_home_HomeActivity.png": png(squircle(240, (255, 70, 140, 255), "P")),
    root + "icons/YouTube.png": png(squircle(240, (230, 30, 30, 255), "Y")),
    root + "wallpaper/wallpaper.jpg": jpg(gradient(540, 1170, (20, 40, 110), (230, 120, 160))),
    root + "wallpaper/lockscreen_wallpaper.jpg": jpg(gradient(540, 1170, (10, 20, 60), (250, 150, 90), (170, 500, 370, 700))),
    root + "preview/preview_launcher_0.jpg": jpg(gradient(270, 585, (20, 40, 110), (230, 120, 160))),
}
with zipfile.ZipFile(sys.argv[1], "w", zipfile.ZIP_STORED) as z:
    for k, v in files.items():
        z.writestr(k, v)
print("ok", sys.argv[1])
