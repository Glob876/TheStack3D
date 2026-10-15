"""Generate matching Android vector/adaptive and legacy icons. Requires rsvg-convert."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
res = root / "android/src/main/res"
background = "#0D1424"
faces = []
# Back to front: four simple slabs, tapering slightly toward the top.
for y, half_width, top, left, right in [
    (60, 22, "#4CB4C4", "#256A88", "#328AA4"),
    (50, 21, "#64D2C6", "#2C899A", "#3DAAAE"),
    (40, 20, "#8AE4CB", "#3AA49E", "#55C3AF"),
    (30, 19, "#BEF7D9", "#63C6AC", "#8BE0BF"),
]:
    x0, x1, middle, bottom = 54 - half_width, 54 + half_width, y + 8, y + 16
    faces.extend([
        (left, f"M{x0},{middle} L54,{bottom} L54,{bottom + 6} L{x0},{middle + 6} Z"),
        (right, f"M54,{bottom} L{x1},{middle} L{x1},{middle + 6} L54,{bottom + 6} Z"),
        (top, f"M54,{y} L{x1},{middle} L54,{bottom} L{x0},{middle} Z"),
    ])

def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text + "\n", encoding="utf-8")

paths = "\n".join(f'<path fill="{color}" d="{data}"/>' for color, data in faces)
svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
       f'<rect width="108" height="108" fill="{background}"/>'
       f'<g transform="rotate(-8 54 54)">{paths}</g></svg>')
write(root / "artwork/android-icon.svg", svg)
vector_paths = "\n".join(f'        <path android:fillColor="{color}" android:pathData="{data}" />' for color, data in faces)
write(res / "drawable/ic_launcher_foreground.xml", f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:rotation="-8" android:pivotX="54" android:pivotY="54">
{vector_paths}
    </group>
</vector>''')
write(res / "values/icon_colors.xml", f'<resources><color name="ic_launcher_background">{background}</color></resources>')
adaptive = '''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>'''
for name in ("ic_launcher", "ic_launcher_round"):
    write(res / f"mipmap-anydpi-v26/{name}.xml", adaptive)
    write(res / f"mipmap-anydpi-v33/{name}.xml", adaptive.replace(
        "</adaptive-icon>", '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n</adaptive-icon>'))

# Legacy launchers need raster icons; rounded and square versions share the artwork.
round_svg = svg.replace('<rect width="108" height="108"', '<rect rx="54" width="108" height="108"')
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    folder = res / f"mipmap-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    for name, content in [("ic_launcher", svg), ("ic_launcher_round", round_svg)]:
        subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), "-o", str(folder / f"{name}.png")],
                       input=content.encode(), check=True)
subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", "-o", str(root / "artwork/android-icon.png")],
               input=svg.encode(), check=True)
