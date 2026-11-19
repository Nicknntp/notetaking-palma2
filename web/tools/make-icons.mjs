// Generates icon-180/192/512.png (no dependencies). Run: node tools/make-icons.mjs
import zlib from 'node:zlib';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => {
  const t = Buffer.from(type), len = Buffer.alloc(4), c = Buffer.alloc(4);
  len.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, c]);
};

function png(size) {
  const raw = Buffer.alloc((size * 3 + 1) * size);
  const inRect = (x, y, x0, y0, x1, y1) => x >= x0 * size && x < x1 * size && y >= y0 * size && y < y1 * size;
  for (let y = 0; y < size; y++) {
    raw[y * (size * 3 + 1)] = 0;
    for (let x = 0; x < size; x++) {
      let v = 17; // dark background
      if (inRect(x, y, 0.266, 0.1875, 0.734, 0.8125)) { // white page
        v = 255;
        for (const [a, b, w] of [[0.305, 0.34, 0.3125], [0.42, 0.455, 0.3125], [0.535, 0.57, 0.3125], [0.65, 0.685, 0.195]]) if (inRect(x, y, 0.344, a, 0.344 + w, b)) v = 17;
      }
      const o = y * (size * 3 + 1) + 1 + x * 3;
      raw[o] = raw[o + 1] = raw[o + 2] = v;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

const out = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public');
for (const s of [180, 192, 512]) fs.writeFileSync(path.join(out, `icon-${s}.png`), png(s));
console.log('icons written');
