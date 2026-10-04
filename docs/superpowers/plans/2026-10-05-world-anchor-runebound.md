# World Anchor Runebound Monolith Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the World Anchor's look with the approved Runebound Monolith: generated inert/linked block models, a `linked` block state, a block entity renderer for the black hole and seed, and drifting rune particles.

**Architecture:** A dependency-free Node script turns the concept's voxel shape/paint functions into Minecraft block models, atlas textures and particle sprites (committed to the repo). Java adds a `LINKED` property that swaps models, a `BlockEntityRenderer` that draws the moving parts (rift sphere with the End gateway shader, camera-facing ring, spinning disk, beam, seed) as untextured full-bright geometry, and a client-side rune particle spawned from `animateTick`.

**Tech Stack:** Node 24 (built-in `node:test`, `zlib`), Java 21, Forge 1.21.11-61.1.0 (render API: `BlockEntityRenderer<T,S>`, `SubmitNodeCollector.submitCustomGeometry`, `RenderTypes.endGateway()` / `RenderTypes.lightning()`, `SingleQuadParticle`).

**Spec:** `docs/superpowers/specs/2026-10-05-world-anchor-runebound-design.md`

## Global Constraints

- Minecraft 1.21.11 / Forge 61.1.0 APIs only; no new dependencies (Gradle or npm).
- Two blocks tall, 16×16 footprint; model element coordinates must stay within −16…32.
- At most 200 elements per generated model (target ~150).
- Runebound palette: stone `[66,70,88]`, plinth `[48,50,62]`, gold `[214,162,60]`, rune `[140,235,255]`, ring `[150,240,255]`, deep `[40,110,230]`, carved `[34,36,46]`, waiting `[120,190,230]`.
- Geometry constants: cavity centre y 13.5, radius 7.4; sphere r 3.2; photon ring r 2.8–4.2 (y squashed ×1.12, horizontal flares to 5.0); disk r 4.2–6.3; seed centre (8, 27.5, 8).
- Rings glow steadily (no pulse); rune particles ≈ 1 per second per anchor.
- Commit messages must NOT contain a `Co-Authored-By` line (user preference).
- Player-facing text keeps the existing storytelling tone (no new text is planned).
- Generated assets are committed; the Gradle build never runs Node.
- Deviation from spec §3.1/§3.3 (deliberate): the photon ring, disk, beam and seed are drawn as untextured coloured geometry with `RenderTypes.lightning()` (full-bright, additive), so the spec's `world_anchor_bh_ring/bh_disk/seed` textures are not generated. The shimmer is computed per frame instead of by an animated texture.
- The working tree already holds unrelated uncommitted work (access list, networking, earlier anchor edits). Task 0 commits it separately first so these tasks' commits only contain this feature.

## Review Focus

1. **Anchors linked before this change** load with `LINKED=false` in their block state → they must switch to the linked look on load (Task 3 `onLoad` sync; manual check step).
2. **Face UV orientation** in generated models → runes and gold trim must not appear mirrored or rotated on any face (Task 1 orientation test; Task 2 manual check).
3. **Breaking or replacing one half** → the other half and the renderer must disappear cleanly, no orphan black hole (manual check in Task 6).
4. **Viewed from below or at grazing angles** → disk must be visible from both sides; ring must never show its back (Task 4 draws both windings).
5. **Particle setting "Minimal"** → no rune particles, no errors (Task 5 manual check).

---

## File Structure

| File | Responsibility |
|---|---|
| `tools/anchor/runebound.mjs` | Shape + paint functions and palette (single source of truth for the design) |
| `tools/anchor/png.mjs` | Minimal RGBA PNG encoder |
| `tools/anchor/mesh.mjs` | Voxelize, greedy-merge into boxes, atlas packing, face UV baking |
| `tools/anchor/build.mjs` | Writes all models, textures, mcmeta, particle JSON |
| `tools/anchor/test/*.test.mjs` | `node --test` unit tests for the three modules above |
| `block/WorldAnchorBlock.java` | `LINKED` property, shapes, `setLinked` helper, `animateTick` particles |
| `blockentity/WorldAnchorBlockEntity.java` | sync state on load/unlink, render bounding box |
| `item/WorldSeedItem.java` | set `LINKED` after linking |
| `init/ModBlocks.java` | light level depends on `LINKED` |
| `init/ModParticles.java` (new) | `rune` particle type |
| `client/WorldAnchorRenderState.java` (new) | render state |
| `client/WorldAnchorBlockEntityRenderer.java` (new) | black hole, rings, beam, seed |
| `client/particle/RuneParticle.java` (new) | rune particle + provider |
| `client/ClientSetup.java` | register renderer and particle provider |
| `PocketDimensionsMod.java` | register `ModParticles.PARTICLE_TYPES` |
| `assets/.../blockstates/world_anchor.json` | 4 variants (half × linked) |

---

### Task 0: Separate the pre-existing work

- [ ] **Step 1: Ask the user** whether to commit the current uncommitted changes (see `git status`) as one commit before starting. If yes:

```bash
git add -A src README.md PRD.md pocket-rooms.md pocket-worlds.md
git commit -m "Realm access list, two-block World Anchor, Pocket Item merge and doc fixes"
```
If no, leave them and stage only the exact files named in each task.

---

### Task 1: Voxel model toolkit (shape module, PNG encoder, mesher)

**Files:**
- Create: `tools/anchor/runebound.mjs`, `tools/anchor/png.mjs`, `tools/anchor/mesh.mjs`
- Test: `tools/anchor/test/mesh.test.mjs`, `tools/anchor/test/png.test.mjs`

**Interfaces:**
- Produces:
  - `runebound.mjs`: `export const PAL`, `export const SC = 13.5`, `export const CR = 7.4`, `export const GLYPHS`, `export function hash3(x,y,z): number`, `export function shape(x,y,z,linked): string|null`, `export function paint(kind,x,y,z,linked,exposure): {c:number[3], g?:true, fl?:true}`
  - `png.mjs`: `export function encodePNG(width, height, rgba: Uint8Array): Buffer`
  - `mesh.mjs`: `export function voxelize(shape, paint, linked, y0, y1): Map<string,Voxel>` where `Voxel = {x,y,z,cls:'solid'|'glow'|'flow', c:number[3]}`; `export function mergeBoxes(voxels): Box[]` where `Box = {from:[x,y,z], to:[x,y,z], cls}` (to exclusive); `export function faceCells(box, dir): {cols, rows, cell(col,row)->[x,y,z]}` returning the face pixels in Minecraft UV order; `export function buildModel(voxels, yShift, texKey): {elements, atlas:{size, rgba}}`

