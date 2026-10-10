import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FRAMES, COLOUR, veilTexture } from '../veil.mjs';

test('each band is one still 16 x 16 frame, fully opaque', () => {
  assert.equal(FRAMES, 1);
  for (let b = 0; b < 8; b++) {
    const rgba = veilTexture(b);
    assert.equal(rgba.length, 16 * 16 * 4);
    for (let i = 3; i < rgba.length; i += 4) assert.equal(rgba[i], 255, `band ${b} pixel ${(i - 3) / 4}`);
  }
});

test('one colour all the way up: every band is the same texture', () => {
  assert.deepEqual(COLOUR, [182, 200, 236], 'the pale ground-level mist #b6c8ec');
  for (let b = 1; b < 8; b++) assert.deepEqual(veilTexture(b), veilTexture(0), `band ${b}`);
});

test('plain: every pixel stays within a few percent of the band colour (no glints, no drifting mist)', () => {
  for (let b = 0; b < 8; b++) {
    const rgba = veilTexture(b), base = COLOUR;
    for (let i = 0; i < rgba.length; i += 4)
      for (let c = 0; c < 3; c++) assert.ok(Math.abs(rgba[i + c] - base[c]) <= Math.ceil(base[c] * 0.06) + 1, `band ${b} pixel ${i / 4}`);
  }
});

test('the texture tiles: the left and right columns are close in colour', () => {
  const rgba = veilTexture(3);
  for (let y = 0; y < 16; y++) {
    const l = (y * 16) * 4, r = (y * 16 + 15) * 4;
    assert.ok(Math.abs(rgba[l] - rgba[r]) < 12, `row ${y}`);
  }
});
