import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';

const sourceRoot = path.resolve('src');
const files = [];

function collect(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) collect(target);
    else if (/\.tsx?$/.test(entry.name) && !/\.(test|spec)\.tsx?$/.test(entry.name)) files.push(target);
  }
}

function resolveImport(source, specifier) {
  if (!specifier.startsWith('.')) return null;
  const base = path.resolve(path.dirname(source), specifier);
  return [base, `${base}.ts`, `${base}.tsx`, path.join(base, 'index.ts'), path.join(base, 'index.tsx')]
    .find(candidate => fs.existsSync(candidate)) ?? base;
}

collect(sourceRoot);
const violations = [];
const canvasRoot = path.join(sourceRoot, 'modules', 'task', 'canvas');
const canvasFiles = new Set(files.filter(file => file.startsWith(`${canvasRoot}${path.sep}`)));
const canvasRuntimeImports = new Map([...canvasFiles].map(file => [file, new Set()]));

function isRuntimeReference(statement) {
  if (ts.isImportDeclaration(statement)) {
    const clause = statement.importClause;
    if (!clause) return true;
    if (clause.isTypeOnly) return false;
    if (clause.name) return true;
    if (clause.namedBindings && ts.isNamedImports(clause.namedBindings)) {
      return clause.namedBindings.elements.some(element => !element.isTypeOnly);
    }
    return Boolean(clause.namedBindings);
  }
  if (!ts.isExportDeclaration(statement) || statement.isTypeOnly) return false;
  if (!statement.exportClause || !ts.isNamedExports(statement.exportClause)) return true;
  return statement.exportClause.elements.some(element => !element.isTypeOnly);
}

for (const file of files) {
  const relative = path.relative(sourceRoot, file).split(path.sep);
  const sourceModule = relative[0] === 'modules' ? relative[1] : null;
  const source = ts.createSourceFile(file, fs.readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true);
  for (const statement of source.statements) {
    if (!ts.isImportDeclaration(statement) && !ts.isExportDeclaration(statement)) continue;
    const specifier = statement.moduleSpecifier?.text;
    if (typeof specifier !== 'string') continue;
    const resolved = resolveImport(file, specifier);
    if (!resolved) continue;
    if (canvasFiles.has(file) && canvasFiles.has(resolved) && isRuntimeReference(statement)) {
      canvasRuntimeImports.get(file).add(resolved);
    }
    const target = path.relative(sourceRoot, resolved).split(path.sep);
    if (relative[0] === 'shared' && (target[0] === 'modules' || target[0] === 'app')) {
      violations.push(`${relative.join('/')}: shared 不能依赖 ${target[0]}（${specifier}）`);
    }
    if (sourceModule && target[0] === 'modules' && target[1] !== sourceModule) {
      const targetModuleRoot = path.join(sourceRoot, 'modules', target[1]);
      const targetIndex = path.join(targetModuleRoot, 'index.ts');
      if (path.normalize(resolved) !== path.normalize(targetModuleRoot)
          && path.normalize(resolved) !== path.normalize(targetIndex)) {
        violations.push(`${relative.join('/')}: 跨模块只能通过 ${target[1]}/index.ts（${specifier}）`);
      }
    }
    if (sourceModule && target[0] === 'modules' && target[1] === sourceModule
        && path.basename(file) !== 'index.ts' && path.basename(resolved) === 'index.ts') {
      violations.push(`${relative.join('/')}: 模块内部不能通过自身 index.ts 回引（${specifier}）`);
    }
    if (path.basename(file) === 'index.ts' && sourceModule && /(^|\/)pages\//.test(specifier)) {
      violations.push(`${relative.join('/')}: 公开入口不能导出页面（${specifier}）`);
    }
  }
}

const canvasVisitState = new Map();
const canvasStack = [];
const reportedCanvasCycles = new Set();
const visitCanvasFile = (file) => {
  canvasVisitState.set(file, 'visiting');
  canvasStack.push(file);
  for (const dependency of canvasRuntimeImports.get(file) ?? []) {
    const state = canvasVisitState.get(dependency);
    if (state === 'visiting') {
      const start = canvasStack.indexOf(dependency);
      const cycle = [...canvasStack.slice(start), dependency]
        .map(item => path.relative(canvasRoot, item).split(path.sep).join('/'));
      const members = [...new Set(cycle.slice(0, -1))].sort().join('|');
      if (!reportedCanvasCycles.has(members)) {
        reportedCanvasCycles.add(members);
        violations.push(`modules/task/canvas: 生产运行时静态依赖存在循环（${cycle.join(' -> ')}）`);
      }
      continue;
    }
    if (state !== 'visited') visitCanvasFile(dependency);
  }
  canvasStack.pop();
  canvasVisitState.set(file, 'visited');
};
for (const file of canvasFiles) {
  if (!canvasVisitState.has(file)) visitCanvasFile(file);
}

if (violations.length > 0) {
  console.error(`前端模块边界检查失败（${violations.length} 项）：\n${violations.map(item => `- ${item}`).join('\n')}`);
  process.exitCode = 1;
} else {
  console.log(`前端模块边界检查通过（${files.length} 个生产 TypeScript 文件）`);
}
