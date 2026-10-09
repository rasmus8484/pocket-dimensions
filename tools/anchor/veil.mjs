// The Veil: the ward's wall around every realm. A full-height curtain of drifting mist with a few faint rune glints,
// coloured by height band: dark indigo underground, pale mist at ground level, the realm's fog and then its sky colour
// at the top, so from far away the wall melts into the horizon haze.
import { hash3 } from './runebound.mjs';

export const FRAMES = 16;
// bottom (y -64) to top (y 319); 5 is the realm's fog colour #c0d8ff, 7 its sky colour #78a7ff (realm_type.json)
export const BAND_COLOURS = [
  [30, 26, 64], [86, 92, 146], [172, 188, 222], [182, 200, 236],
  [188, 210, 248], [192, 216, 255], [156, 192, 255], [120, 167, 255],
];
const GLINT = [235, 250, 255];

// smooth value noise on a 4 x 4 lattice that wraps every 16 pixels, so the texture tiles and the loop is seamless
const lattice = (i, j, seed) => hash3(((i % 4) + 4) % 4, ((j % 4) + 4) % 4, seed);
const smooth = t => t * t * (3 - 2 * t);
function noise(x, y, seed) {
  const gx = x / 4, gy = y / 4, i = Math.floor(gx), j = Math.floor(gy), fx = smooth(gx - i), fy = smooth(gy - j);
  const a = lattice(i, j, seed), b = lattice(i + 1, j, seed), c = lattice(i, j + 1, seed), d = lattice(i + 1, j + 1, seed);
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}

/** Brightness factor of pixel (x, y) in frame f: two mist layers drifting opposite ways, one full period per loop. */
function mist(x, y, f) {
  const s = (f / FRAMES) * 16;
  return 0.78 + 0.16 * noise(x + s, y, 31) + 0.10 * noise(x - s, y + 5, 57) - 0.02;
}

/** The band's texture: FRAMES frames of 16 x 16, stacked top to bottom (Minecraft's animated texture layout). */
export function veilTexture(band) {
  const base = BAND_COLOURS[band], rgba = new Uint8Array(16 * 16 * FRAMES * 4);
  for (let f = 0; f < FRAMES; f++) for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const k = mist(x, y, f);
    let c = base.map(v => Math.max(0, Math.min(255, Math.round(v * k))));
    // a faint rune glint drifts with the first mist layer
    const gx = ((x + Math.round((f / FRAMES) * 16)) % 16 + 16) % 16;
    if (hash3(gx, y, band + 90) > 0.985) c = c.map((v, i) => Math.round(v + (GLINT[i] - v) * 0.35));
    rgba.set([...c, 255], ((f * 16 + y) * 16 + x) * 4);
  }
  return rgba;
}
