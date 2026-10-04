import { test } from 'node:test';
import assert from 'node:assert/strict';
import { inflateSync } from 'node:zlib';
import { encodePNG } from '../png.mjs';

test('encodes a 2x1 RGBA image with valid signature and data', () => {
  const buf = encodePNG(2, 1, Uint8Array.from([255, 0, 0, 255, 0, 255, 0, 128]));
  assert.deepEqual([...buf.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  assert.equal(buf.readUInt32BE(16), 2);          // IHDR width
  assert.equal(buf.readUInt32BE(20), 1);          // IHDR height
  const idatLen = buf.readUInt32BE(33);
  const raw = inflateSync(buf.subarray(41, 41 + idatLen));
  assert.deepEqual([...raw], [0, 255, 0, 0, 255, 0, 255, 0, 128]);  // filter byte + pixels
});
