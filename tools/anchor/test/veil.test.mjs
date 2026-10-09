import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FRAMES, BAND_COLOURS, veilTexture } from '../veil.mjs';

const avg = rgba => {
  let s = 0;
  for (let i = 0; i < rgba.length; i += 4) s += rgba[i] + rgba[i + 1] + rgba[i + 2];
  return s / (rgba.length / 4) / 3;
};

test('each band is a 16 x 16 strip of animation frames, fully opaque', () => {
  for (let b = 0; b < 8; b++) {
    const rgba = veilTexture(b);
    assert.equal(rgba.length, 16 * 16 * FRAMES * 4);
    for (let i = 3; i < rgba.length; i += 4) assert.equal(rgba[i], 255, `band ${b} pixel ${(i - 3) / 4}`);
  }
});

test('eight bands, dark at the bottom, the realm sky colour at the top', () => {
  assert.equal(BAND_COLOURS.length, 8);
  assert.ok(avg(veilTexture(0)) < avg(veilTexture(2)), 'underground is darker than ground level');
  assert.deepEqual(BAND_COLOURS[7], [120, 167, 255], 'top band is the realm sky #78a7ff');
  assert.deepEqual(BAND_COLOURS[5], [192, 216, 255], 'a high band is the realm fog #c0d8ff');
});

test('the mist moves: consecutive frames differ', () => {
  const rgba = veilTexture(3), frame = 16 * 16 * 4;
  let diff = 0;
  for (let i = 0; i < frame; i++) diff += Math.abs(rgba[i] - rgba[frame + i]);
  assert.ok(diff > 0);
});

test('the texture tiles: the left and right columns are close in colour', () => {
  const rgba = veilTexture(3);
  for (let y = 0; y < 16; y++) {
    const l = (y * 16) * 4, r = (y * 16 + 15) * 4;
    assert.ok(Math.abs(rgba[l] - rgba[r]) < 40, `row ${y}`);
  }
});
