import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FRAMES, FRAMETIME, LOOP_SECONDS, VEINS, STONE_GREY, frame, strip } from '../dais.mjs';

const at = (x, y) => VEINS.mask[((y + 16) % 16) * 16 + ((x + 16) % 16)];

test('the rim is mixed into vanilla stone grey (126, its average), not a rock of our own', () => {
  assert.deepEqual(STONE_GREY, [126, 126, 126]);
  const r = frame('rim', 0);
  for (let i = 0; i < 256; i++) if (VEINS.mask[i] === 0.5)
    for (let ch = 0; ch < 3; ch++) assert.ok(Math.abs(r[i * 4 + ch] - 126) < 90, `rim pixel ${i} stays near grey`);
});

test('the veins are thin, like crying obsidian: a few dozen pixels, never a 2 x 2 clump', () => {
  const veins = VEINS.mask.filter(v => v === 1).length;
  assert.ok(veins >= 30 && veins <= 60, `vein pixels ${veins}`);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++)
    assert.ok(!(at(x, y) === 1 && at(x + 1, y) === 1 && at(x, y + 1) === 1 && at(x + 1, y + 1) === 1), `clump at ${x},${y}`);
});

test('the veins run on across the tile edges, so neighbouring blocks join', () => {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    if (at(x, y) !== 1) continue;
    let n = 0;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) if ((dx || dy) && at(x + dx, y + dy) === 1) n++;
    assert.ok(n > 0, `lone vein pixel at ${x},${y}`);
  }
  const edge = f => [...Array(16).keys()].some(f);
  assert.ok(edge(i => at(0, i) === 1) && edge(i => at(15, i) === 1), 'veins reach the left and right edges');
  assert.ok(edge(i => at(i, 0) === 1) && edge(i => at(i, 15) === 1), 'veins reach the top and bottom edges');
});

test('the rim lies only beside a vein', () => {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++)
    if (at(x, y) === 0.5) assert.ok([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => at(x + dx, y + dy) === 1), `${x},${y}`);
});

test('the two overlays never share a pixel and are transparent elsewhere', () => {
  for (const t of [0, 3.25, 17]) {
    const v = frame('veins', t), r = frame('rim', t);
    for (let i = 0; i < 256; i++) {
      assert.equal(v[i * 4 + 3], VEINS.mask[i] === 1 ? 255 : 0, `veins pixel ${i}`);
      assert.equal(r[i * 4 + 3], VEINS.mask[i] === 0.5 ? 255 : 0, `rim pixel ${i}`);
    }
  }
});

test('the strip loops: 128 frames of 5 ticks make 32 s, and the frame after the last is the first again', () => {
  assert.equal(FRAMES, 128);
  assert.equal(FRAMETIME, 5);
  assert.equal(LOOP_SECONDS, 32);
  for (const layer of ['veins', 'rim']) {
    assert.deepEqual(frame(layer, LOOP_SECONDS), frame(layer, 0), layer);
    assert.equal(strip(layer).length, 16 * 16 * 4 * FRAMES, layer);
  }
});

test('the power flows: the veins change from one moment to the next', () => {
  assert.notDeepEqual(frame('veins', 0), frame('veins', 0.5));
});
