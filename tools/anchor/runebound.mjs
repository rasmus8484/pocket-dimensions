// Runebound Monolith: the approved World Anchor design (see design/world-anchor-concept.html, round 7).
// Coordinates are pixels: x/z 0..15, y 0..31 across both block halves.
export const PAL = {
  stone: [66, 70, 88], plinth: [48, 50, 62], gold: [214, 162, 60], rune: [140, 235, 255],
  ring: [150, 240, 255], deep: [40, 110, 230], carved: [34, 36, 46], waiting: [120, 190, 230],
};
export const SC = 13.5, CR = 7.4;
export const GLYPHS = [
  ['XXXX', 'X...', 'XXX.', '...X', 'XXXX'], ['.XX.', 'X..X', '.XX.', '..X.', 'XXX.'], ['X.X.', 'XXXX', 'X.X.', '..X.', '..XX'],
  ['XXX.', '..X.', 'XXXX', '.X..', 'XX..'], ['X..X', 'X.X.', 'XX..', 'X.X.', 'X..X'], ['.X..', 'XXX.', '.X.X', '...X', '..XX'],
];

export function hash3(x, y, z) {
  let n = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(z | 0, 1274126177);
  n = Math.imul(n ^ (n >>> 13), 1274126177);
  return ((n ^ (n >>> 16)) >>> 0) / 4294967295;
}
const shade = (c, k) => c.map(v => Math.max(0, Math.min(255, v * k)));
const tone = (c, x, y, z, amt = 0.12) => shade(c, 1 - amt / 2 + hash3(x, y, z) * amt);
const goldTone = (x, y, z) => (hash3(x + 11, y, z + 5) > 0.93 ? [250, 226, 150] : tone(PAL.gold, x, y, z, 0.14));
const LX = v => Math.min(v, 15 - v);
const inBlock = (x, z) => x >= 0 && x <= 15 && z >= 0 && z <= 15;
const anySide = e => e.nx || e.px || e.nz || e.pz;
function sideFace(e, x, z) {
  if (e.nz) return { f: 0, u: x };
  if (e.pz) return { f: 1, u: 15 - x };
  if (e.nx) return { f: 2, u: 15 - z };
  if (e.px) return { f: 3, u: z };
  return null;
}
const stone = (x, y, z) => { let c = tone(PAL.stone, x, y, z, 0.14); if (y % 4 === 0) c = shade(c, 0.88); return c; };

function runeAt(sf, y) {
  const u = sf.u;
  const band = (y0, set) => {
    const v = y - y0;
    if (v < 0 || v > 4) return false;
    let g, col;
    if (u >= 3 && u <= 6) { g = GLYPHS[(sf.f * 2 + set) % 6]; col = u - 3; }
    else if (u >= 9 && u <= 12) { g = GLYPHS[(sf.f * 2 + set + 1) % 6]; col = u - 9; }
    else return false;
    return g[4 - v][col] === 'X';
  };
  if (band(18, 0) || band(5, 3)) return true;
  return (u === 2 || u === 13) && y >= 11 && y <= 19 && y % 2 === 1;
}

export function shape(x, y, z, L) {
  if (!inBlock(x, z) || y < 0 || y > 31) return null;
  const lx = LX(x), lz = LX(z), cx = x + 0.5 - 8, cz = z + 0.5 - 8;
  if (y <= 1) return lx + lz >= 1 ? 'plinth' : null;
  if (y === 2) return lx >= 1 && lz >= 1 ? 'plinth2' : null;
  if (y <= 4) return lx >= 1 && lz >= 1 ? 'flare' : null;
  if (y <= 22) {
    if (lx < 2 || lz < 2) return null;
    const post = Math.abs(cx) >= 4.5 && Math.abs(cz) >= 4.5;
    if (!post && Math.hypot(cx, y + 0.5 - SC, cz) < CR) return null;
    if (L && y >= 20 && Math.abs(cx) < 1 && Math.abs(cz) < 1) return null;
    return 'body';
  }
  if (y === 23) return lx >= 1 && lz >= 1 ? 'band' : null;
  if (y === 24) return lx >= 2 && lz >= 2 ? (lx >= 6 && lz >= 6 ? 'socket' : 'collar') : null;
  if (y <= 26) return (lx === 2 || lx === 3) && (lz === 2 || lz === 3) ? 'claw' : null;
  if (y <= 28) return (lx === 3 || lx === 4) && (lz === 3 || lz === 4) ? 'claw' : null;
  if (y <= 30) return lx === 4 && lz === 4 ? 'claw' : null;
  return null;
}

export function paint(k, x, y, z, L, e) {
  const lx = LX(x), lz = LX(z), cx = x + 0.5 - 8, cz = z + 0.5 - 8;
  if (k === 'plinth' || k === 'plinth2') return { c: tone(PAL.plinth, x, y, z, 0.16) };
  if (k === 'flare') return { c: y === 4 && e.py ? goldTone(x, y, z) : stone(x, y, z) };
  if (k === 'band') {
    const rune = (x + z) % 3 === 0 && (lx === 0 || lz === 0);
    if (rune) return L ? { c: PAL.rune, g: true } : { c: PAL.carved };
    return { c: goldTone(x, y, z) };
  }
  if (k === 'collar') return { c: e.py && (lx === 2 || lz === 2) ? goldTone(x, y, z) : stone(x, y, z) };
  if (k === 'socket') return L ? { c: goldTone(x, y, z) } : { c: PAL.waiting, g: true };
  if (k === 'claw') {
    if (y >= 28) return { c: goldTone(x, y, z) };
    if (y === 26 && anySide(e)) return L ? { c: PAL.rune, g: true } : { c: PAL.carved };
    return { c: stone(x, y, z) };
  }
  // body
  const d = Math.hypot(cx, y + 0.5 - SC, cz);
  const outer = (lz === 2 && (e.nz || e.pz)) || (lx === 2 && (e.nx || e.px));
  if (outer && d < CR + 0.45) return L ? { c: shade(PAL.rune, 0.7), g: true } : { c: PAL.carved };
  if (!outer && d < CR + 0.6) return { c: shade(stone(x, y, z), 0.55) };
  const sf = sideFace(e, x, z);
  if (sf && runeAt(sf, y)) return L ? { c: PAL.rune, fl: true } : { c: PAL.carved };
  return { c: stone(x, y, z) };
}
