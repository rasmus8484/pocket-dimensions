import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shape as breakerShape, paint as breakerPaint, Y0, Y1 } from '../unmaker.mjs';
import { CRACK_LINES, damageShape, damagePaint } from '../damage.mjs';
import { shape as anchorShape, paint as anchorPaint } from '../runebound.mjs';
import { voxelize, buildModel } from '../mesh.mjs';

test('every crack appears between 0 and 1 and grows outward from its start', () => {
  assert.ok(CRACK_LINES.length > 0);
  for (const line of CRACK_LINES) {
    for (const c of line) assert.ok(c.birth > 0 && c.birth <= 1, `birth ${c.birth}`);
    for (let i = 1; i < line.length; i++) assert.ok(line[i].birth >= line[i - 1].birth);
  }
});

test('damage level 0 leaves the linked anchor unchanged', () => {
  const e = { nx: false, px: false, ny: false, py: false, nz: true, pz: false };
  for (const [x, y, z] of [[5, 20, 2], [3, 6, 2], [8, 30, 6], [2, 12, 2]]) {
    const k = anchorShape(x, y, z, true);
    if (!k) continue;
    assert.deepEqual(damagePaint(0)(k, x, y, z, true, e), anchorPaint(k, x, y, z, true, e));
  }
  for (let y = 0; y < 32; y++) assert.equal(damageShape(0)(8, y, 0, true), anchorShape(8, y, 0, true));
});

test('the sigil ring only appears once damage starts', () => {
  assert.equal(damageShape(0)(8, 4, 0, true), null);
  assert.equal(damageShape(1)(8, 4, 0, true), 'sigil');
});

test('breaker never occupies an anchor voxel', () => {
  for (let y = Y0; y < Y1; y++) for (let z = 0; z < 16; z++) for (let x = 0; x < 16; x++)
    if (breakerShape(x, y, z, 0) !== null) assert.equal(anchorShape(x, y, z, true), null, `overlap ${x},${y},${z}`);
});

test('breaker models stay within budget and bounds at every charge level', () => {
  for (let c = 0; c <= 4; c++) {
    const m = buildModel(voxelize(breakerShape, breakerPaint, c, Y0, Y1), 32, 'main');
    assert.ok(m.elements.length <= 200, `charge ${c}: ${m.elements.length}`);
    for (const el of m.elements) for (const v of [...el.from, ...el.to]) assert.ok(v >= -16 && v <= 32, `coord ${v}`);
  }
});

test('damaged anchor halves stay within the element budget', () => {
  for (let d = 1; d <= 4; d++) for (const [y0, y1] of [[0, 16], [16, 32]]) {
    const s = damageShape(d);
    const other = (x, y, z) => (y < y0 || y >= y1) && s(x, y, z, true) !== null;
    const m = buildModel(voxelize(s, damagePaint(d), true, y0, y1), y0, 'main', other);
    assert.ok(m.elements.length <= 200, `damage ${d} y${y0}: ${m.elements.length}`);
  }
});
