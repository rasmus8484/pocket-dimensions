// Anchor damage while an Anchor Breaker (the Unmaker) works on it: cracks that appear and grow with
// progress from every direction, runes heating from their own colour toward red, and an unmaking
// sigil of red glyphs around the plinth. level 0 = no breaker, 1..4 = progress quarters.
import { shape as anchorShape, paint as anchorPaint, hash3, GLYPHS } from './runebound.mjs';

const HOT = [255, 80, 40], EMBER = [255, 170, 60], IRON_D = [42, 38, 52];
const LX = v => Math.min(v, 15 - v);
const mixC = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
export const damageProgress = level => level * 0.25;

/** Crack lines per face: [{ f, u, y, birth }], birth = progress (0..1) at which that pixel appears. */
export const CRACK_LINES = (() => {
  const lines = [];
  let seed = 91;
  const rnd = () => { seed = (Math.imul(seed, 1103515245) + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
  const starts = [[2, 21], [13, 21], [2, 6], [13, 6], [7, 22], [8, 5], [3, 13], [12, 14], [5, 19], [10, 8]];
  const D = [[1, 0], [-1, 0], [0, 1], [0, -1]], STEPS = 9;
  for (let f = 0; f < 4; f++) starts.forEach(([u0, y0], si) => {
    const start = 0.05 + ((si * 7 + f * 3) % 10) / 10 * 0.45;     // cracks appear between 5 % and 50 %
    const line = [];
    let u = u0, y = y0, dir = Math.floor(rnd() * 4);
    for (let i = 0; i < STEPS; i++) {
      const birth = start + (i / STEPS) * (0.95 - start);
      line.push({ f, u, y, birth });
      if (rnd() < 0.35) dir = Math.floor(rnd() * 4);
      u = Math.max(2, Math.min(13, u + D[dir][0]));
      y = Math.max(5, Math.min(22, y + D[dir][1]));
      if (rnd() < 0.2) line.push({ f, u: Math.min(13, u + 1), y, birth });
    }
    lines.push(line);
  });
  return lines;
})();
const BIRTH = new Map();
for (const line of CRACK_LINES) for (const c of line) {
  const k = c.f + '|' + c.u + '|' + c.y;
  if (!BIRTH.has(k) || BIRTH.get(k) > c.birth) BIRTH.set(k, c.birth);
}

function sideFace(e, x, z) {
  if (e.nz) return { f: 0, u: x };
  if (e.pz) return { f: 1, u: 15 - x };
  if (e.nx) return { f: 2, u: 15 - z };
  if (e.px) return { f: 3, u: z };
  return null;
}

/** Linked anchor shape plus, once damaged, the sigil ring around the plinth (outside the flare). */
export const damageShape = level => (x, y, z, L) => {
  if (level > 0 && L && y === 4 && x >= 0 && x <= 15 && z >= 0 && z <= 15 && (LX(x) === 0 || LX(z) === 0)) return 'sigil';
  return anchorShape(x, y, z, L);
};

export const damagePaint = level => (k, x, y, z, L, e) => {
  if (level === 0) return anchorPaint(k, x, y, z, L, e);
  const p = damageProgress(level);
  if (k === 'sigil') {
    const u = LX(x) === 0 ? z : x;
    const lit = (u % 5 === 4) || GLYPHS[Math.floor(u / 5) % 6][3][u % 5] === 'X';
    return lit ? { c: p > 0.6 ? [255, 150, 90] : HOT, g: true } : { c: IRON_D };
  }
  if (k === 'body') {
    const sf = sideFace(e, x, z);
    const birth = sf && BIRTH.get(sf.f + '|' + (sf.f < 2 ? x : z) + '|' + y);
    // solid (not glow) so cracked stone still merges into the same boxes as plain stone
    if (birth !== undefined && birth <= p) return { c: p > 0.6 && hash3(x, y, z) > 0.6 ? EMBER : HOT };
  }
  const r = anchorPaint(k, x, y, z, L, e);
  if (r.g || r.fl) {
    const lum = Math.max(...r.c) / 255;
    return { ...r, c: mixC(r.c, HOT.map(v => v * lum), p) };
  }
  return r;
};
