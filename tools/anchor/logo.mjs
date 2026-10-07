// Mod logo (mods.toml logoFile): the Tumbling Cube, drawn voxel for voxel from pocket.mjs in isometric view, wrapped
// in an aurora glow that hugs its silhouette (the World Core's aurora colours). The glow is pixel art like the cube:
// built on a coarse grid in a few flat steps, no smooth gradients. Run: node tools/anchor/logo.mjs [out.png]
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { encodePNG } from './png.mjs';
import { hash3 } from './runebound.mjs';
import { shape, paint, LO, HI } from './pocket.mjs';

const W = 256, H = 256, A = 8;                       // A: half the width of one voxel's top diamond, in pixels
const CX = W / 2, CY = H / 2;
const CELL = 4;                                      // the glow's pixel size (half a voxel)
const GW = W / CELL, GH = H / CELL;

/** Same as WorldCoreBlockEntityRenderer.AURORA: green, cyan, violet, pink. */
const AURORA = [[60, 255, 170], [60, 200, 255], [170, 90, 255], [255, 110, 200]];
/** Glow strength by distance from the cube, in cells: flat steps, fading out. */
const STEPS = [0.9, 0.62, 0.4, 0.24, 0.12];

// --- the cube, voxel by voxel (far to near, with a depth buffer for the portal inside) into its own layer ---------------------------------
const cube = new Uint8Array(W * H * 4);
const vox = [];
for (let y = LO; y <= HI; y++) for (let z = LO; z <= HI; z++) for (let x = LO; x <= HI; x++) {
  const k = shape(x, y, z);
  if (k) vox.push({ x, y, z, p: paint(k, x, y, z) });
}
const solid = new Set(vox.map(v => `${v.x},${v.y},${v.z}`));
const has = (x, y, z) => solid.has(`${x},${y},${z}`);
vox.sort((a, b) => (a.x + a.y + a.z) - (b.x + b.y + b.z) || a.y - b.y);

const mid = (LO + HI + 1) / 2;
const sx = (x, z) => CX + (x - z) * A;
const sy = (x, y, z) => CY + 8 + ((x - mid) + (z - mid)) * A / 2 - (y - mid) * A;

/** Fill a convex quad (screen corners in either winding) at a depth (larger is nearer); colour is a colour or (x, y) => colour. */
const zbuf = new Float32Array(W * H).fill(-Infinity);
function quad(pts, c, depth) {
  const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
  for (let y = Math.floor(Math.min(...ys)); y < Math.ceil(Math.max(...ys)); y++)
    for (let x = Math.floor(Math.min(...xs)); x < Math.ceil(Math.max(...xs)); x++) {
      const qx = x + 0.5, qy = y + 0.5;
      let pos = 0, neg = 0;                          // inside when every edge sees the point on the same side
      for (let i = 0; i < 4; i++) {
        const [ax, ay] = pts[i], [bx, by] = pts[(i + 1) % 4];
        const cr = (bx - ax) * (qy - ay) - (by - ay) * (qx - ax);
        if (cr > 0) pos++; else if (cr < 0) neg++;
      }
      if (pos && neg || x < 0 || y < 0 || x >= W || y >= H || depth < zbuf[y * W + x]) continue;
      zbuf[y * W + x] = depth;
      const col = typeof c === 'function' ? c(x, y) : c;
      cube.set([...col.map(Math.round), 255], (y * W + x) * 4);
    }
}
const shade = (c, k) => c.map(v => Math.min(255, v * k));
const P = (x, y, z) => [sx(x, z), sy(x, y, z)];

// The end portal inside the cube, seen through its windows: the same box as PocketAnchorRenderer.portal (half-width
// 2.2 px, half-height 2 px, round the centre). Like the real effect it is a starfield fixed to the screen, not the
// face: dark teal with specks of the portal's colours, on a 2-pixel grid.
const PW = 2.2, PH = 2, M = mid;
const STARS = [[40, 180, 160], [90, 225, 200], [190, 255, 240], [70, 130, 210], [150, 100, 210]];
const portalAt = k => (x, y) => {
  const gx = x >> 1, gy = y >> 1, h = hash3(gx, gy, 23);
  const base = shade([10, 26, 32], k);
  return h > 0.86 ? shade(STARS[Math.floor(hash3(gx, gy, 31) * STARS.length)], k * (0.55 + (h - 0.86) * 3)) : base;
};
quad([P(M - PW, M + PH, M - PW), P(M + PW, M + PH, M - PW), P(M + PW, M + PH, M + PW), P(M - PW, M + PH, M + PW)], portalAt(1), 3 * M + PH);
quad([P(M + PW, M + PH, M - PW), P(M + PW, M - PH, M - PW), P(M + PW, M - PH, M + PW), P(M + PW, M + PH, M + PW)], portalAt(0.8), 3 * M + PW);
quad([P(M - PW, M + PH, M + PW), P(M + PW, M + PH, M + PW), P(M + PW, M - PH, M + PW), P(M - PW, M - PH, M + PW)], portalAt(0.9), 3 * M + PW);

