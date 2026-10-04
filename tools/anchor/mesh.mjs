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
