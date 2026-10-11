import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FRAMES, FRAMETIME, LOOP_SECONDS, VEINS, STONE_GREY, TEAL, accents, frame, strip, DEEPSLATE_GREY, SLATE, slateFrame, slateStrip, auroraAt } from '../dais.mjs';

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

test('the aurora is mainly teal: most of the vein, most of the time, shows no accent', () => {
  let n = 0, teal = 0;
  for (let k = 0; k < FRAMES; k++) for (let i = 0; i < 256; i++) if (VEINS.mask[i] === 1) {
    n++; if (accents(i % 16, i >> 4, k * LOOP_SECONDS / FRAMES).reduce((a, b) => a + b) < 0.2) teal++;
  }
  assert.ok(teal / n >= 0.6, `teal share ${(teal / n).toFixed(2)}`);
  assert.ok(TEAL.every(c => c[1] > c[0] && c[2] > c[0]), 'teal: more green and blue than red');
});

test('swatches of pink, blue and purple each show strongly somewhere in the loop', () => {
  const peak = [0, 0, 0];
  for (let k = 0; k < FRAMES; k++) for (let i = 0; i < 256; i++) if (VEINS.mask[i] === 1)
    accents(i % 16, i >> 4, k * LOOP_SECONDS / FRAMES).forEach((w, c) => { peak[c] = Math.max(peak[c], w); });
  peak.forEach((p, c) => assert.ok(p > 0.6, `accent ${c} peaks at ${p.toFixed(2)}`));
});

test('the swatches tile across blocks and loop in time', () => {
  for (const [x, y, t] of [[3, 5, 1.5], [0, 15, 20], [11, 2, 31]]) {
    assert.deepEqual(accents(x + 16, y, t).map(v => v.toFixed(9)), accents(x, y, t).map(v => v.toFixed(9)));
    assert.deepEqual(accents(x, y + 16, t).map(v => v.toFixed(9)), accents(x, y, t).map(v => v.toFixed(9)));
    assert.deepEqual(accents(x, y, t + LOOP_SECONDS).map(v => v.toFixed(9)), accents(x, y, t).map(v => v.toFixed(9)));
  }
});

test('aurora slate: about one pixel in twenty is a fleck of the aurora, a few more a dim halo, the rest clear', () => {
  assert.deepEqual(DEEPSLATE_GREY, [80, 80, 83], "vanilla deepslate's average grey");
  const flecks = SLATE.mask.filter(v => v === 1).length, halo = SLATE.mask.filter(v => v === 0.5).length;
  assert.ok(flecks >= 256 * 0.03 && flecks <= 256 * 0.08, `flecks ${flecks}`);
  assert.ok(halo > 0 && halo <= flecks * 1.5, `halo ${halo}`);
  const f = slateFrame(0);
  for (let i = 0; i < 256; i++) assert.equal(f[i * 4 + 3], SLATE.mask[i] ? 255 : 0, `pixel ${i}`);
});

test("the flecks shift with the veins: each is the aurora's colour at that moment, twinkling", () => {
  for (const t of [0, 5.5, 13, 27.25]) {
    const f = slateFrame(t);
    for (let i = 0; i < 256; i++) {
      if (SLATE.mask[i] !== 1) continue;
      const x = i % 16, y = i >> 4, au = auroraAt(x, y, ((x * 0.37 + y * 0.21) % 1 + 1) % 1, t), tw = SLATE.twinkle(i, t);
      for (let ch = 0; ch < 3; ch++) assert.ok(Math.abs(f[i * 4 + ch] - Math.min(255, Math.round(au[ch] * (0.75 + 0.35 * tw)))) <= 1, `pixel ${i} at ${t}`);
    }
  }
  assert.notDeepEqual(slateFrame(0), slateFrame(8), 'the colours move on');
});

test('the slate strip loops on the same 32 s as the veins: 128 frames', () => {
  assert.deepEqual(slateFrame(LOOP_SECONDS), slateFrame(0));
  assert.equal(slateStrip().length, 16 * 16 * 4 * FRAMES);
});

