// The Veil: the ward's wall around every realm, now a plain backdrop behind the Veil fog (client/veil): one still frame,
// no animation, no glints, and one deep violet all the way up, darker than the smoke, so wherever the smoke thins the
// wall reads as deeper smoke. Lit like ordinary blocks (no glow), so it darkens with the smoke at night. The block keeps
// its eight height bands (the blockstate and world generation use them), but every band gets the same texture.
import { hash3 } from './runebound.mjs';

export const FRAMES = 1;
// a deep violet, darker than the smoke's shadowed colour by day, #7672b0
export const COLOUR = [118, 114, 176];

// smooth value noise on a 4 x 4 lattice that wraps every 16 pixels, so the texture tiles and the loop is seamless
const lattice = (i, j, seed) => hash3(((i % 4) + 4) % 4, ((j % 4) + 4) % 4, seed);
const smooth = t => t * t * (3 - 2 * t);
function noise(x, y, seed) {
  const gx = x / 4, gy = y / 4, i = Math.floor(gx), j = Math.floor(gy), fx = smooth(gx - i), fy = smooth(gy - j);
  const a = lattice(i, j, seed), b = lattice(i + 1, j, seed), c = lattice(i, j + 1, seed), d = lattice(i + 1, j + 1, seed);
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}

/** A band's texture (the same for every band): one still 16 x 16 frame, the colour with a faint, tileable variation. */
export function veilTexture(band) {
  const base = COLOUR, rgba = new Uint8Array(16 * 16 * 4);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const k = 0.97 + 0.06 * (noise(x, y, 31) - 0.5);
    const c = base.map(v => Math.max(0, Math.min(255, Math.round(v * k))));
    rgba.set([...c, 255], (y * 16 + x) * 4);
  }
  return rgba;
}
