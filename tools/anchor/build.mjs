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
