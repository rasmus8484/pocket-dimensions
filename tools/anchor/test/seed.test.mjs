import { test } from 'node:test';
import assert from 'node:assert/strict';
import { starseed, sprite } from '../seed.mjs';

const at = (x, y) => starseed(x, y);

test('the sprite is a 16 x 16 gem on a transparent background', () => {
  const rgba = sprite();
  assert.equal(rgba.length, 16 * 16 * 4);
  assert.equal(rgba[3], 0, 'corner is transparent');
  assert.equal(rgba[(8 * 16 + 8) * 4 + 3], 255, 'centre is solid');
});

test('the gem outline is symmetrical left to right', () => {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 8; x++) assert.equal(at(x, y) === null, at(15 - x, y) === null, `${x},${y}`);
});

test('it has a dark core of night with a few stars, inside a rim', () => {
  let night = 0, stars = 0;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const c = at(x, y);
    if (!c) continue;
    if (Math.max(...c) < 60) night++;
    if (c[2] > 200 && c[0] < 120) stars++;
  }
  assert.ok(night >= 12, `night pixels ${night}`);
  assert.ok(stars >= 1 && stars <= 6, `blue stars ${stars}`);
  const top = [...Array(16).keys()].map(x => at(x, 0)).find(Boolean);
  assert.ok(top && Math.max(...top) < 80, 'the outline is the dark rim');
});
