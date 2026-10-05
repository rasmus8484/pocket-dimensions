import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shape, paint, STATES, FALL } from '../geode.mjs';
import { voxelize, buildModel } from '../mesh.mjs';

const HALVES = [[0, 16], [16, 32]];
const model = (st, y0, y1) => {
  const other = (x, y, z) => (y < y0 || y >= y1) && shape(x, y, z, st) !== null;
  return buildModel(voxelize(shape, paint, st, y0, y1), y0, 'main', other);
};

test('every siege state model stays within the element budget and coordinate bounds', () => {
  for (const st of Object.keys(STATES)) for (const [y0, y1] of HALVES) {
    const m = model(st, y0, y1);
    assert.ok(m.elements.length <= 200, `${st} y${y0}: ${m.elements.length}`);
    for (const el of m.elements) for (const v of [...el.from, ...el.to]) assert.ok(v >= -16 && v <= 32, `coord ${v}`);
  }
});

test('the floating boulder never touches the ground; once lost it has fallen by FALL px', () => {
  const lowest = st => { for (let y = 0; y < 32; y++) for (let z = 0; z < 16; z++) for (let x = 0; x < 16; x++) if (shape(x, y, z, st) !== null && shape(x, y, z, st) !== 'deadShard') return y; };
  assert.ok(lowest('normal') >= 4);
  assert.equal(lowest('lost'), lowest('normal') - FALL);
});

test('nothing glows once the anchor is lost', () => {
  for (const [y0, y1] of HALVES) for (const v of voxelize(shape, paint, 'lost', y0, y1).values()) assert.equal(v.cls, 'solid');
});

test('each rune tablet carries glowing glyph pixels while the core lives', () => {
  const glyphs = [...voxelize(shape, paint, 'normal', 0, 16).values()].filter(v => v.cls === 'glow' && v.y >= 6 && v.y <= 10
    && (v.x <= 3 || v.x >= 12 || v.z <= 3 || v.z >= 12));
  assert.ok(glyphs.length >= 4 * 8, `glyph pixels ${glyphs.length}`);
});

test('the shaft is open from the crown to the underside', () => {
  for (let y = 4; y < 32; y++) for (const [x, z] of [[7, 7], [8, 8], [7, 8], [8, 7]]) assert.equal(shape(x, y, z, 'normal'), null, `shaft blocked at y${y}`);
});
