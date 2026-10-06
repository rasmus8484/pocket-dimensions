import { test } from 'node:test';
import assert from 'node:assert/strict';
import { slab, polished, tablet, SLAB_W, SLAB_H } from '../gui.mjs';

const alpha = (img, x, y) => img.rgba[(y * img.w + x) * 4 + 3];
const lum = (img, x, y) => { const i = (y * img.w + x) * 4; return (img.rgba[i] + img.rgba[i + 1] + img.rgba[i + 2]) / 3; };
const spread = img => {          // how busy a texture is: mean difference between neighbouring pixels
  let s = 0, n = 0;
  for (let y = 0; y < img.h; y++) for (let x = 1; x < img.w; x++) if (alpha(img, x, y) && alpha(img, x - 1, y)) { s += Math.abs(lum(img, x, y) - lum(img, x - 1, y)); n++; }
  return s / n;
};

test('the slab is the size of the screen, with a broken outline and solid stone inside', () => {
  const img = slab();
  assert.equal(img.w, SLAB_W); assert.equal(img.h, SLAB_H);
  assert.equal(img.rgba.length, SLAB_W * SLAB_H * 4);
  assert.equal(alpha(img, SLAB_W >> 1, SLAB_H >> 1), 255);
  let bitten = 0;
  for (let x = 0; x < SLAB_W; x++) if (alpha(img, x, 0) === 0) bitten++;
  assert.ok(bitten > 20 && bitten < SLAB_W - 20, `top edge broken in ${bitten} columns`);
  for (let y = 7; y < SLAB_H - 7; y++) for (let x = 7; x < SLAB_W - 7; x++) assert.equal(alpha(img, x, y), 255, `hole at ${x},${y}`);
});

test('polished stone is much calmer than the weathered rock, so text reads on it', () => {
  const rough = spread(slab()), smooth = spread(polished());
  assert.ok(smooth * 2.5 < rough, `polished ${smooth.toFixed(2)} vs rock ${rough.toFixed(2)}`);
});

test('the warning tablet is cracked: red pixels run through it', () => {
  const img = tablet();
  let red = 0;
  for (let i = 0; i < img.rgba.length; i += 4) if (img.rgba[i] > 200 && img.rgba[i + 1] < 120 && img.rgba[i + 3] === 255) red++;
  assert.ok(red > 60, `red crack pixels ${red}`);
});