- [ ] **Step 1: Write `tools/anchor/runebound.mjs`** (copied from the concept page's monolith, Runebound palette)

```js
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
```

- [ ] **Step 2: Write the failing tests**

`tools/anchor/test/png.test.mjs`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { inflateSync } from 'node:zlib';
import { encodePNG } from '../png.mjs';

test('encodes a 2x1 RGBA image with valid signature and data', () => {
  const buf = encodePNG(2, 1, Uint8Array.from([255, 0, 0, 255, 0, 255, 0, 128]));
  assert.deepEqual([...buf.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  assert.equal(buf.readUInt32BE(16), 2);          // IHDR width
  assert.equal(buf.readUInt32BE(20), 1);          // IHDR height
  const idatLen = buf.readUInt32BE(33);
  const raw = inflateSync(buf.subarray(41, 41 + idatLen));
  assert.deepEqual([...raw], [0, 255, 0, 0, 255, 0, 255, 0, 128]);  // filter byte + pixels
});
```

`tools/anchor/test/mesh.test.mjs`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { voxelize, mergeBoxes, faceCells, buildModel } from '../mesh.mjs';
import { shape, paint } from '../runebound.mjs';

const cube = (x, y, z) => (x >= 0 && x < 2 && y >= 0 && y < 2 && z >= 0 && z < 2 ? 'k' : null);
const flat = () => ({ c: [10, 20, 30] });

test('a 2x2x2 cube of one class merges into one box', () => {
  const boxes = mergeBoxes(voxelize(cube, flat, false, 0, 16));
  assert.equal(boxes.length, 1);
  assert.deepEqual(boxes[0].from, [0, 0, 0]);
  assert.deepEqual(boxes[0].to, [2, 2, 2]);
});

test('glow voxels never merge with solid voxels', () => {
  const p = (k, x) => (x === 0 ? { c: [1, 1, 1], g: true } : { c: [2, 2, 2] });
  const boxes = mergeBoxes(voxelize(cube, p, false, 0, 16));
  assert.equal(boxes.length, 2);
  assert.deepEqual(boxes.map(b => b.cls).sort(), ['glow', 'solid']);
});

test('north face columns run from high x to low x (Minecraft UV order)', () => {
  const box = { from: [3, 0, 0], to: [6, 2, 1], cls: 'solid' };
  const f = faceCells(box, 'north');
  assert.equal(f.cols, 3);
  assert.deepEqual(f.cell(0, 0), [5, 1, 0]);   // left of the north face = highest x, top row = highest y
  const s = faceCells(box, 'south');
  assert.deepEqual(s.cell(0, 0), [3, 1, 0]);
});

test('runebound halves stay within the element budget', () => {
  for (const L of [false, true]) for (const [y0, y1] of [[0, 16], [16, 32]]) {
    const m = buildModel(voxelize(shape, paint, L, y0, y1), y0, 'main');
    assert.ok(m.elements.length <= 200, `linked=${L} y${y0}: ${m.elements.length} elements`);
    for (const el of m.elements) for (const v of [...el.from, ...el.to]) assert.ok(v >= -16 && v <= 32);
  }
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `node --test tools/anchor/test/`
Expected: FAIL with `Cannot find module '.../png.mjs'` / `mesh.mjs`.

- [ ] **Step 4: Write `tools/anchor/png.mjs`**

```js
import { deflateSync } from 'node:zlib';

const CRC = new Uint32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = buf => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };

function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

/** Encode an RGBA image (rgba.length === w*h*4) as a PNG file buffer. */
export function encodePNG(w, h, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; // 8-bit RGBA
  const raw = Buffer.alloc(h * (w * 4 + 1));
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    Buffer.from(rgba.buffer, rgba.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
  }
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
  ]);
}
```

- [ ] **Step 5: Write `tools/anchor/mesh.mjs`**

```js
const key = (x, y, z) => `${x},${y},${z}`;
const DIRS = { px: [1, 0, 0], nx: [-1, 0, 0], py: [0, 1, 0], ny: [0, -1, 0], pz: [0, 0, 1], nz: [0, 0, -1] };

/** Fill every voxel in x/z 0..15, y y0..y1-1. Exposed voxels get their painted colour and class. */
export function voxelize(shape, paint, L, y0, y1) {
  const kinds = new Map();
  for (let y = y0; y < y1; y++) for (let z = 0; z < 16; z++) for (let x = 0; x < 16; x++) {
    const k = shape(x, y, z, L);
    if (k !== null) kinds.set(key(x, y, z), k);
  }
  // exposure is judged against the full design so the seam between halves is treated as solid
  const filled = (x, y, z) => kinds.has(key(x, y, z)) || ((y < y0 || y >= y1) && shape(x, y, z, L) !== null);
  const out = new Map();
  for (const [k, kind] of kinds) {
    const [x, y, z] = k.split(',').map(Number);
    const e = {};
    for (const [n, [dx, dy, dz]] of Object.entries(DIRS)) e[n] = !filled(x + dx, y + dy, z + dz);
    const exposed = Object.values(e).some(Boolean);
    const r = exposed ? paint(kind, x, y, z, L, e) : { c: [0, 0, 0] };
    out.set(k, { x, y, z, cls: r.fl ? 'flow' : r.g ? 'glow' : 'solid', c: r.c.map(Math.round) });
  }
  return out;
}

/** Greedy-merge voxels of the same class into boxes (to = exclusive). */
export function mergeBoxes(voxels) {
  const used = new Set(), boxes = [];
  const ok = (x, y, z, cls) => { const v = voxels.get(key(x, y, z)); return v && v.cls === cls && !used.has(key(x, y, z)); };
  const sorted = [...voxels.values()].sort((a, b) => a.y - b.y || a.z - b.z || a.x - b.x);
  for (const v of sorted) {
    if (used.has(key(v.x, v.y, v.z))) continue;
    const cls = v.cls;
    let x2 = v.x; while (ok(x2 + 1, v.y, v.z, cls)) x2++;
    let z2 = v.z;
    rowZ: while (true) { for (let i = v.x; i <= x2; i++) if (!ok(i, v.y, z2 + 1, cls)) break rowZ; z2++; }
    let y2 = v.y;
    slabY: while (true) { for (let i = v.x; i <= x2; i++) for (let j = v.z; j <= z2; j++) if (!ok(i, y2 + 1, j, cls)) break slabY; y2++; }
    for (let a = v.y; a <= y2; a++) for (let b = v.z; b <= z2; b++) for (let c = v.x; c <= x2; c++) used.add(key(c, a, b));
    boxes.push({ from: [v.x, v.y, v.z], to: [x2 + 1, y2 + 1, z2 + 1], cls });
  }
  return boxes;
}

