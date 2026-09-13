import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const metadata = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf8'));
const modules = resolve(root, '../..');
// Installed as a package in the Host's one dependency tree, without nested DSH copies.
for (const [name, expected] of Object.entries({ ...metadata.peerDependencies, ...metadata.dependencies })) {
  const actual = JSON.parse(readFileSync(resolve(modules, name, 'package.json'), 'utf8')).version;
  if (actual !== expected) throw new Error(`Runtime mismatch: ${name} expected ${expected}, got ${actual}`);
}
console.log('Bridge runtime dependencies match the pinned Host dependency tree.');
