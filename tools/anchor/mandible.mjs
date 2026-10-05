// Mandible: the approved World Breacher design (see design/world-anchor-concept.html, round 12 · III).
// Coordinates share the anchor's frame: x/z 0..15, world y; the breacher block itself is y 32..47,
// its mandibles reach down to y 20. Anchor voxels are never overlapped.
import { hash3, GLYPHS, shape as anchorShape } from './runebound.mjs';

export const Y0 = 20, Y1 = 48;
export const MAG = [255, 90, 220];
const LAPIS = [80, 130, 255];
const RIVET = [176, 170, 186];

const shade = (c, k) => c.map(v => Math.max(0, Math.min(255, v * k)));
const tone = (c, x, y, z, amt = 0.12) => shade(c, 1 - amt / 2 + hash3(x, y, z) * amt);
const goldTone = (x, y, z) => (hash3(x + 11, y, z + 5) > 0.93 ? [250, 226, 150] : tone([214, 162, 60], x, y, z, 0.14));
const ironC = (x, y, z) => { let c = tone([62, 58, 74], x, y, z, 0.14); if (y % 4 === 0) c = shade(c, 0.86); return c; };
const ironD = (x, y, z) => { let c = tone([42, 38, 52], x, y, z, 0.14); if (y % 4 === 0) c = shade(c, 0.86); return c; };
const LX = v => Math.min(v, 15 - v);
const C = (x, z) => ({ cx: x + 0.5 - 8, cz: z + 0.5 - 8 });
const oct = (cx, cz, h, cut) => { const ax = Math.abs(cx), az = Math.abs(cz); return ax < h && az < h && ax + az < 2 * h - cut; };
const anySide = e => e.nx || e.px || e.nz || e.pz;
const CORNERS = [[1, 1], [1, -1], [-1, 1], [-1, -1]];
const faceU = (x, z, lx, lz) => (lz <= lx ? x : z);
function sideFace(e, x, z) {
  if (e.nz) return { f: 0, u: x };
  if (e.pz) return { f: 1, u: 15 - x };
  if (e.nx) return { f: 2, u: 15 - z };
  if (e.px) return { f: 3, u: z };
  return null;
}
function segInfo(p, a, b) {
  const ab = [b[0] - a[0], b[1] - a[1], b[2] - a[2]];
  const L2 = ab[0] ** 2 + ab[1] ** 2 + ab[2] ** 2;
  const t = ((p[0] - a[0]) * ab[0] + (p[1] - a[1]) * ab[1] + (p[2] - a[2]) * ab[2]) / L2;
  const tc = Math.max(0, Math.min(1, t));
  return { t, d: Math.hypot(a[0] + ab[0] * tc - p[0], a[1] + ab[1] * tc - p[1], a[2] + ab[2] * tc - p[2]) };
}
function polyDist(p, pts) {
  let best = { d: 1e9, seg: 0, t: 0 };
  for (let i = 0; i < pts.length - 1; i++) { const s = segInfo(p, pts[i], pts[i + 1]); if (s.d < best.d) best = { d: s.d, seg: i, t: s.t }; }
  return best;
}
const glyphAt = (u, v, set) => u >= 0 && u <= 3 && v >= 0 && v <= 4 && GLYPHS[set % 6][4 - v][u] === 'X';

/**
 * How far the breacher's magenta has spread into the anchor's runes at height y (0..1).
 * level 0 = no breacher, 1..3 = breaching (front moves down with progress), 4 = breach complete.
 */
export function influenceAmount(y, level) {
  if (level <= 0) return 0;
  if (level >= 4) return 1;
  const front = 30 - 30 * level / 4;
  const f = Math.min(1, Math.max(0, (y + 0.5 - (front - 10)) / 10));
  return f * f * (3 - 2 * f);
}

function rawShape(x, y, z) {
  const lx = LX(x), lz = LX(z), { cx, cz } = C(x, z), ax = Math.abs(cx), az = Math.abs(cz), p = [x + 0.5, y + 0.5, z + 0.5];
  for (const [sx, sz] of CORNERS) {
    const pd = polyDist(p, [[8 + sx * 4.6, 33.5, 8 + sz * 4.6], [8 + sx * 7, 31, 8 + sz * 7], [8 + sx * 7, 25, 8 + sz * 7], [8 + sx * 6, 22.5, 8 + sz * 6]]);
    if (pd.d < 1.15) return pd.seg === 2 && pd.t > 0.5 ? 'hook' : 'mandible';
  }
  if (y >= 31 && y <= 32) return oct(cx, cz, 4.5, 1.5) ? 'jaw' : null;
  if (y >= 33 && y <= 38) return oct(cx, cz, 5.5, 2.5) ? 'head' : null;
  if (y === 39) return oct(cx, cz, 5, 2) ? 'brow' : null;
  if (y === 40) return oct(cx, cz, 4, 1.5) ? 'brow2' : null;
  if (y >= 41 && y <= 43) {
    if (ax < 1.5 && az < 1.5) return 'eye';
    return y === 41 && ax < 2.5 && az < 2.5 ? 'lid' : null;
  }
  if (y >= 28 && y <= 30) {
    const u = faceU(x, z, lx, lz), d = Math.min(lx, lz);
    if (d === 3 && (u === 6 || u === 9)) return y === 28 ? 'fangTip' : 'fang';
  }
  return null;
}

/** Breacher voxel kind, or null. Never overlaps the linked anchor's voxels. */
export function shape(x, y, z) {
  if (x < 0 || x > 15 || z < 0 || z > 15 || y < Y0 || y >= Y1) return null;
  const k = rawShape(x, y, z);
  if (k === null || anchorShape(x, y, z, true) !== null) return null;
  return k;
}

/** complete = breach finished (eye lit). */
export function paint(k, x, y, z, complete, e) {
  if (k === 'eye') return complete ? { c: hash3(x, y, z) > 0.65 ? [255, 232, 250] : [255, 110, 222], g: true } : { c: [110, 44, 100] };
  if (k === 'brow' || k === 'brow2' || k === 'lid' || k === 'fangTip') return { c: goldTone(x, y, z) };
  if (k === 'fang') return { c: [210, 204, 220] };
  if (k === 'hook') return { c: MAG, g: true };
  if (k === 'jaw') return { c: ironD(x, y, z) };
  if (k === 'mandible') return { c: anySide(e) && hash3(x, y, z) > 0.8 ? RIVET : ironC(x, y, z) };
  const sf = sideFace(e, x, z);
  if (sf && y >= 35 && y <= 37) {
    if (sf.u >= 6 && sf.u <= 9) return { c: (y + sf.u) % 3 === 0 ? [170, 200, 255] : LAPIS, g: true };
    if (glyphAt(sf.u - 1, y - 34, sf.f) || glyphAt(sf.u - 11, y - 34, sf.f + 1)) return { c: MAG, fl: true };
  }
  if (y === 33) return { c: ironD(x, y, z) };
  return { c: ironC(x, y, z) };
}
