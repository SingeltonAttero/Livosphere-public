#!/usr/bin/env node
// Rasterize the checked-in launcher vectors without redrawing their artwork.
import { createRequire } from 'node:module';
import { readFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const sharp = require('sharp');
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const output = resolve(process.argv[2] ?? resolve(root, 'docs/release/store-assets/icon-512.png'));
const drawable = resolve(root, 'android/hub/app/src/main/res/drawable');

function paths(xml) {
  if (/<group\b|<clip-path\b/.test(xml)) throw new Error('Grouped vectors require an explicit exporter update');
  const vector = xml.match(/<vector\b[^>]*>/)?.[0] ?? '';
  if (!/android:viewportWidth="108"/.test(vector) || !/android:viewportHeight="108"/.test(vector)) {
    throw new Error('Expected a 108 by 108 launcher viewport');
  }
  return [...xml.matchAll(/<path\b[^>]*\/>/g)].map(([path]) => {
    const attribute = name => path.match(new RegExp(`android:${name}="([^"]*)"`))?.[1];
    const d = attribute('pathData');
    const fill = attribute('fillColor');
    if (!d || !/^#[0-9A-Fa-f]{6}$/.test(fill ?? '')) throw new Error('Unsupported launcher path');
    return `<path d="${d}" fill="${fill}" fill-rule="${attribute('fillType') === 'evenOdd' ? 'evenodd' : 'nonzero'}"/>`;
  }).join('');
}

const background = paths(await readFile(resolve(drawable, 'ic_launcher_background.xml'), 'utf8'));
const foreground = paths(await readFile(resolve(drawable, 'ic_launcher_foreground.xml'), 'utf8'));
await mkdir(dirname(output), { recursive: true });
await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">${background}${foreground}</svg>`))
  .resize(512, 512).flatten({ background: '#F6F2EC' }).png({ compressionLevel: 9 }).toFile(output);
const metadata = await sharp(output).metadata();
if (metadata.width !== 512 || metadata.height !== 512 || metadata.hasAlpha) throw new Error('Invalid store icon export');
process.stdout.write(`${output}\n`);
