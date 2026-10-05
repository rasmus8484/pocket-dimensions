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

test('top and bottom each carry a glowing rune drawn on the plate, and different runes', () => {
  const vox = voxelize(shape, paint, null, 0, 16);
  const glowAt = y => [...vox.values()].filter(v => v.cls === 'glow' && v.y === y).map(v => `${v.x},${v.z}`).sort();
  const top = glowAt(HI - 1), bottom = glowAt(LO + 1);
  assert.ok(top.length >= 8 && bottom.length >= 8, `top ${top.length} bottom ${bottom.length}`);
  assert.notDeepEqual(top, bottom);
});

test('each rune has a soft glow sprite: full under the glyph, fading out a pixel or two around it', async () => {
  const { glowSprite } = await import('../pocket.mjs');
  const { GLYPHS } = await import('../runebound.mjs');
  for (const g of GLYPHS) {
    const a = glowSprite(g), at = (c, r) => a[(r * 8 + c) * 4 + 3];
    g.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === 'X') assert.ok(at(c + 2, r + 1) >= 200, 'bright under the glyph'); }));
    let halo = 0;
    for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) {
      const inGlyph = r >= 1 && r <= 5 && c >= 2 && c <= 5 && g[r - 1][c - 2] === 'X';
      if (!inGlyph && at(c, r) > 0) { halo++; assert.ok(at(c, r) < 200, 'the halo is fainter than the glyph'); }
    }
    assert.ok(halo >= 10, `halo pixels ${halo}`);
  }
});

test('rune glow only shows on the outside of its plate: stone on every other side', () => {
  for (const [x, y, z] of ALL) if (shape(x, y, z) === 'glyph') {
    const out = y > 8 ? 1 : -1;                                   // the plate side, where the carving opens
    for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, -out, 0]]) {
      assert.notEqual(shape(x + dx, y + dy, z + dz), null, `glyph ${x},${y},${z} open towards ${dx},${dy},${dz}`);
    }
  }
});