/**
 * Pixels of one box face in Minecraft's default UV order: col 0 is the face's left edge as seen
 * from outside, row 0 its top edge. cell(col,row) returns the voxel coordinate behind that pixel.
 */
export function faceCells(b, dir) {
  const [x0, y0, z0] = b.from, [x1, y1, z1] = b.to;
  const W = { north: x1 - x0, south: x1 - x0, east: z1 - z0, west: z1 - z0, up: x1 - x0, down: x1 - x0 }[dir];
  const H = { north: y1 - y0, south: y1 - y0, east: y1 - y0, west: y1 - y0, up: z1 - z0, down: z1 - z0 }[dir];
  const cell = (c, r) => ({
    north: [x1 - 1 - c, y1 - 1 - r, z0],
    south: [x0 + c, y1 - 1 - r, z1 - 1],
    east: [x1 - 1, y1 - 1 - r, z1 - 1 - c],
    west: [x0, y1 - 1 - r, z0 + c],
    up: [x0 + c, y1 - 1, z0 + r],
    down: [x0 + c, y0, z1 - 1 - r],
  }[dir]);
  return { cols: W, rows: H, cell };
}

const NEIGH = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };

/** Build Minecraft elements + one atlas for a half. yShift is subtracted from y (16 for the upper half). */
export function buildModel(voxels, yShift, texKey, extraFilled = () => false) {
  const boxes = mergeBoxes(voxels);
  const filled = (x, y, z) => voxels.has(key(x, y, z)) || extraFilled(x, y, z);
  const faces = [];
  for (const b of boxes) for (const dir of Object.keys(NEIGH)) {
    const f = faceCells(b, dir), [dx, dy, dz] = NEIGH[dir];
    let visible = false;
    for (let r = 0; r < f.rows && !visible; r++) for (let c = 0; c < f.cols && !visible; c++) {
      const [x, y, z] = f.cell(c, r);
      if (!filled(x + dx, y + dy, z + dz)) visible = true;
    }
    if (visible) faces.push({ b, dir, f });
  }
  // shelf-pack face rectangles into the smallest square atlas that fits
  let size = 16, placed;
  while (true) {
    placed = []; let x = 0, y = 0, rowH = 0, fit = true;
    for (const face of [...faces].sort((a, b) => b.f.rows - a.f.rows)) {
      if (x + face.f.cols > size) { x = 0; y += rowH; rowH = 0; }
      if (y + face.f.rows > size) { fit = false; break; }
      placed.push({ ...face, ax: x, ay: y });
      x += face.f.cols; rowH = Math.max(rowH, face.f.rows);
    }
    if (fit) break;
    size *= 2;
    if (size > 512) throw new Error('atlas too large');
  }
  const rgba = new Uint8Array(size * size * 4);
  const elements = new Map();
  for (const p of placed) {
    for (let r = 0; r < p.f.rows; r++) for (let c = 0; c < p.f.cols; c++) {
      const v = voxels.get(key(...p.f.cell(c, r)));
      const i = ((p.ay + r) * size + p.ax + c) * 4;
      rgba.set([...v.c, 255], i);
    }
    const s = 16 / size;
    let el = elements.get(p.b);
    if (!el) {
      el = { from: [p.b.from[0], p.b.from[1] - yShift, p.b.from[2]], to: [p.b.to[0], p.b.to[1] - yShift, p.b.to[2]], faces: {} };
      if (p.b.cls !== 'solid') { el.shade = false; el.light_emission = 15; }
      elements.set(p.b, el);
    }
    el.faces[p.dir] = { uv: [p.ax * s, p.ay * s, (p.ax + p.f.cols) * s, (p.ay + p.f.rows) * s], texture: p.b.cls === 'flow' ? '#flow' : `#${texKey}` };
  }
  return { elements: [...elements.values()], atlas: { size, rgba }, placed };
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `node --test tools/anchor/test/`
Expected: PASS, 5 tests.

- [ ] **Step 7: Commit**

```bash
git add tools/anchor
git commit -m "Add voxel-to-Minecraft model toolkit for the World Anchor"
```

---

### Task 2: Generate the assets

**Files:**
- Create: `tools/anchor/build.mjs`
- Generated: `src/main/resources/assets/pocketdimensions/models/block/world_anchor_{inert,linked}_{lower,upper}.json`, `models/item/world_anchor.json`, `textures/block/world_anchor_{inert,linked}_{lower,upper}.png`, `textures/block/world_anchor_{inert,linked}_{lower,upper}_flow.png(.mcmeta)`, `textures/particle/rune_0..5.png`, `particles/rune.json`

**Interfaces:**
- Consumes: Task 1 exports.
- Produces: model ids `pocketdimensions:block/world_anchor_{inert|linked}_{lower|upper}`, item model `pocketdimensions:item/world_anchor`, particle sprites `pocketdimensions:rune_0..5`.

- [ ] **Step 1: Write `tools/anchor/build.mjs`**

```js
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { shape, paint, GLYPHS, PAL, SC } from './runebound.mjs';
import { voxelize, buildModel } from './mesh.mjs';
import { encodePNG } from './png.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/assets/pocketdimensions');
const write = (rel, data) => { const p = join(root, rel); mkdirSync(dirname(p), { recursive: true }); writeFileSync(p, data); };
const json = o => JSON.stringify(o, null, 2) + '\n';

const FLOW_FRAMES = 8;
// Flow texture: the flow pixels of the atlas, brightness wave moving toward the core (y 13.5).
function flowStrip(model, voxels) {
  const { size } = model.atlas, out = new Uint8Array(size * size * 4 * FLOW_FRAMES);
  for (const p of model.placed) if (p.b.cls === 'flow') {
    for (let r = 0; r < p.f.rows; r++) for (let c = 0; c < p.f.cols; c++) {
      const [x, y, z] = p.f.cell(c, r);
      const v = voxels.get(`${x},${y},${z}`);
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - SC, z + 0.5 - 8);
      for (let k = 0; k < FLOW_FRAMES; k++) {
        const b = 0.3 + 0.7 * Math.pow(0.5 + 0.5 * Math.sin(d * 1.625 + (k / FLOW_FRAMES) * 2 * Math.PI), 2);
        const i = ((k * size + p.ay + r) * size + p.ax + c) * 4;
        out.set([...v.c.map(ch => Math.min(255, Math.round(ch * b))), 255], i);
      }
    }
  }
  return { w: size, h: size * FLOW_FRAMES, rgba: out };
}

let report = [];
const halves = {};
for (const [state, L] of [['inert', false], ['linked', true]]) {
  for (const [half, y0, y1] of [['lower', 0, 16], ['upper', 16, 32]]) {
    const name = `world_anchor_${state}_${half}`;
    const voxels = voxelize(shape, paint, L, y0, y1);
    const other = (x, y, z) => (y < y0 || y >= y1) && shape(x, y, z, L) !== null;
    const m = buildModel(voxels, y0, 'main', other);
    if (m.elements.length > 200) throw new Error(`${name}: ${m.elements.length} elements (max 200)`);
    report.push(`${name}: ${m.elements.length} elements, atlas ${m.atlas.size}px`);
    write(`textures/block/${name}.png`, encodePNG(m.atlas.size, m.atlas.size, m.atlas.rgba));
    const textures = { main: `pocketdimensions:block/${name}`, particle: `pocketdimensions:block/${name}` };
    if (m.elements.some(e => Object.values(e.faces).some(f => f.texture === '#flow'))) {
      const s = flowStrip(m, voxels);
      write(`textures/block/${name}_flow.png`, encodePNG(s.w, s.h, s.rgba));
      write(`textures/block/${name}_flow.png.mcmeta`, json({ animation: { frametime: 8, interpolate: true } }));
      textures.flow = `pocketdimensions:block/${name}_flow`;
    }
    write(`models/block/${name}.json`, json({ parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: m.elements }));
    halves[`${state}_${half}`] = { m, textures };
  }
}

// Item model: the inert monolith, both halves in one model
const lo = halves.inert_lower, up = halves.inert_upper;
const retex = (els, k) => els.map(e => ({ ...e, faces: Object.fromEntries(Object.entries(e.faces).map(([d, f]) => [d, { ...f, texture: `#${k}` }])) }));
const lift = els => els.map(e => ({ ...e, from: [e.from[0], e.from[1] + 16, e.from[2]], to: [e.to[0], e.to[1] + 16, e.to[2]] }));
write('models/item/world_anchor.json', json({
  parent: 'minecraft:block/block',
  textures: { lower: lo.textures.main, upper: up.textures.main, particle: lo.textures.main },
  elements: [...retex(lo.m.elements, 'lower'), ...lift(retex(up.m.elements, 'upper'))],
  display: {
    gui: { rotation: [30, 225, 0], translation: [0, -4, 0], scale: [0.34, 0.34, 0.34] },
    ground: { translation: [0, 2, 0], scale: [0.2, 0.2, 0.2] },
    fixed: { translation: [0, -4, 0], scale: [0.4, 0.4, 0.4] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 1, 0], scale: [0.25, 0.25, 0.25] },
    firstperson_righthand: { rotation: [0, 45, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3] },
    firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3] },
  },
}));

