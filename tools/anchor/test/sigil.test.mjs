import { test } from 'node:test';
import assert from 'node:assert/strict';
import { lineTexture } from '../sigil.mjs';

test('the line is white, brightest along its middle and fading to nothing at its sides', () => {
  const rgba = lineTexture();
  assert.equal(rgba.length, 16 * 16 * 4);
  const alpha = y => rgba[(y * 16 + 8) * 4 + 3];
  assert.ok(alpha(8) > 200, 'middle');
  assert.ok(alpha(0) < 20 && alpha(15) < 20, 'sides');
  assert.ok(alpha(4) < alpha(7), 'fades outward');
  for (let i = 0; i < rgba.length; i += 4) assert.equal(rgba[i], 255);
});
