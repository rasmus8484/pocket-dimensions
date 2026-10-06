// World Core screen textures: the screen is a slab of the Geode Heart's own weathered rock (see
// design/world-core-gui-mock.html). Everything else on it (carvings, runes, seals, the ward hollow) is drawn by
// WorldCoreScreen; these are only the stone surfaces.
import { hash3 } from './runebound.mjs';

export const SLAB_W = 252, SLAB_H = 270;       // must match WorldCoreMenu.GUI_W / GUI_H
const ROCK = [122, 117, 130], LICHEN = [104, 120, 88];

const img = (w, h) => ({ w, h, rgba: new Uint8Array(w * h * 4) });
const put = (im, x, y, c, a = 255) => im.rgba.set([...c.map(v => Math.max(0, Math.min(255, Math.round(v)))), a], (y * im.w + x) * 4);

/** Weathered rock at (x, y): grain, blotches, lichen speckles (seed picks a different cut of stone). */
function rock(x, y, base, seed, lichen = true) {
  const h = hash3(x, y, seed), b = hash3(x >> 2, y >> 2, seed + 1), m = hash3(x >> 3, y >> 3, seed + 2);
  let c = base.map(v => v * (0.86 + h * 0.14) * (b > 0.8 ? 0.88 : 1) * (0.94 + m * 0.1));
  if (lichen && hash3(x >> 1, y >> 1, seed + 3) > 0.93) c = LICHEN.map(v => v * (0.85 + h * 0.25));
  return c;
}

/** Hairline cracks: short wandering dark lines. */
function cracks(im, n, seed, color = [30, 28, 36], k = 0.45) {
  for (let i = 0; i < n; i++) {
    let x = Math.floor(hash3(i, 1, seed) * im.w), y = Math.floor(hash3(i, 2, seed) * im.h);
    const len = 6 + Math.floor(hash3(i, 3, seed) * 16);
    for (let s = 0; s < len && y < im.h; s++) {
      const j = (y * im.w + x) * 4;
      if (x >= 0 && x < im.w && im.rgba[j + 3]) for (let c = 0; c < 3; c++) im.rgba[j + c] = im.rgba[j + c] * (1 - k) + color[c] * k;
      y++; const r = hash3(i, s, seed + 9); x += r > 0.66 ? 1 : r < 0.33 ? -1 : 0;
    }
  }
}

/** Broken outline: how far the edge is bitten in at position i along a side (pixel steps of 3). */
const bite = (i, side, amp, seed) => Math.floor(hash3(Math.floor(i / 3), side, seed) * amp);

function outlined(w, h, amp, seed) {
  return (x, y) => x >= bite(y, 4, amp, seed) && x < w - bite(y, 2, amp, seed) && y >= bite(x, 1, amp, seed) && y < h - bite(x, 3, amp, seed);
}

/** The screen itself: weathered rock lit from the top left, lichen on its crown, cracks, a broken edge. */
export function slab() {
  const im = img(SLAB_W, SLAB_H), inside = outlined(SLAB_W, SLAB_H, 6, 11);
  for (let y = 0; y < SLAB_H; y++) for (let x = 0; x < SLAB_W; x++) {
    if (!inside(x, y)) continue;
    let c = rock(x, y, ROCK, 5);
    const light = 1.08 - 0.22 * ((x / SLAB_W) * 0.5 + (y / SLAB_H) * 0.5);     // lit from the top left
    c = c.map(v => v * light);
    if (y < 12 && hash3(x, y, 13) < (12 - y) / 16) c = LICHEN.map(v => v * (0.85 + hash3(x, y, 14) * 0.3));   // lichen on the crown
    const edge = !inside(x - 1, y) || !inside(x, y - 1) ? 1.18 : !inside(x + 1, y) || !inside(x, y + 1) ? 0.62 : 1;   // a lit rim and a shadowed one
    put(im, x, y, c.map(v => v * edge));
  }
  cracks(im, 22, 7);
  return im;
}

/** A polished face: the rock flowed smooth and flat here (like the tablets under the core's openings). Tiles. */
export function polished() {
  const im = img(64, 64);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    const k = 0.97 + hash3(x >> 2, y >> 2, 41) * 0.04 + hash3(x, y, 42) * 0.02;
    put(im, x, y, [150, 146, 158].map(v => v * k));
  }
  return im;
}

/** A darker cut of the rock for hollows (the online-players picker). Tiles. */
export function hollow() {
  const im = img(64, 64);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) put(im, x, y, rock(x, y, [82, 78, 90], 15));
  return im;
}

/** The relocate warning: a cracked stone tablet, its cracks glowing red. */
export const TABLET_W = 204, TABLET_H = 126;
export function tablet() {
  const im = img(TABLET_W, TABLET_H), inside = outlined(TABLET_W, TABLET_H, 3, 23);
  for (let y = 0; y < TABLET_H; y++) for (let x = 0; x < TABLET_W; x++) {
    if (!inside(x, y)) continue;
    const edge = !inside(x - 1, y) || !inside(x, y - 1) ? 1.15 : !inside(x + 1, y) || !inside(x, y + 1) ? 0.6 : 1;
    put(im, x, y, rock(x, y, [118, 112, 124], 31).map(v => v * edge));
  }
  for (let k = 0; k < 3; k++) {                    // three cracks splitting it top to bottom, burning red
    let x = Math.floor(TABLET_W * (0.2 + 0.3 * k));
    for (let y = 0; y < TABLET_H; y++) {
      if (inside(x, y)) put(im, x, y, [255, 96, 74]);
      if (inside(x + 1, y)) put(im, x + 1, y, [40, 14, 12]);
      const r = hash3(k, y, 31); x += r > 0.7 ? 1 : r < 0.3 ? -1 : 0;
    }
  }
  return im;
}
