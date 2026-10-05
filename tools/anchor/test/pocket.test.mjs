import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shape, paint, LO, HI } from '../pocket.mjs';
import { voxelize, buildModel } from '../mesh.mjs';

const ALL = [];
for (let y = 0; y < 16; y++) for (let z = 0; z < 16; z++) for (let x = 0; x < 16; x++) ALL.push([x, y, z]);

test('the cube fits a 10 px box centred in the block, and stays within the element budget', () => {
  for (const [x, y, z] of ALL) if (shape(x, y, z) !== null) {
    for (const v of [x, y, z]) assert.ok(v >= LO && v <= HI, `voxel ${x},${y},${z} outside ${LO}..${HI}`);
  }
  assert.equal(LO + HI, 15, 'centred on 8');
  const m = buildModel(voxelize(shape, paint, null, 0, 16), 0, 'main');
  assert.ok(m.elements.length <= 200, `${m.elements.length} elements`);
});

test('the four sides are identical: a quarter turn about the vertical axis maps the cube onto itself', () => {
  for (const [x, y, z] of ALL) {
    const k = shape(x, y, z);
    if (k === 'glyph' || k === 'cap') continue;                 // runes on top and bottom are the one asymmetry
    const r = shape(15 - z, y, x);
    if (r === 'glyph' || r === 'cap') continue;
    assert.equal(k, r, `${x},${y},${z}`);
  }
});

test('each side is an open window onto the portal, framed a pixel deeper than the edges', () => {
  for (let y = 7; y <= 8; y++) for (let x = 7; x <= 8; x++) assert.equal(shape(x, y, LO + 1), null, 'window open');
  assert.equal(shape(8, 8, LO + 2), null, 'window open behind the frame plane');
  assert.equal(shape(LO + 2, 8, LO + 2), 'ring', 'frame set back');
  assert.equal(shape(8, 8, LO), null, 'no outer skin over the window');
});

test('the corners carry proud gold knobs with their tips bevelled off', () => {
  assert.equal(shape(LO, LO, LO), null);
  assert.equal(shape(LO + 1, LO, LO), 'knob');
  assert.equal(shape(LO + 1, LO + 1, LO + 1), 'knob');
});

test('top and bottom each carry a carved rune that glows one pixel down, and different runes', () => {
  const vox = voxelize(shape, paint, null, 0, 16);
  const glowAt = y => [...vox.values()].filter(v => v.cls === 'glow' && v.y === y).map(v => `${v.x},${v.z}`).sort();
  const top = glowAt(HI - 2), bottom = glowAt(LO + 2);
  assert.ok(top.length >= 8 && bottom.length >= 8, `top ${top.length} bottom ${bottom.length}`);
  assert.notDeepEqual(top, bottom);
  for (const p of top) { const [x, z] = p.split(',').map(Number); assert.equal(shape(x, HI - 1, z), null, 'carved through the plate'); }
});
