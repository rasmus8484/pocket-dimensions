// The Stone Dais under the World Core (RL-024, docs/superpowers/specs/2026-10-11-core-landing-dais-design.md):
// aurora stone's two animated overlays (drawn over vanilla stone), its veins and their stained rim.
// Ported from the approved studies page (core-landing-studies.html, version 18): the same hash and vein walk.

/** The studies page's integer hash, 0..1. */
export function hsh(i, j, k, s) {
  let n = Math.imul(i, 374761393) ^ Math.imul(j, 668265263) ^ Math.imul(k, 1274126177) ^ Math.imul(s, 1442695041);
  n = Math.imul(n ^ (n >>> 13), 1274126177); n ^= n >>> 16;
  return (n >>> 0) / 4294967295;
}

/** 128 frames, 5 ticks each (4 a second): 32 s, one turn of the aurora's colours and 16 pulses, so the strip loops. */
export const FRAMES = 128, FRAMETIME = 5, LOOP_SECONDS = FRAMES * FRAMETIME / 20;

/** Vanilla stone's average grey (its pixels run from 104 to 143): the rim's veins are mixed into it. */
export const STONE_GREY = [126, 126, 126];

const DIRS8 = [[1, 0], [1, 1], [0, 1], [-1, 1], [-1, 0], [-1, -1], [0, -1], [1, -1]];
const W = n => ((n % 16) + 16) % 16;

/**
 * The vein pattern (the studies page's tile 0): two 1-pixel random walks with a few branches, wrapping round the tile's
 * edges so neighbouring blocks join. mask: 1 = vein, 0.5 = the stained rim beside it, 0 = rock. along: 0..1, how far
 * along its vein a pixel lies (in 16ths, wrapping), which the pulses travel down.
 */
export const VEINS = (() => {
  const v = 0, mask = new Float32Array(256), dist = new Float32Array(256); let k = 0;
  const walk = (x, y, d, steps, from, depth) => {
    for (let i = 0; i < steps; i++) {
      x = W(x); y = W(y); mask[y * 16 + x] = 1; dist[y * 16 + x] = from + i; k++;
      const r = hsh(v, k, 1, 58);
      if (r < 0.3) d = (d + 1) & 7; else if (r < 0.6) d = (d + 7) & 7;              // wander, never doubling back
      if (depth < 2 && hsh(v, k, 2, 58) < 0.07)
        walk(x, y, (d + (hsh(v, k, 3, 58) < 0.5 ? 2 : 6)) & 7, 4 + Math.floor(hsh(v, k, 4, 58) * 6), from + i, depth + 1);
      x += DIRS8[d][0]; y += DIRS8[d][1];
    }
  };
  for (let j = 0; j < 2; j++)
    walk(Math.floor(hsh(v, j, 5, 58) * 16), Math.floor(hsh(v, j, 6, 58) * 16), Math.floor(hsh(v, j, 7, 58) * 8), 14 + Math.floor(hsh(v, j, 8, 58) * 7), 0, 0);
  const along = new Float32Array(256);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const i = y * 16 + x;
    if (!mask[i] && [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => mask[W(y + dy) * 16 + W(x + dx)] === 1) && hsh(x, y, v, 59) < 0.35) mask[i] = 0.5;
    along[i] = Math.round((dist[i] % 16) / 16 * 255) / 255;                        // as the studies page stored it
  }
  return { mask, along };
})();

const AURORA = [[60, 255, 170], [60, 200, 255], [170, 90, 255], [255, 110, 200]];   // green, cyan, violet, pink
/** The aurora's colour at s (one turn per 1.0), each colour easing into the next. */
export function aurora(s) {
  const f = (s - Math.floor(s)) * 4, i = Math.floor(f) % 4, t = f - Math.floor(f), e = t * t * (3 - 2 * t);
  return AURORA[i].map((a, c) => a + (AURORA[(i + 1) % 4][c] - a) * e);
}

const TAU = Math.PI * 2, clamp = v => Math.max(0, Math.min(255, Math.round(v)));
/** The pulse's brightness 0..1 at a vein pixel `along` its vein, t seconds in: one pulse every 2 s, two per 16 px. */
export const pulse = (along, t) => Math.pow(0.5 + 0.5 * Math.sin(TAU * (along * 2 - t * 0.5)), 2);

/** One 16 x 16 frame of a layer ('veins' or 'rim') at t seconds: that layer's pixels coloured, the rest transparent. */
export function frame(layer, t) {
  const rgba = new Uint8Array(16 * 16 * 4), want = layer === 'veins' ? 1 : 0.5;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const i = y * 16 + x;
    if (VEINS.mask[i] !== want) continue;
    const a = VEINS.along[i], p = pulse(a, t), au = aurora(t / LOOP_SECONDS + 0.08 * Math.sin(TAU * a));
    const c = layer === 'veins'
      ? au.map(v => v * (0.6 + 0.75 * p) + 255 * 0.05 * p)
      : STONE_GREY.map((r, ch) => r + (au[ch] * 0.35 - r) * (0.35 + 0.2 * p));
    rgba.set([...c.map(clamp), 255], i * 4);
  }
  return rgba;
}

/** A layer's animation strip: FRAMES frames stacked downward (16 x 16 * FRAMES). */
export function strip(layer) {
  const out = new Uint8Array(16 * 16 * 4 * FRAMES);
  for (let k = 0; k < FRAMES; k++) out.set(frame(layer, k * LOOP_SECONDS / FRAMES), k * 16 * 16 * 4);
  return out;
}
