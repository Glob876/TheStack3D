"""Create a small, Cyrillic bitmap font with a subtle pixel grid (requires Pillow)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
target = root / "android/src/main/assets/fonts"
target.mkdir(parents=True, exist_ok=True)
font = ImageFont.truetype(str(root / "tools/font-source/LiberationSans-Regular.ttf"), 16)
ascent, descent = font.getmetrics()
characters = [chr(i) for i in range(32, 127)] + [chr(i) for i in range(0x400, 0x460)]
characters += list("—–…×«»")
atlas = Image.new("RGBA", (512, 256))
draw = ImageDraw.Draw(atlas)
entries = []
x, y, row_height = 1, 1, 0
for char in characters:
    left, top, right, bottom = font.getbbox(char, anchor="ls")
    width, height = right - left, bottom - top
    if x + width + 1 > atlas.width:
        x, y, row_height = 1, y + row_height + 2, 0
    assert y + height < atlas.height
    draw.text((x - left, y - top), char, font=font, fill="white", anchor="ls")
    advance = round(font.getlength(char))
    entries.append(f"char id={ord(char)} x={x} y={y} width={width} height={height} "
                   f"xoffset={left} yoffset={ascent + top} xadvance={advance} page=0 chnl=15")
    x += width + 2
    row_height = max(row_height, height)
atlas.save(target / "stack-sans.png", optimize=True)
header = [
    'info face="Stack Sans" size=16 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1',
    f"common lineHeight={ascent + descent + 1} base={ascent} scaleW=512 scaleH=256 pages=1 packed=0",
    'page id=0 file="stack-sans.png"', f"chars count={len(entries)}",
]
(target / "stack-sans.fnt").write_text("\n".join(header + entries) + "\n", encoding="utf-8")