// Rune particle sprites: 8x8, glyph centred, white (tinted in code)
GLYPHS.forEach((g, i) => {
  const rgba = new Uint8Array(8 * 8 * 4);
  g.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === 'X') rgba.set([255, 255, 255, 255], ((r + 1) * 8 + c + 2) * 4); }));
  write(`textures/particle/rune_${i}.png`, encodePNG(8, 8, rgba));
});
write('particles/rune.json', json({ textures: GLYPHS.map((_, i) => `pocketdimensions:rune_${i}`) }));

console.log(report.join('\n'));
```

- [ ] **Step 2: Run the generator**

Run: `node tools/anchor/build.mjs`
Expected: four lines like `world_anchor_inert_lower: N elements, atlas 64px`, every N ≤ 200, no exception.

- [ ] **Step 3: Check the generated textures by eye**

Run: `python3 -c "from PIL import Image; im=Image.open('src/main/resources/assets/pocketdimensions/textures/block/world_anchor_linked_lower.png'); im.resize((im.width*8,im.height*8),Image.NEAREST).save('C:/Users/rasmu/AppData/Local/Temp/atlas_preview.png')"` and open the preview.
Expected: basalt greys, gold trim strips and cyan rune pixels; no fully black atlas.

- [ ] **Step 4: Commit**

```bash
cd src/main/resources/assets/pocketdimensions
git add models/block/world_anchor_inert_*.json models/block/world_anchor_linked_*.json models/item/world_anchor.json         textures/block/world_anchor_inert_* textures/block/world_anchor_linked_* textures/particle particles
cd -
git add tools/anchor/build.mjs
git commit -m "Generate Runebound World Anchor models, textures and rune sprites"
```

---

### Task 3: `LINKED` block state, shapes, light and state sync

**Files:**
- Modify: `src/main/java/com/pocketdimensions/block/WorldAnchorBlock.java`
- Modify: `src/main/java/com/pocketdimensions/blockentity/WorldAnchorBlockEntity.java` (`unlink()`, add `onLoad()`, `getRenderBoundingBox()`)
- Modify: `src/main/java/com/pocketdimensions/item/WorldSeedItem.java` (after `be.setLinked(true);`)
- Modify: `src/main/java/com/pocketdimensions/init/ModBlocks.java` (WORLD_ANCHOR properties)
- Modify: `src/main/resources/assets/pocketdimensions/blockstates/world_anchor.json`
- Modify: `src/main/resources/assets/pocketdimensions/items/world_anchor.json`
- Delete: `models/block/world_anchor_lower.json`, `models/block/world_anchor_upper.json`, `textures/block/world_anchor_{stone,gold,runes,energy}.png`, folder `worldanchor_assets/`

**Interfaces:**
- Consumes: Task 2 model ids.
- Produces: `public static final BooleanProperty WorldAnchorBlock.LINKED`; `public static void WorldAnchorBlock.setLinked(Level level, BlockPos lowerPos, boolean linked)` (updates both halves, flag 3).

- [ ] **Step 1: Add the property and helper to `WorldAnchorBlock`**

```java
// fields (next to HALF)
public static final BooleanProperty LINKED = BooleanProperty.create("linked");
private static final VoxelShape LOWER_SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 5, 16), Block.box(2, 5, 2, 14, 16, 14));
private static final VoxelShape UPPER_SHAPE = Block.box(2, 0, 2, 14, 15, 14);

