// The ward sigil's lines and beam: a soft white stroke, tinted by the renderer with the siege colour.

/** 16 x 16 RGBA: white, its alpha a soft band across the rows (brightest on rows 7 and 8). */
export function lineTexture() {
  const rgba = new Uint8Array(16 * 16 * 4);
  for (let y = 0; y < 16; y++) {
    const d = Math.abs(y - 7.5) / 7.5;                         // 0 at the middle, 1 at the edges
    const a = Math.round(255 * Math.max(0, 1 - d) ** 2.2);
    for (let x = 0; x < 16; x++) rgba.set([255, 255, 255, a], (y * 16 + x) * 4);
  }
  return rgba;
}
