// Geode Heart: the approved World Core (design/world-anchor-concept.html, round 18 "Aurora Spears").
// A tall weathered boulder floating above the ground, split open on four sides around a crystal-lined hollow,
// a crystal-lined shaft down its vertical axis, and a rune tablet under each opening. Frame: x/z 0..15, world y
// 0..31 (lower half 0..15, upper half 16..31). The black hole, beam, shards, aurora crystals and rune helix are
// drawn by the renderer; this file is only the static model.
import { hash3, GLYPHS } from './runebound.mjs';

/** Black hole centre (px), shared with the renderer. */
export const CORE = [8, 17.5, 8];
/** How far the boulder drops once inert (anchor lost). */
export const FALL = 4;

const shade = (c, k) => c.map(v => Math.max(0, Math.min(255, v * k)));
const tone = (c, x, y, z, amt = 0.12) => shade(c, 1 - amt / 2 + hash3(x, y, z) * amt);
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);

/** Siege state palettes (WorldCoreBlockEntity siege states). lost = inert: nothing glows. */
export const STATES = {
  normal: { lo: [60, 130, 255], hi: [150, 240, 255], rune: [140, 235, 255], bright: [200, 250, 255] },
  breaching: { lo: [200, 50, 180], hi: [255, 140, 235], rune: [255, 90, 220], bright: [255, 190, 245] },
  breaking: { lo: [255, 80, 40], hi: [255, 170, 60], rune: [255, 80, 40], bright: [255, 170, 60] },
  lost: { lo: [70, 80, 100], hi: [110, 125, 150], rune: [56, 54, 64], bright: [66, 64, 76], inert: true },
};

const inBlock = (x, z) => x >= 0 && x <= 15 && z >= 0 && z <= 15;
const C = (x, z) => ({ cx: x + 0.5 - 8, cz: z + 0.5 - 8 });

function sideFace(e, x, z) {
  if (e.nz) return { f: 0, u: x };
  if (e.pz) return { f: 1, u: 15 - x };
  if (e.nx) return { f: 2, u: 15 - z };
  if (e.px) return { f: 3, u: z };
  return null;
}

/**
 * The five faceted aurora crystals driven down through the crown, fallen with the boulder (same rasterisation as
 * WorldCoreBlockEntityRenderer.crystals). While the core lives the renderer draws them; once it is inert they are
 * dull and still, so they become part of the static model: key -> facet shade.
 */
const DEAD_CRYSTALS = (() => {
  const m = new Map();
  const full3 = [[0, 0, 1], [-1, 0, 0.82], [1, 0, 0.95], [0, -1, 0.88], [0, 1, 1.08]], full2 = [[0, 0, 1], [1, 0, 0.84], [0, 1, 1.06], [1, 1, 0.92]];
  for (let i = 0; i < 5; i++) {
    const a = i * 2 * Math.PI / 5 + 0.5, r = 3.0 + hash3(i, 1, 2) * 0.9, len = 7 + hash3(i, 2, 3) * 2.5, lean = (hash3(i, 5, 5) - 0.5) * 0.25;
    const base = [8 + Math.cos(a) * r, 35 - hash3(i, 4, 4) * 2.5, 8 + Math.sin(a) * r], d = [Math.cos(a) * lean, -1, Math.sin(a) * lean];
    const dl = Math.hypot(...d), thick = i < 2 ? 3 : 2, full = thick === 3 ? full3 : full2;
    for (let t = 0; t <= len; t += 0.35) {
      const q = [0, 1, 2].map(c => Math.floor(base[c] + d[c] / dl * t - (c === 1 ? FALL : 0)));
      const tip = t < 1.2 ? 0 : t < 2.4 ? 1 : 2;
      const sec = tip === 0 ? [[0, 0, 1.1]] : tip === 1 ? full.slice(0, thick === 3 ? 3 : 2) : full;
      for (const [ox, oz, sh] of sec) if (q[1] >= 0 && q[1] <= 35) m.set(`${q[0] + ox},${q[1]},${q[2] + oz}`, sh);
    }
  }
  return m;
})();