// constructor default state
registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(LINKED, false));

// createBlockStateDefinition
builder.add(HALF, LINKED);

@Override
protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
    return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
}

/** Sets LINKED on both halves of the anchor whose lower half is at lowerPos. */
public static void setLinked(Level level, BlockPos lowerPos, boolean linked) {
    for (BlockPos p : new BlockPos[]{lowerPos, lowerPos.above()}) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof WorldAnchorBlock && s.getValue(LINKED) != linked) {
            level.setBlock(p, s.setValue(LINKED, linked), 3);
        }
    }
}
```
Imports: `net.minecraft.world.level.block.state.properties.BooleanProperty`, `net.minecraft.world.phys.shapes.{VoxelShape,Shapes,CollisionContext}`, `net.minecraft.world.level.BlockGetter`. In `setPlacedBy`, the upper half is placed with `defaultBlockState().setValue(HALF, UPPER)`, which already carries `LINKED=false` — no change needed.

- [ ] **Step 2: Set the state when linking (`WorldSeedItem`)** — directly after `be.setLinked(true);`:

```java
WorldAnchorBlock.setLinked(level, anchorPos, true);
```

- [ ] **Step 3: `WorldAnchorBlockEntity`** — update `unlink()` and add two overrides:

```java
public void unlink() {
    this.ownerUUID = null;
    this.linked = false;
    setChanged();
    if (level != null && !level.isClientSide()) WorldAnchorBlock.setLinked(level, worldPosition, false);
}

/** Anchors linked before the LINKED block state existed load as inert; fix them up once. */
@Override
public void onLoad() {
    super.onLoad();
    if (level != null && !level.isClientSide() && getBlockState().getValue(WorldAnchorBlock.LINKED) != linked) {
        WorldAnchorBlock.setLinked(level, worldPosition, linked);
    }
}

/** The black hole and seed reach into the upper block. */
@Override
public AABB getRenderBoundingBox() {
    return new AABB(worldPosition).expandTowards(0, 1, 0);
}
```
Imports: `com.pocketdimensions.block.WorldAnchorBlock`, `net.minecraft.world.phys.AABB`.

- [ ] **Step 4: Light level in `ModBlocks.WORLD_ANCHOR`** — add to its properties chain:

```java
.lightLevel(state -> state.getValue(WorldAnchorBlock.LINKED) ? 10 : 0)
```

- [ ] **Step 5: Blockstate and item JSON**

`blockstates/world_anchor.json`:
```json
{
  "variants": {
    "half=lower,linked=false": { "model": "pocketdimensions:block/world_anchor_inert_lower" },
    "half=upper,linked=false": { "model": "pocketdimensions:block/world_anchor_inert_upper" },
    "half=lower,linked=true":  { "model": "pocketdimensions:block/world_anchor_linked_lower" },
    "half=upper,linked=true":  { "model": "pocketdimensions:block/world_anchor_linked_upper" }
  }
}
```
`items/world_anchor.json`:
```json
{ "model": { "type": "minecraft:model", "model": "pocketdimensions:item/world_anchor" } }
```

- [ ] **Step 6: Remove the old assets**

```bash
rm -f src/main/resources/assets/pocketdimensions/models/block/world_anchor_lower.json src/main/resources/assets/pocketdimensions/models/block/world_anchor_upper.json
rm -f src/main/resources/assets/pocketdimensions/textures/block/world_anchor_{stone,gold,runes,energy}.png
rm -rf worldanchor_assets
```

- [ ] **Step 7: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Manual check (runClient)**

Run `./gradlew runClient`, open the test world, creative mode:
1. Place a World Anchor → inert monolith; claws with a faintly glowing socket; F3 shows `linked: false`.
2. Use a World Seed on it → both halves switch to the linked model, runes glow, light around it rises.
3. Runes on all four faces read the same way up (not mirrored/rotated vs. the concept).
4. Log has no `Missing texture` / `Unable to load model` lines for `world_anchor`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/pocketdimensions/block/WorldAnchorBlock.java src/main/java/com/pocketdimensions/blockentity/WorldAnchorBlockEntity.java src/main/java/com/pocketdimensions/item/WorldSeedItem.java src/main/java/com/pocketdimensions/init/ModBlocks.java src/main/resources/assets/pocketdimensions/blockstates/world_anchor.json src/main/resources/assets/pocketdimensions/items/world_anchor.json
git add -u src/main/resources/assets/pocketdimensions   # records removal of the old anchor files if they were tracked
git commit -m "Add linked block state to World Anchor and switch to Runebound models"
```

---

### Task 4: Black hole and seed renderer

**Files:**
- Create: `src/main/java/com/pocketdimensions/client/WorldAnchorRenderState.java`
- Create: `src/main/java/com/pocketdimensions/client/WorldAnchorBlockEntityRenderer.java`
- Modify: `src/main/java/com/pocketdimensions/client/ClientSetup.java` (`onRegisterRenderers`)

**Interfaces:**
- Consumes: `WorldAnchorBlock.LINKED`, `ModBlockEntityTypes.WORLD_ANCHOR`.
- Produces: renderer registered for `WORLD_ANCHOR`.

- [ ] **Step 1: Render state**

```java
package com.pocketdimensions.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Extracted on the game thread for WorldAnchorBlockEntityRenderer. */
public class WorldAnchorRenderState extends BlockEntityRenderState {
    public boolean linked;
    /** Seconds, continuous (game time + partial tick) / 20. */
    public float time;
}
```

- [ ] **Step 2: Renderer**

