import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { shape, paint, GLYPHS, PAL, SC } from './runebound.mjs';
import { shape as breacherShape, paint as breacherPaint, influenceAmount, MAG, Y0 as B_Y0, Y1 as B_Y1 } from './mandible.mjs';
import { shape as breakerShape, paint as breakerPaint, Y0 as K_Y0, Y1 as K_Y1 } from './unmaker.mjs';
import { damageShape, damagePaint } from './damage.mjs';
import { shape as coreShape, paint as corePaint, STATES as CORE_STATES, CORE as CORE_C } from './geode.mjs';
import { voxelize, buildModel } from './mesh.mjs';
import { encodePNG } from './png.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/assets/pocketdimensions');
const write = (rel, data) => { const p = join(root, rel); mkdirSync(dirname(p), { recursive: true }); writeFileSync(p, data); };
const json = o => JSON.stringify(o, null, 2) + '\n';

const FLOW_FRAMES = 8;
// Flow texture: the flow pixels of the atlas, brightness wave moving toward the core (y coreY).
function flowStrip(model, voxels, coreY = SC) {
  const { size } = model.atlas, out = new Uint8Array(size * size * 4 * FLOW_FRAMES);
  for (const p of model.placed) if (p.b.cls === 'flow') {
    for (let r = 0; r < p.f.rows; r++) for (let c = 0; c < p.f.cols; c++) {
      const [x, y, z] = p.f.cell(c, r);
      const v = voxels.get(`${x},${y},${z}`);
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - coreY, z + 0.5 - 8);
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

/** Write one block model (+ atlas and optional flow texture) and return it. */
function emitModel(name, voxels, yShift, other, coreY = SC) {
    const m = buildModel(voxels, yShift, 'main', other);
    if (m.elements.length > 200) throw new Error(`${name}: ${m.elements.length} elements (max 200)`);
    report.push(`${name}: ${m.elements.length} elements, atlas ${m.atlas.size}px`);
    write(`textures/block/${name}.png`, encodePNG(m.atlas.size, m.atlas.size, m.atlas.rgba));
    const textures = { main: `pocketdimensions:block/${name}`, particle: `pocketdimensions:block/${name}` };
    if (m.elements.some(e => Object.values(e.faces).some(f => f.texture === '#flow'))) {
      const s = flowStrip(m, voxels, coreY);
      write(`textures/block/${name}_flow.png`, encodePNG(s.w, s.h, s.rgba));
      write(`textures/block/${name}_flow.png.mcmeta`, json({ animation: { frametime: 8, interpolate: true } }));
      textures.flow = `pocketdimensions:block/${name}_flow`;
    }
    write(`models/block/${name}.json`, json({ parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: m.elements }));
    return { m, textures };
}

// Breacher influence: glowing rune pixels drift from cyan toward magenta (keeping their brightness)
const mixC = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const influenced = level => (k, x, y, z, L, e) => {
  const r = paint(k, x, y, z, L, e);
  const amt = influenceAmount(y, level);
  if (!(r.g || r.fl) || amt === 0) return r;
  const lum = Math.max(...r.c) / 255;
  return { ...r, c: mixC(r.c, MAG.map(v => v * lum), amt) };
};

const anchorVariants = [['inert', false, 0], ['linked', true, 0], ...[1, 2, 3, 4].map(i => [`linked_i${i}`, true, i])];
for (const [state, L, level] of anchorVariants) {
  for (const [half, y0, y1] of [['lower', 0, 16], ['upper', 16, 32]]) {
    const name = `world_anchor_${state}_${half}`;
    const voxels = voxelize(shape, level ? influenced(level) : paint, L, y0, y1);
    const other = (x, y, z) => (y < y0 || y >= y1) && shape(x, y, z, L) !== null;
    halves[`${state}_${half}`] = emitModel(name, voxels, y0, other);
  }
}

// Anchor under an Anchor Breaker: cracks, heated runes and the sigil ring (damage 1..4)
for (let d = 1; d <= 4; d++) {
  const dShape = damageShape(d);
  for (const [half, y0, y1] of [['lower', 0, 16], ['upper', 16, 32]]) {
    const voxels = voxelize(dShape, damagePaint(d), true, y0, y1);
    const other = (x, y, z) => (y < y0 || y >= y1) && dShape(x, y, z, true) !== null;
    halves[`linked_d${d}_${half}`] = emitModel(`world_anchor_linked_d${d}_${half}`, voxels, y0, other);
  }
}

// Breaker (Unmaker): block-local y = world y - 32; coils fill with charge 0..4
const breaker = [];
for (let c = 0; c <= 4; c++) {
  const voxels = voxelize(breakerShape, breakerPaint, c, K_Y0, K_Y1);
  const other = (x, y, z) => (y < 32 && shape(x, y, z, true) !== null);
  breaker.push(emitModel(`anchor_breaker_c${c}`, voxels, 32, other));
}

// Breacher (Mandible): block-local y = world y - 32; faces against the anchor are culled
const breacher = {};
for (const [state, complete] of [['breaching', false], ['complete', true]]) {
  const voxels = voxelize(breacherShape, breacherPaint, complete, B_Y0, B_Y1);
  const other = (x, y, z) => (y < 32 && shape(x, y, z, true) !== null);
  breacher[state] = emitModel(`world_breacher_${state}`, voxels, 32, other);
}

// World Core (Geode Heart): one model pair per siege state (0 normal, 1 breaching, 2 breaking, 3 anchor lost)
const CORE_ORDER = ['normal', 'breaching', 'breaking', 'lost'];
const core = {};
for (const st of CORE_ORDER) for (const [half, y0, y1] of [['lower', 0, 16], ['upper', 16, 32]]) {
  const voxels = voxelize(coreShape, corePaint, st, y0, y1);
  const other = (x, y, z) => (y < y0 || y >= y1) && coreShape(x, y, z, st) !== null;
  core[`${st}_${half}`] = emitModel(`world_core_${st}_${half}`, voxels, y0, other, CORE_C[1]);
}

// Blockstates
const anchorVariantsJson = {};
for (const half of ['lower', 'upper']) for (let dmg = 0; dmg <= 4; dmg++) for (let inf = 0; inf <= 4; inf++) for (const linked of [false, true]) {
  const model = !linked ? `world_anchor_inert_${half}`
    : dmg > 0 ? `world_anchor_linked_d${dmg}_${half}`
    : inf > 0 ? `world_anchor_linked_i${inf}_${half}` : `world_anchor_linked_${half}`;
  anchorVariantsJson[`damage=${dmg},half=${half},influence=${inf},linked=${linked}`] = { model: `pocketdimensions:block/${model}` };
}
write('blockstates/world_anchor.json', json({ variants: anchorVariantsJson }));
write('blockstates/world_breacher.json', json({ variants: {
  'complete=false': { model: 'pocketdimensions:block/world_breacher_breaching' },
  'complete=true': { model: 'pocketdimensions:block/world_breacher_complete' },
} }));
write('blockstates/world_core.json', json({ variants: Object.fromEntries(['lower', 'upper'].flatMap(half =>
  CORE_ORDER.map((st, i) => [`half=${half},siege=${i}`, { model: `pocketdimensions:block/world_core_${st}_${half}` }]))) }));
write('blockstates/anchor_breaker.json', json({ variants: Object.fromEntries(
  [0, 1, 2, 3, 4].map(c => [`charge=${c}`, { model: `pocketdimensions:block/anchor_breaker_c${c}` }])) }));

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
const runeSprites = { textures: GLYPHS.map((_, i) => `pocketdimensions:rune_${i}`) };
for (const n of ['rune', 'rune_pink', 'rune_gold', 'rune_red']) write(`particles/${n}.json`, json(runeSprites));
for (const n of ['drain', 'unmake', 'siphon']) write(`particles/${n}.json`, json({ textures: ['minecraft:glow'] }));

// Breacher item model: the breaching state, scaled into the slot
write('models/item/world_breacher.json', json({
  parent: 'minecraft:block/block',
  textures: { main: breacher.breaching.textures.main, particle: breacher.breaching.textures.main, ...(breacher.breaching.textures.flow ? { flow: breacher.breaching.textures.flow } : {}) },
  elements: breacher.breaching.m.elements,
  display: {
    gui: { rotation: [30, 225, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
    ground: { translation: [0, 3, 0], scale: [0.3, 0.3, 0.3] },
    fixed: { translation: [0, 2, 0], scale: [0.55, 0.55, 0.55] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.35, 0.35, 0.35] },
    firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
    firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
  },
}));

// World Core item model: the living core, both halves in one model
{
  const lo = core.normal_lower, up = core.normal_upper;
  write('models/item/world_core.json', json({
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
}

// Breaker item model: half-charged, scaled into the slot
const bi = breaker[2];
write('models/item/anchor_breaker.json', json({
  parent: 'minecraft:block/block',
  textures: { main: bi.textures.main, particle: bi.textures.main, ...(bi.textures.flow ? { flow: bi.textures.flow } : {}) },
  elements: bi.m.elements,
  display: {
    gui: { rotation: [30, 225, 0], translation: [0, 3, 0], scale: [0.45, 0.45, 0.45] },
    ground: { translation: [0, 3, 0], scale: [0.3, 0.3, 0.3] },
    fixed: { translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.35, 0.35, 0.35] },
    firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
    firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
  },
}));

console.log(report.join('\n'));
