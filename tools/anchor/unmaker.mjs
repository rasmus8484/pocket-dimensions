// Unmaker: the approved Anchor Breaker design (see design/world-anchor-concept.html, round 14).
// Shared frame with the anchor: x/z 0..15, world y; the breaker block is y 32..47 and its clamps
// reach down to y 19. Anchor voxels are never overlapped. charge 0..4 fills the coils.
import { hash3, shape as anchorShape } from './runebound.mjs';

export const Y0 = 19, Y1 = 48;
const HOT = [255, 80, 40], HOT_DIM = [120, 40, 30], LAPIS = [80, 130, 255], RIVET = [176, 170, 186];

const shade = (c, k) => c.map(v => Math.max(0, Math.min(255, v * k)));
const tone = (c, x, y, z, amt = 0.12) => shade(c, 1 - amt / 2 + hash3(x, y, z) * amt);
const goldTone = (x, y, z) => (hash3(x + 11, y, z + 5) > 0.93 ? [250, 226, 150] : tone([214, 162, 60], x, y, z, 0.14));
const ironC = (x, y, z) => { let c = tone([62, 58, 74], x, y, z, 0.14); if (y % 4 === 0) c = shade(c, 0.86); return c; };
const ironD = (x, y, z) => { let c = tone([42, 38, 52], x, y, z, 0.14); if (y % 4 === 0) c = shade(c, 0.86); return c; };
const LX = v => Math.min(v, 15 - v);
const oct = (cx, cz, h, cut) => { const ax = Math.abs(cx), az = Math.abs(cz); return ax < h && az < h && ax + az < 2 * h - cut; };
const anySide = e => e.nx || e.px || e.nz || e.pz;
function sideFace(e, x, z) {
  if (e.nz) return { f: 0, u: x };
  if (e.pz) return { f: 1, u: 15 - x };
  if (e.nx) return { f: 2, u: 15 - z };
  if (e.px) return { f: 3, u: z };
  return null;
}

function rawShape(x, y, z) {
  const lx = LX(x), lz = LX(z), cx = x + 0.5 - 8, cz = z + 0.5 - 8, ax = Math.abs(cx), az = Math.abs(cz);
  if (y >= 19 && y <= 33 && lx <= 1 && lz <= 1) return (y === 26 || y === 27) ? 'buckle' : 'clamp';
  if (y >= 32 && y <= 35 && ((lx <= 2 && lz <= 4) || (lz <= 2 && lx <= 4))) return y === 32 ? 'shoulderBase' : 'shoulder';
  if (y >= 32 && y <= 33 && ax < 3 && az < 3 && (ax >= 1 || az >= 1)) return 'funnel';
  if (y >= 34 && y <= 39 && oct(cx, cz, 7, 3)) return y === 39 ? 'rim' : 'housing';
  if (y >= 40 && y <= 46) {
    if (ax < 1.5 && az < 1.5) return y === 46 ? 'spireTip' : 'spire';
    if (ax >= 3 && ax < 5 && az >= 3 && az < 5 && y <= 45) return (y === 40 || y === 45) ? 'coilCap' : 'coil';
  }
  return null;
}

/** Breaker voxel kind, or null. Never overlaps the linked anchor. */
export function shape(x, y, z) {
  if (x < 0 || x > 15 || z < 0 || z > 15 || y < Y0 || y >= Y1) return null;
  const k = rawShape(x, y, z);
  return k === null || anchorShape(x, y, z, true) !== null ? null : k;
}

/** charge 0..4: how far the red coils have filled. */
export function paint(k, x, y, z, charge, e) {
  if (k === 'rim' || k === 'buckle' || k === 'coilCap' || k === 'spireTip') return { c: goldTone(x, y, z) };
  if (k === 'shoulderBase' || k === 'spire') return { c: ironD(x, y, z) };
  if (k === 'shoulder') return { c: anySide(e) && y === 34 && (x + z) % 3 === 0 ? RIVET : ironC(x, y, z) };
  if (k === 'funnel') return e.ny ? { c: HOT, g: true } : { c: ironD(x, y, z) };
  if (k === 'clamp') return y <= 20 && anySide(e) ? { c: HOT, g: true } : { c: y % 4 === 1 ? RIVET : ironC(x, y, z) };
  if (k === 'coil') {
    const filled = (y - 41) < charge * 1.05;
    return filled ? { c: hash3(x, y, z) > 0.6 ? [255, 150, 110] : HOT, g: true } : { c: HOT_DIM };
  }
  const sf = sideFace(e, x, z);
  if (sf && y >= 35 && y <= 37) {
    if (sf.u === 7 || sf.u === 8) return { c: LAPIS, g: true };
    if (sf.u % 2 === 0 && sf.u >= 3 && sf.u <= 12) return { c: HOT, g: true };
  }
  if (y === 34) return { c: ironD(x, y, z) };
  return { c: ironC(x, y, z) };
}
