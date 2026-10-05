// Pocket Anchor, the Tumbling Cube: the approved design (see design/world-anchor-concept.html, round 21).
// A 10 px cube centred in the block (x/y/z LO..HI). The renderer lifts it into the air and turns it, so this is only
// the cube itself. Depth comes from layers: proud corner knobs, edges set back between them, window frames a pixel
// deeper still, and a rune carved through the top and bottom plates with its glow at the bottom of the cut.
import { hash3, GLYPHS } from './runebound.mjs';

export const LO = 3, HI = 12;
export const TOP_GLYPH = 0, BOTTOM_GLYPH = 3;
const S = HI - LO;                                   // 9: last local index
const STONE = [66, 70, 88], RUNE = [140, 235, 255];

const shade = (c, k) => c.map(v => Math.max(0, Math.min(255, v * k)));
const tone = (c, x, y, z, amt = 0.12) => shade(c, 1 - amt / 2 + hash3(x, y, z) * amt);
const goldTone = (x, y, z) => (hash3(x + 11, y, z + 5) > 0.93 ? [250, 226, 150] : tone([214, 162, 60], x, y, z, 0.14));
const stone = (x, y, z) => tone(STONE, x, y, z, 0.14);

const ext = v => v === 0 || v === S;                 // outermost layer: only the knobs reach it
const sh = v => v === 1 || v === S - 1;              // the cube's skin
const inner = v => v === 2 || v === S - 2;           // one pixel in
const near = v => v <= 2 || v >= S - 2;              // inside a corner knob's span

/** Rune pixel at local (x, z) of the top (or bottom) plate. */
function glyphAt(x, z, top) {
  const u = x - 3, v = top ? 7 - z : z - 2;
  return u >= 0 && u < 4 && v >= 0 && v < 5 && GLYPHS[top ? TOP_GLYPH : BOTTOM_GLYPH][v][u] === 'X';
}

/** Voxel kind at block pixel (x, y, z), or null. */
export function shape(X, Y, Z) {
  const x = X - LO, y = Y - LO, z = Z - LO;
  if (x < 0 || x > S || y < 0 || y > S || z < 0 || z > S) return null;
  if (near(x) && near(y) && near(z)) return ext(x) + ext(y) + ext(z) === 3 ? null : 'knob';   // tip bevelled off
  if (ext(x) || ext(y) || ext(z)) return null;
  if (sh(x) + sh(y) + sh(z) >= 2) return 'edge';
  if (sh(y)) return glyphAt(x, z, y !== 1) ? null : 'cap';                                    // rune carved through
  if (sh(x) || sh(z)) return null;                                                             // open windows
  if (inner(y) && glyphAt(x, z, y !== 2)) return 'glyph';                                      // glow at the bottom of the cut
  if ((inner(x) || inner(z)) && y >= 2 && y <= S - 2) {                                        // window frame, set back
    const u = inner(x) ? z : x;
    if (u >= 2 && u <= S - 2 && (u === 2 || u === S - 2 || y === 2 || y === S - 2)) return 'ring';
  }
  return null;
}

export function paint(k, x, y, z) {
  if (k === 'knob') return { c: goldTone(x, y, z) };
  if (k === 'edge') return { c: shade(stone(x, y, z), 0.8) };
  if (k === 'glyph') return { c: RUNE, g: true };
  return { c: stone(x, y, z) };
}

/**
 * Soft glow behind a rune, as an 8x8 white sprite (glyph at column 2, row 1, like the rune particles): full under the
 * glyph, fading over a pixel or two around it. Drawn additively after everything else, so the runes glow a little.
 */
export function glowSprite(g) {
  const on = [];
  g.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === 'X') on.push([c + 2, r + 1]); }));
  const rgba = new Uint8Array(8 * 8 * 4);
  for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) {
    let a;
    if (on.some(([x, y]) => x === c && y === r)) a = 230;
    else a = Math.min(170, Math.round(255 * 0.45 * on.reduce((s, [x, y]) => s + Math.exp(-((x - c) ** 2 + (y - r) ** 2) / 1.1), 0)));
    if (a >= 8) rgba.set([255, 255, 255, a], (r * 8 + c) * 4);
  }
  return rgba;
}
