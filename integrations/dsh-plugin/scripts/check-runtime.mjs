import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const metadata = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf8'));
const modules = resolve(root, '../..');
const dshVersion = '0.1.5-rc.1';
const presetPackages = [
  'dsh-agent-instructions', 'dsh-terminal', 'dsh-terminal-bash',
  'dsh-tool-bash-persistent', 'dsh-tool-pwsh-persistent', 'dsh-tool-fs',
  'dsh-tool-fs-search', 'dsh-tool-str-replace-editor', 'dsh-tool-jobs',
  'dsh-skill-filesystem', 'dsh-tool-skill', 'dsh-command-goal', 'dsh-tool-goal',
  'dsh-plan-mode', 'dsh-command-compact', 'dsh-compaction-tool-result-pruner',
  'dsh-tool-subagent-control', 'dsh-tool-subagent', 'dsh-workflow-worker-thread',
  'dsh-tool-workflow', 'dsh-tool-ralph', 'dsh-tool-todo', 'dsh-tool-web',
  'dsh-tool-cordis', 'dsh-tool-present', 'dsh-code-runtime-worker-thread',
];
// Installed as a package in the Host's one dependency tree, without nested DSH copies.
for (const [name, expected] of Object.entries({ ...metadata.peerDependencies, ...metadata.dependencies })) {
  const actual = JSON.parse(readFileSync(resolve(modules, name, 'package.json'), 'utf8')).version;
  if (actual !== expected) throw new Error(`Runtime mismatch: ${name} expected ${expected}, got ${actual}`);
}
for (const name of presetPackages) {
  const packageName = `@deepseek-ai/${name}`;
  const actual = JSON.parse(readFileSync(resolve(modules, packageName, 'package.json'), 'utf8')).version;
  if (actual !== dshVersion) throw new Error(`Runtime mismatch: ${packageName} expected ${dshVersion}, got ${actual}`);
}
console.log('Bridge runtime dependencies match the pinned Host dependency tree.');
