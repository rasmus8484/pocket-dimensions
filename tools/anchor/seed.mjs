// World Seed: the approved item sprite (design/world-anchor-concept.html, round 22 "Starseed"). The seed gem from the
// World Anchor's claws, a shell of teal crystal around a core of starry night: a realm waiting to unfold.
import { hash3 } from './runebound.mjs';

const LO = [40, 200, 140], HI = [160, 255, 215], RIM = [16, 70, 62], SPARK = [235, 255, 248];
const shade = (c, k) => c.map(v => Math.max(0, Math.min(255, v * k)));
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const seedCol = y => mix(HI, LO, Math.min(1, Math.max(0, (y - 1) / 13)));
// the anchor's gem in profile, centred between pixels 7 and 8
const gemIn = (x, y, r, k = 0.8, cy = 7.5) => Math.abs(x + 0.5 - 8) + Math.abs(y + 0.5 - cy) * k < r;

/** Faceted gem: four facets lit from the top left, a bright ridge down the middle, a dark rim. */
function gem(x, y, r = 6.2, cy = 7.5) {
  if (!gemIn(x, y, r, 0.8, cy)) return null;
  const edge = !gemIn(x - 1, y, r, 0.8, cy) || !gemIn(x + 1, y, r, 0.8, cy) || !gemIn(x, y - 1, r, 0.8, cy) || !gemIn(x, y + 1, r, 0.8, cy);
  if (edge) return RIM;
  const left = x < 8, top = y + 0.5 < cy;
  let c = shade(seedCol(y), top ? (left ? 1.12 : 0.96) : (left ? 0.86 : 0.7));
  if (x === 7 && top) c = mix(c, SPARK, 0.55);                                               // ridge catching the light
  if ((x === 5 && y === Math.round(cy) - 3) || (x === 6 && y === Math.round(cy) - 4)) c = SPARK;   // glint
  return c;
}

/** Colour of sprite pixel (x, y), y = 0 at the top, or null where transparent. */
export function starseed(x, y) {
  const g = gem(x, y);
  if (!g) return null;
  if (gemIn(x, y, 3.1)) {                                                                     // the core of night
    const h = hash3(x, y, 22);
    return h > 0.9 ? SPARK : h > 0.78 ? [90, 120, 230] : [14, 20, 44];
  }
  return g;
}

/** The 16 x 16 RGBA texture. */
export function sprite() {
  const rgba = new Uint8Array(16 * 16 * 4);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const c = starseed(x, y);
    if (c) rgba.set([...c.map(Math.round), 255], (y * 16 + x) * 4);
  }
  return rgba;
}
