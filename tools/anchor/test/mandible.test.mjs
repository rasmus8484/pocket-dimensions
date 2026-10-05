import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shape as breacherShape, paint as breacherPaint, influenceAmount, Y0, Y1 } from '../mandible.mjs';
import { shape as anchorShape } from '../runebound.mjs';
import { voxelize, buildModel } from '../mesh.mjs';

test('influence is zero without a breacher and full when the breach is complete', () => {
  for (let y = 0; y < 32; y++) {
    assert.equal(influenceAmount(y, 0), 0);
    assert.equal(influenceAmount(y, 4), 1);
  }
});

test('influence grows with breach level and toward the top', () => {
  for (let y = 0; y < 32; y++) for (let l = 1; l < 4; l++) assert.ok(influenceAmount(y, l + 1) >= influenceAmount(y, l));
  for (let l = 1; l <= 3; l++) for (let y = 1; y < 32; y++) assert.ok(influenceAmount(y, l) >= influenceAmount(y - 1, l));
  assert.ok(influenceAmount(30, 1) > 0.9 && influenceAmount(5, 1) < 0.1);
});

test('breacher never occupies an anchor voxel', () => {
  for (let y = Y0; y < Y1; y++) for (let z = 0; z < 16; z++) for (let x = 0; x < 16; x++) {
    if (breacherShape(x, y, z, false) !== null) assert.equal(anchorShape(x, y, z, true), null, `overlap at ${x},${y},${z}`);
  }
});

test('breacher models stay within the element budget and coordinate bounds', () => {
  for (const L of [false, true]) {
    const m = buildModel(voxelize(breacherShape, breacherPaint, L, Y0, Y1), 32, 'main');
    assert.ok(m.elements.length <= 200, `complete=${L}: ${m.elements.length} elements`);
    for (const el of m.elements) for (const v of [...el.from, ...el.to]) assert.ok(v >= -16 && v <= 32, `coord ${v}`);
  }
});
