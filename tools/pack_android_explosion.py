"""Pack the original explosion for Android. Run from any directory; requires Pillow."""
from pathlib import Path
import shutil

from PIL import Image


root = Path(__file__).resolve().parents[1]
source = root / "src/main/resources"
target = root / "android/src/main/assets/effects"
target.mkdir(parents=True, exist_ok=True)

with Image.open(source / "explode.gif") as animation:
    # TowerGame uses 17 frames at 100 ms, laid out in five columns.
    assert animation.size == (142, 200) and animation.n_frames == 17
    atlas = Image.new("RGBA", (710, 800))
    for index in range(animation.n_frames):
        animation.seek(index)
        assert animation.info.get("duration") == 100
        atlas.paste(animation.convert("RGBA"), (index % 5 * 142, index // 5 * 200))
    atlas.save(target / "explosion.png", optimize=True)

shutil.copyfile(source / "explode.wav", target / "explosion.wav")