for (const v of vox) {
  const { x, y, z } = v, c = v.p.c, lit = v.p.g ? 1.15 : 1, d = x + y + z + 2;   // depth: the face centres' x+y+z
  const Q = (dx, dy, dz) => P(x + dx, y + dy, z + dz);
  if (!has(x, y + 1, z)) quad([Q(0, 1, 0), Q(1, 1, 0), Q(1, 1, 1), Q(0, 1, 1)], shade(c, 1.0 * lit), d);    // top
  if (!has(x + 1, y, z)) quad([Q(1, 1, 0), Q(1, 0, 0), Q(1, 0, 1), Q(1, 1, 1)], shade(c, 0.62 * lit), d);   // +x side
  if (!has(x, y, z + 1)) quad([Q(0, 1, 1), Q(1, 1, 1), Q(1, 0, 1), Q(0, 0, 1)], shade(c, 0.8 * lit), d);    // +z side
}

// --- the silhouette on the glow grid, and each cell's distance from it ------------------------------------------
const inCube = new Uint8Array(GW * GH);
for (let gy = 0; gy < GH; gy++) for (let gx = 0; gx < GW; gx++) {
  let n = 0;
  for (let y = 0; y < CELL; y++) for (let x = 0; x < CELL; x++) if (cube[((gy * CELL + y) * W + gx * CELL + x) * 4 + 3]) n++;
  inCube[gy * GW + gx] = n > CELL * CELL / 4 ? 1 : 0;
}
const dist = new Float32Array(GW * GH).fill(Infinity);
for (let gy = 0; gy < GH; gy++) for (let gx = 0; gx < GW; gx++) {
  if (inCube[gy * GW + gx]) { dist[gy * GW + gx] = 0; continue; }
  for (let y = Math.max(0, gy - 6); y <= Math.min(GH - 1, gy + 6); y++)
    for (let x = Math.max(0, gx - 6); x <= Math.min(GW - 1, gx + 6); x++)
      if (inCube[y * GW + x]) dist[gy * GW + gx] = Math.min(dist[gy * GW + gx], Math.hypot(x - gx, y - gy));
}

// --- the aurora: bands of colour that drift round the cube and ring outward; strength steps down with distance ----
/** Colour at a glow cell d cells out from the cube: aurora colours in bands, turning with the angle and the distance. */
function auroraAt(gx, gy, d) {
  const ang = Math.atan2(gy + 0.5 - GH / 2, gx + 0.5 - GW / 2) / (2 * Math.PI) + 0.5;
  return AURORA[Math.floor((ang * 6 + d * 0.18 + 0.35) % 1 * AURORA.length * 1.5) % AURORA.length];
}

const out = new Uint8Array(W * H * 4);
for (let gy = 0; gy < GH; gy++) for (let gx = 0; gx < GW; gx++) {
  const d = dist[gy * GW + gx];
  if (d === 0 || d === Infinity) continue;
  const ragged = d + (hash3(gx, gy, 41) - 0.5) * 1.1;              // wisps: the edge of each step frays
  const step = Math.floor(ragged - 0.5);
  if (step < 0 || step >= STEPS.length) continue;
  const c = auroraAt(gx, gy, d), a = STEPS[step];
  for (let y = 0; y < CELL; y++) for (let x = 0; x < CELL; x++)
    out.set([...c, Math.round(a * 255)], ((gy * CELL + y) * W + gx * CELL + x) * 4);
}

// a few loose motes drifting off the glow, like the rune particles
for (let gy = 0; gy < GH; gy++) for (let gx = 0; gx < GW; gx++) {
  const d = dist[gy * GW + gx];
  if (d < 6 || d > 9 || hash3(gx, gy, 77) < 0.975) continue;
  const c = AURORA[Math.floor(hash3(gx, gy, 5) * AURORA.length)];
  for (let y = 0; y < CELL; y++) for (let x = 0; x < CELL; x++) out.set([...c, 150], ((gy * CELL + y) * W + gx * CELL + x) * 4);
}

// --- the cube on top --------------------------------------------------------------------------------------------
for (let i = 0; i < W * H; i++) if (cube[i * 4 + 3]) out.set(cube.subarray(i * 4, i * 4 + 4), i * 4);

const file = process.argv[2] ?? join(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/pocketdimensions_logo.png');
writeFileSync(file, encodePNG(W, H, out));
console.log('wrote', file);