/** Voxel kind at world (x, y, z) for a siege state, or null. */
export function shape(x, y0, z, state) {
  if (!inBlock(x, z)) return null;
  const inert = STATES[state].inert, y = inert ? y0 + FALL : y0;   // once inert it has fallen the 4 px it used to float
  const { cx, cz } = C(x, z), ax = Math.abs(cx), az = Math.abs(cz), dy = y + 0.5 - CORE[1], rr = Math.hypot(cx, cz);
  if (inert && y0 === 0 && rr >= 6.2 && rr < 7.8 && hash3(x, 77, z) > 0.85) return 'deadShard';   // shards that fell with it
  if (inert && DEAD_CRYSTALS.has(`${x},${y0},${z}`)) return 'deadCrystal';                         // the crown crystals, dull and still
  if (y <= 3 || (y === 4 && hash3(x, y, z) > 0.6)) return null;                                  // floating, broken underside
  const ds = dy < 0 ? dy * 0.42 : dy * 0.62;
  const n = (hash3(x >> 1, y >> 1, z >> 1) - 0.5) * 1.2;
  const d = 0.25 * Math.max(ax, az, Math.abs(ds)) + 0.75 * Math.hypot(cx, cz, ds);
  if (d > 7.6 + n * 0.8) return null;
  if (y >= 5 && y <= 11) {   // rune tablets: below each opening the rock flowed smooth and flat
    const rag = y === 5 || y === 11 ? 3.6 + hash3(x + z, y, 1) * 0.8 : 5;
    if ((az < rag && ax >= 5.5) || (ax < rag && az >= 5.5)) return null;
    if ((az < rag && ax >= 4.5) || (ax < rag && az >= 4.5)) return 'tablet';
  }
  const r = Math.hypot(cx, cz, dy);
  if (r < 6.0) return null;                       // the hollow
  if (rr < 2.2) return null;                      // the shaft bored straight down through it
  if (rr < 3.2) return 'bore';
  const w = 2.8 + 1.6 * Math.max(0, 1 - Math.abs(dy) / 5.5) + (hash3(Math.round(dy), cx > 0 ? 1 : 2, cz > 0 ? 3 : 4) - 0.5) * 1.2;
  if (Math.abs(dy) < 5.5 && ((az < w && ax > 2.5) || (ax < w && az > 2.5))) return null;   // split open on four sides
  return r < 7.0 ? 'crystal' : r < 7.9 ? 'calcite' : 'rock';
}

/** Colour and class ({c, g?, fl?}) of an exposed voxel. */
export function paint(k, x, y0, z, state, e) {
  const P = STATES[state], inert = !!P.inert, y = inert ? y0 + FALL : y0;
  if (k === 'deadShard') return { c: mix(P.lo, P.hi, hash3(x, y, z)) };
  if (k === 'deadCrystal') { const h = hash3(x, y0, z), sh = DEAD_CRYSTALS.get(`${x},${y0},${z}`); return { c: [90 + 30 * h, 100 + 32 * h, 120 + 36 * h].map(v => Math.min(255, v * sh)) }; }
  if (k === 'crystal' || k === 'bore') return { c: mix(P.lo, P.hi, hash3(x, y, z)), g: !inert };
  if (k === 'calcite') return { c: tone([222, 224, 228], x, y, z, 0.08) };
  const sf = sideFace(e, x, z);
  if (!inert && sf && sf.u === 7 && y >= 6 && y <= 12) return { c: P.bright, fl: true };   // vein feeding the hollow (straight, so it merges into one element)
  if (k === 'tablet') {
    if (sf && y >= 6 && y <= 10) {
      const v = y - 6;
      if (sf.u >= 3 && sf.u <= 6 && GLYPHS[(sf.f * 2) % 6][4 - v][sf.u - 3] === 'X') return { c: P.rune, g: !inert };
      if (sf.u >= 9 && sf.u <= 12 && GLYPHS[(sf.f * 2 + 1) % 6][4 - v][sf.u - 9] === 'X') return { c: P.rune, g: !inert };
    }
    return { c: tone([128, 124, 138], x, y, z, 0.07) };
  }
  if (e.py && y > 22 && hash3(x, y, z) > 0.5) return { c: tone([104, 120, 88], x, y, z, 0.2) };   // lichen on the crown
  return { c: tone([112, 108, 120], x, y, z, 0.18) };
}