```java
package com.pocketdimensions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pocketdimensions.block.WorldAnchorBlock;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Draws the linked World Anchor's black hole (rift sphere, photon ring, accretion disk), beam and seed. */
public class WorldAnchorBlockEntityRenderer implements BlockEntityRenderer<WorldAnchorBlockEntity, WorldAnchorRenderState> {

    private static final float PX = 1f / 16f;
    private static final float SC = 13.5f;
    private static final float[] GLOW = {215 / 255f, 248 / 255f, 255 / 255f};   // ring colour mixed 45% toward white
    private static final float[] DEEP = {40 / 255f, 110 / 255f, 230 / 255f};
    private static final float STEADY = 1.06f;                                 // midpoint of the old pulse

    private static final List<int[]> SPHERE_FACES = blockySphereFaces(3.2);
    private static final List<int[]> RING = ringPixels();
    private static final List<int[]> DISK = diskPixels();
    private static final List<int[]> SEED = seedVoxels();

    public WorldAnchorBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public WorldAnchorRenderState createRenderState() { return new WorldAnchorRenderState(); }

    @Override
    public void extractRenderState(WorldAnchorBlockEntity be, WorldAnchorRenderState s, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, s, crumbling);
        s.linked = be.getBlockState().getValue(WorldAnchorBlock.LINKED);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() + partialTick) / 20f;
    }

    @Override
    public void submit(WorldAnchorRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (!s.linked) return;
        float t = s.time;

        // Rift sphere, turned to face the camera like the ring so their pixel edges line up
        pose.pushPose();
        pose.translate(0.5f, SC * PX, 0.5f);
        pose.mulPose(camera.orientation);
        out.submitCustomGeometry(pose, RenderTypes.endGateway(), (p, vc) -> {
            Matrix4f m = p.pose();
            for (int[] f : SPHERE_FACES) {
                for (int i = 0; i < 4; i++) vc.addVertex(m, f[i * 3] * PX, f[i * 3 + 1] * PX, f[i * 3 + 2] * PX);
            }
        });
        // Photon ring: camera-facing, 0.6 px toward the viewer, shimmer shifts one pixel 6x a second
        pose.translate(0f, 0f, 0.6f * PX);
        int step = (int) Math.floor(t * 6);
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
            for (int[] px : RING) {
                int slot = Math.floorMod(px[2] - step, RING.size());
                float b = (0.92f + 0.16f * hash(slot, 3, 11)) * STEADY;
                quadXY(vc, p.pose(), px[0], px[1], GLOW[0] * b, GLOW[1] * b, GLOW[2] * b, 1f);
            }
        });
        pose.popPose();

        // Accretion disk: flat ring spinning about Y, drawn from both sides
        pose.pushPose();
        pose.translate(0.5f, SC * PX, 0.5f);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(t * 0.6f));
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
            for (int[] px : DISK) {
                float r = (float) Math.hypot(px[0] + 0.5, px[1] + 0.5);
                float f = Math.min(1f, Math.max(0f, (r - 4.5f) / 1.7f));
                float b = (0.92f + 0.16f * hash(px[0], 7, px[1])) * STEADY;
                float cr = (GLOW[0] + (DEEP[0] - GLOW[0]) * f) * b, cg = (GLOW[1] + (DEEP[1] - GLOW[1]) * f) * b, cb = (GLOW[2] + (DEEP[2] - GLOW[2]) * f) * b;
                quadXZ(vc, p.pose(), px[0], px[1], cr, cg, cb);
            }
        });
        pose.popPose();

        // Beam from the cavity roof up to the seed
        float beamA = 0.55f + 0.35f * (0.5f + 0.5f * (float) Math.sin(t * 1.4));
        pose.pushPose();
        pose.translate(0.5f, 20.7f * PX, 0.5f);
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> box(vc, p.pose(), 0.6f * PX, 3f * PX, GLOW, beamA));
        pose.popPose();

        // Seed: floats and turns in the claws
        pose.pushPose();
        pose.translate(0.5f, (27.5f + 0.6f * (float) Math.sin(t * 1.4)) * PX, 0.5f);
        pose.mulPose(com.mojang.math.Axis.YP.rotation(t * 0.7f));
        out.submitCustomGeometry(pose, RenderTypes.lightning(), (p, vc) -> {
            for (int[] v : SEED) {
                float k = (v[1] + 4) / 7f;
                float[] c = {(40 + k * 120) / 255f, (200 + k * 55) / 255f, (140 + k * 75) / 255f};
                cube(vc, p.pose(), v[0], v[1], v[2], c);
            }
        });
        pose.popPose();
    }

    // ---- geometry helpers -------------------------------------------------------------------

    private static void quadXY(VertexConsumer vc, Matrix4f m, int x, int y, float r, float g, float b, float a) {
        vc.addVertex(m, x * PX, y * PX, 0).setColor(r, g, b, a);
        vc.addVertex(m, (x + 1) * PX, y * PX, 0).setColor(r, g, b, a);
        vc.addVertex(m, (x + 1) * PX, (y + 1) * PX, 0).setColor(r, g, b, a);
        vc.addVertex(m, x * PX, (y + 1) * PX, 0).setColor(r, g, b, a);
    }

    private static void quadXZ(VertexConsumer vc, Matrix4f m, int x, int z, float r, float g, float b) {
        float x0 = x * PX, x1 = (x + 1) * PX, z0 = z * PX, z1 = (z + 1) * PX;
        vc.addVertex(m, x0, 0, z0).setColor(r, g, b, 1f); vc.addVertex(m, x0, 0, z1).setColor(r, g, b, 1f);
        vc.addVertex(m, x1, 0, z1).setColor(r, g, b, 1f); vc.addVertex(m, x1, 0, z0).setColor(r, g, b, 1f);
        vc.addVertex(m, x0, 0, z0).setColor(r, g, b, 1f); vc.addVertex(m, x1, 0, z0).setColor(r, g, b, 1f);
        vc.addVertex(m, x1, 0, z1).setColor(r, g, b, 1f); vc.addVertex(m, x0, 0, z1).setColor(r, g, b, 1f);
    }

    /** Axis-aligned box centred on x/z, bottom at y=0. */
    private static void box(VertexConsumer vc, Matrix4f m, float half, float h, float[] c, float a) {
        float[][] q = {
            {-half, 0, -half, half, 0, -half, half, h, -half, -half, h, -half},
            {half, 0, half, -half, 0, half, -half, h, half, half, h, half},
            {-half, 0, half, -half, 0, -half, -half, h, -half, -half, h, half},
            {half, 0, -half, half, 0, half, half, h, half, half, h, -half}};
        for (float[] f : q) for (int i = 0; i < 4; i++) vc.addVertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(c[0], c[1], c[2], a);
    }

    /** One seed voxel as six faces (pixel units, centred). */
    private static void cube(VertexConsumer vc, Matrix4f m, int x, int y, int z, float[] c) {
        float x0 = x * PX, x1 = (x + 1) * PX, y0 = y * PX, y1 = (y + 1) * PX, z0 = z * PX, z1 = (z + 1) * PX;
        float[][] q = {
            {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
            {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
            {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
        for (float[] f : q) for (int i = 0; i < 4; i++) vc.addVertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(c[0], c[1], c[2], 1f);
    }

    private static float hash(int x, int y, int z) {
        int n = x * 374761393 ^ y * 668265263 ^ z * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) >>> 0 & 0xFFFFFFFFL) / 4294967295f;
    }

    // ---- static shape tables (pixel units, centred on the black hole) -------------------------

    /** Outward faces of a voxel sphere, each as 4 vertices (x,y,z) in counter-clockwise order. */
    private static List<int[]> blockySphereFaces(double R) {
        List<int[]> out = new ArrayList<>();
        int n = (int) Math.ceil(R) + 1;
        for (int i = -n; i < n; i++) for (int j = -n; j < n; j++) for (int k = -n; k < n; k++) {
            if (!inSphere(i, j, k, R)) continue;
            int[] cell = {i, j, k};
            for (int a = 0; a < 3; a++) for (int sgn : new int[]{1, -1}) {
                int[] nb = cell.clone(); nb[a] += sgn;
                if (inSphere(nb[0], nb[1], nb[2], R)) continue;
                int b = (a + 1) % 3, c = (a + 2) % 3;
                int[] p = cell.clone(); if (sgn > 0) p[a] += 1;
                int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                if (sgn < 0) corners = new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}};
                int[] f = new int[12];
                for (int v = 0; v < 4; v++) {
                    int[] q = p.clone(); q[b] += corners[v][0]; q[c] += corners[v][1];
                    f[v * 3] = q[0]; f[v * 3 + 1] = q[1]; f[v * 3 + 2] = q[2];
                }
                out.add(f);
            }
        }
        return out;
    }

    private static boolean inSphere(int i, int j, int k, double R) {
        return Math.sqrt((i + .5) * (i + .5) + (j + .5) * (j + .5) + (k + .5) * (k + .5)) < R;
    }

    /** Photon ring pixels {x, y, slot}; slot = position around the ring, used by the shimmer. */
    private static List<int[]> ringPixels() {
        List<int[]> px = new ArrayList<>();
        for (int x = -6; x < 6; x++) for (int y = -6; y < 6; y++) {
            double fx = x + .5, fy = y + .5, r = Math.hypot(fx, fy * 1.12);
            boolean ring = r >= 2.8 && r < 4.2;
            boolean flare = Math.abs(fy) < 1 && Math.abs(fx) < 5.0 && Math.abs(fx) >= 2.8;
            if (ring || flare) px.add(new int[]{x, y, 0});
        }
        px.sort((a, b) -> Double.compare(Math.atan2(a[1] + .5, a[0] + .5), Math.atan2(b[1] + .5, b[0] + .5)));
        for (int i = 0; i < px.size(); i++) px.get(i)[2] = i;
        return px;
    }

    private static List<int[]> diskPixels() {
        List<int[]> px = new ArrayList<>();
        for (int x = -7; x < 7; x++) for (int z = -7; z < 7; z++) {
            double r = Math.hypot(x + .5, z + .5);
            if (r >= 4.2 && r < 6.3) px.add(new int[]{x, z});
        }
        return px;
    }

    /** Gem-shaped seed voxels {x, y, z}, centred. */
    private static List<int[]> seedVoxels() {
        List<int[]> v = new ArrayList<>();
        for (int i = -3; i < 3; i++) for (int j = -4; j < 4; j++) for (int k = -3; k < 3; k++) {
            if (Math.abs(i + .5) + Math.abs(j + .5) * 0.75 + Math.abs(k + .5) < 2.7) v.add(new int[]{i, j, k});
        }
        return v;
    }

    @Override
    public boolean shouldRenderOffScreen() { return true; }
}
```

