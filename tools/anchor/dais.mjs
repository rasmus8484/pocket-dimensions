// The Stone Dais under the World Core (RL-024, docs/superpowers/specs/2026-10-11-core-landing-dais-design.md):
// aurora stone's two animated overlays (drawn over vanilla stone), its veins and their stained rim. The veins dance in
// teal with drifting swatches of pink, blue and purple, like a real aurora.
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

const TAU = Math.PI * 2, clamp = v => Math.max(0, Math.min(255, Math.round(v)));
export const TEAL = [[20, 190, 160], [90, 255, 215]];                       // deep and bright teal: the aurora's body
const ACCENTS = [                                                           // swatches drifting through it
  { c: [255, 110, 210], k: [1, 2, 3], ph: 0.0 },                           // pink
  { c: [80, 150, 255], k: [2, -1, -2], ph: 0.37 },                         // blue
  { c: [185, 110, 255], k: [-1, 1, 4], ph: 0.71 },                         // purple
];
const smoothstep = (a, b, v) => { const t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); };

/**
 * How strongly each accent (pink, blue, purple) shows at pixel (x, y), t seconds in: a wave travelling across the
 * block (whole cycles per 16 px, so blocks tile, and per loop, so the strip loops), only its crest showing, and each
 * colour coming and going over the loop. Mostly 0: the aurora is mainly teal.
 */
export function accents(x, y, t) {
  return ACCENTS.map(({ k, ph }) => {
    const wave = 0.5 + 0.5 * Math.sin(TAU * (k[0] * x / 16 + k[1] * y / 16 + k[2] * t / LOOP_SECONDS + ph));
    const gate = 0.5 + 0.5 * Math.sin(TAU * (t / LOOP_SECONDS + ph * 2));
    return smoothstep(0.82, 0.99, wave) * gate * gate;
  });
}

/** The aurora's colour at pixel (x, y), `along` its vein, t seconds in: dancing teal with swatches of pink, blue, purple. */
export function auroraAt(x, y, along, t) {
  const shimmer = 0.5 + 0.5 * Math.sin(TAU * (along * 2 + x / 16 - 3 * t / LOOP_SECONDS));   // teal rippling deep to bright
  let c = TEAL[0].map((v, i) => v + (TEAL[1][i] - v) * shimmer);
  accents(x, y, t).forEach((w, i) => { c = c.map((v, ch) => v + (ACCENTS[i].c[ch] - v) * w); });
  return c;
}

/** The pulse's brightness 0..1 at a vein pixel `along` its vein, t seconds in: one pulse every 2 s, two per 16 px. */
export const pulse = (along, t) => Math.pow(0.5 + 0.5 * Math.sin(TAU * (along * 2 - t * 0.5)), 2);

/** One 16 x 16 frame of a layer ('veins' or 'rim') at t seconds: that layer's pixels coloured, the rest transparent. */
export function frame(layer, t) {
  const rgba = new Uint8Array(16 * 16 * 4), want = layer === 'veins' ? 1 : 0.5;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const i = y * 16 + x;
    if (VEINS.mask[i] !== want) continue;
    const a = VEINS.along[i], p = pulse(a, t), au = auroraAt(x, y, a, t);
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
