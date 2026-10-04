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