- [ ] **Step 3: Register in `ClientSetup.onRegisterRenderers`**

```java
event.<WorldAnchorBlockEntity, WorldAnchorRenderState>registerBlockEntityRenderer(
        ModBlockEntityTypes.WORLD_ANCHOR.get(),
        ctx -> new WorldAnchorBlockEntityRenderer(ctx));
```
Import `com.pocketdimensions.blockentity.WorldAnchorBlockEntity`.

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. If the compiler rejects `Axis.YP.rotation` or `camera.orientation`, probe the sources jar (`com/mojang/math/Axis.java`, `CameraRenderState.java`) and adjust the call — do not guess.

- [ ] **Step 5: Manual check (runClient)** on a linked anchor:
1. Starry blocky sphere visible in every window; disk spins; ring always faces you while you walk around; no dark gap between ring and sphere.
2. Look from below the disk and at a grazing angle: disk visible from both sides.
3. Seed floats and turns between the claws; beam connects cavity roof to seed.
4. Inert anchors draw none of this.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/pocketdimensions/client/WorldAnchorRenderState.java src/main/java/com/pocketdimensions/client/WorldAnchorBlockEntityRenderer.java src/main/java/com/pocketdimensions/client/ClientSetup.java
git commit -m "Render the World Anchor black hole, beam and seed"
```

---

### Task 5: Rune particles

**Files:**
- Create: `src/main/java/com/pocketdimensions/init/ModParticles.java`
- Create: `src/main/java/com/pocketdimensions/client/particle/RuneParticle.java`
- Modify: `src/main/java/com/pocketdimensions/PocketDimensionsMod.java` (register next to `ModMenuTypes.MENU_TYPES.register(modBusGroup);`)
- Modify: `src/main/java/com/pocketdimensions/client/ClientSetup.java` (constructor)
- Modify: `src/main/java/com/pocketdimensions/block/WorldAnchorBlock.java` (add `animateTick`)

**Interfaces:**
- Consumes: Task 2 sprites `pocketdimensions:rune_0..5` and `particles/rune.json`; Task 3 `LINKED`.
- Produces: `ModParticles.RUNE: RegistryObject<SimpleParticleType>`.

- [ ] **Step 1: `ModParticles`**

```java
package com.pocketdimensions.init;

import com.pocketdimensions.PocketDimensionsMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, PocketDimensionsMod.MODID);

    /** Glowing rune glyph that drifts off a linked World Anchor. */
    public static final RegistryObject<SimpleParticleType> RUNE =
            PARTICLE_TYPES.register("rune", () -> new SimpleParticleType(false));
}
```
In `PocketDimensionsMod`: `ModParticles.PARTICLE_TYPES.register(modBusGroup);`

- [ ] **Step 2: `RuneParticle`**

```java
package com.pocketdimensions.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** A glowing rune glyph that drifts slowly away from the World Anchor and fades out. */
public class RuneParticle extends SingleQuadParticle {

    RuneParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.xd = xd; this.yd = yd; this.zd = zd;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 1f;
        this.lifetime = 60 + this.random.nextInt(31);
        this.quadSize = 0.11f;
        this.rCol = 140 / 255f; this.gCol = 235 / 255f; this.bCol = 1f;
        this.setAlpha(0f);
    }

    @Override
    public void tick() {
        super.tick();
        float f = (float) this.age / this.lifetime;
        this.setAlpha(Math.min(1f, this.age / 6f) * (1f - f) * 0.85f);
    }

    @Override
    public int getLightColor(float partialTick) { return 0xF000F0; }   // full bright

    @Override
    protected SingleQuadParticle.Layer getLayer() { return SingleQuadParticle.Layer.TRANSLUCENT; }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new RuneParticle(level, x, y, z, xd, yd, zd, sprites.get(random));
        }
    }
}
```

- [ ] **Step 3: Register the provider** — in the `ClientSetup` constructor:

```java
RegisterParticleProvidersEvent.BUS.addListener(e ->
        e.registerSpriteSet(ModParticles.RUNE.get(), RuneParticle.Provider::new));
```
Imports: `net.minecraftforge.client.event.RegisterParticleProvidersEvent`, `com.pocketdimensions.init.ModParticles`, `com.pocketdimensions.client.particle.RuneParticle`.

- [ ] **Step 4: Spawn from `WorldAnchorBlock.animateTick`**

```java
/**
 * Client-only, called at random for blocks near the player (about 0.4 calls/s per block);
 * spawning two runes per call gives roughly one rune per second per linked anchor.
 */
@Override
public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    if (state.getValue(HALF) != DoubleBlockHalf.LOWER || !state.getValue(LINKED)) return;
    for (int n = 0; n < 2; n++) {
        int face = random.nextInt(4);
        double nx = face == 2 ? -1 : face == 3 ? 1 : 0, nz = face == 0 ? -1 : face == 1 ? 1 : 0;
        double along = (random.nextDouble() - 0.5) * 9 / 16.0;
        double y = random.nextBoolean() ? (18 + random.nextDouble() * 5) / 16.0 : (5 + random.nextDouble() * 5) / 16.0;
        double x = pos.getX() + 0.5 + nx * 6.8 / 16.0 + (nz != 0 ? along : 0);
        double z = pos.getZ() + 0.5 + nz * 6.8 / 16.0 + (nx != 0 ? along : 0);
        double out = (0.5 + random.nextDouble() * 0.5) / 16.0 / 20.0;       // 0.5–1 px per second
        double up = (1.2 + random.nextDouble() * 0.8) / 16.0 / 20.0;        // 1.2–2 px per second
        level.addParticle(ModParticles.RUNE.get(), x, pos.getY() + y, z, nx * out, up, nz * out);
    }
}
```
Imports: `net.minecraft.util.RandomSource`, `com.pocketdimensions.init.ModParticles`.

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Manual check (runClient)**
1. Next to a linked anchor, cyan rune glyphs drift slowly off the faces, roughly one per second, fading out over 3–4.5 s; they glow at night.
2. Options → Video → Particles: Minimal → no runes, no log errors.
3. Inert anchor → no runes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/pocketdimensions/init/ModParticles.java src/main/java/com/pocketdimensions/client/particle/RuneParticle.java src/main/java/com/pocketdimensions/PocketDimensionsMod.java src/main/java/com/pocketdimensions/client/ClientSetup.java src/main/java/com/pocketdimensions/block/WorldAnchorBlock.java
git commit -m "Add drifting rune particles to linked World Anchors"
```

---

### Task 6: Full in-game verification and docs

**Files:**
- Modify: `CLAUDE.md` (gitignored in this repo — edit only, do not commit; project tree: `tools/anchor/`, `ModParticles`, `WorldAnchorBlockEntityRenderer`, `RuneParticle`; note that World Anchor assets are generated by `node tools/anchor/build.mjs`)
- Modify: `PRD.md` (CP-004/CP-007 notes: World Anchor uses generated Runebound models)

- [ ] **Step 1: Regenerate and build from clean**

Run: `node --test tools/anchor/test/ && node tools/anchor/build.mjs && ./gradlew build`
Expected: tests pass, element report printed, `BUILD SUCCESSFUL`.

- [ ] **Step 2: In-game checklist (runClient)**
1. Inert anchor: monolith, claws, glowing empty socket, light 0, no particles, item icon shows the monolith.
2. Linked: runes glow with light flowing toward the core; black hole, disk, ring, beam, seed; rune particles ~1/s; light 10.
3. An anchor linked in a world from before this change looks linked after loading (Review Focus 1).
4. Let an Anchor Breaker complete: both halves gone, no leftover renderer or particles (Review Focus 3).
5. F3+T: still correct; log free of `world_anchor` model/texture warnings.

- [ ] **Step 3: Update docs** — add to the CLAUDE.md project tree:

```
tools/anchor/                         ← Node generator for World Anchor models/textures (run: node tools/anchor/build.mjs)
├── runebound.mjs                     ← design source of truth (shape + paint)
├── mesh.mjs / png.mjs / build.mjs
```
and under `client/`: `WorldAnchorBlockEntityRenderer.java ← black hole, rings, beam, seed`, `particle/RuneParticle.java ← drifting rune glyphs`; under `init/`: `ModParticles.java ← rune particle type`.

- [ ] **Step 4: Commit**

```bash
git add PRD.md
git commit -m "Note generated Runebound World Anchor assets in the PRD"
```
