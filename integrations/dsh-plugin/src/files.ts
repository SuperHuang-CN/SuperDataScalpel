import { constants } from 'node:fs';
import { lstat, mkdir, open, readdir, realpath } from 'node:fs/promises';
import { join, isAbsolute } from 'node:path';
import type { Context } from '@deepseek-ai/cordis';
import { z } from 'zod';
import { BridgeError, Serial, fail } from './contracts.js';

/** Controlled tool boundary, not a sandbox for arbitrary processes or native operators. */
export class WorkspaceFiles {
  private serial = new Serial();
  constructor(readonly root: string) {}
  private async resolve(path: string, create = false) {
    if (isAbsolute(path) || path.includes('\\') || path.includes('\0') || path.split('/').some(p => ['..', '.'].includes(p)))
      fail(400, 'BRIDGE_FILE_PATH_REJECTED', '只允许个人工作区内的相对路径。');
    await mkdir(this.root, { recursive: true });
    if (await realpath(this.root) !== this.root) fail(403, 'BRIDGE_FILE_PATH_REJECTED', '工作区不能是符号链接。');
    const parts = path.split('/').filter(Boolean);
    let current = this.root;
    for (const [i, part] of parts.entries()) {
      current = join(current, part);
      let info;
      try { info = await lstat(current); }
      catch (e) {
        if ((e as NodeJS.ErrnoException).code !== 'ENOENT' || !create) throw e;
        if (i < parts.length - 1) await mkdir(current);
        continue;
      }
      if (info.isSymbolicLink() || !info.isDirectory() && (!info.isFile() || info.nlink !== 1))
        fail(403, 'BRIDGE_FILE_PATH_REJECTED', '不支持链接或特殊文件。');
    }
    return current;
  }
  private async guard<T>(fn: () => Promise<T>) {
    try { return await fn(); } catch (e) {
      if (e instanceof BridgeError) throw e;
      if ((e as NodeJS.ErrnoException).code === 'ENOENT') fail(404, 'BRIDGE_FILE_NOT_FOUND', '文件不存在。');
      fail(400, 'BRIDGE_FILE_UNAVAILABLE', '无法处理此工作区文件。');
    }
  }
  read(path: string) { return this.serial.run(() => this.guard(async () => {
    const target = await this.resolve(path), info = await lstat(target);
    if (info.isDirectory()) {
      const names = await readdir(target, { withFileTypes: true });
      if (names.length > 200) fail(413, 'BRIDGE_FILE_LIMIT', '目录超过 200 项，请使用更小的子目录。');
      return { kind: 'directory', path, entries: names.filter(e => e.isDirectory() || e.isFile()).map(e => ({
        name: e.name, path: [path, e.name].filter(Boolean).join('/'), kind: e.isDirectory() ? 'directory' : 'file',
      })).sort((a, b) => a.name.localeCompare(b.name)) };
    }
    const handle = await open(target, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
    try {
      const stat = await handle.stat();
      if (!stat.isFile() || stat.nlink !== 1) fail(403, 'BRIDGE_FILE_PATH_REJECTED', '不支持此文件类型。');
      const buffer = Buffer.alloc(131073), { bytesRead } = await handle.read(buffer, 0, buffer.length, 0);
      if (bytesRead > 131072) fail(413, 'BRIDGE_FILE_LIMIT', '文本文件上限为 128 KiB。');
      const text = new TextDecoder('utf-8', { fatal: true }).decode(buffer.subarray(0, bytesRead));
      if (text.includes('\0')) fail(415, 'BRIDGE_FILE_TYPE_REJECTED', '只支持 UTF-8 文本。');
      return { kind: 'file', path, text };
    } finally { await handle.close(); }
  })); }
  write(path: string, text: string) { return this.serial.run(() => this.guard(async () => {
    if (!path || Buffer.byteLength(text) > 131072 || text.includes('\0')) fail(400, 'BRIDGE_FILE_LIMIT', '需要相对文件路径及不超过 128 KiB 的文本。');
    const target = await this.resolve(path, true);
    const handle = await open(target, constants.O_WRONLY | constants.O_CREAT | constants.O_NOFOLLOW | constants.O_NONBLOCK, 0o600);
    try {
      const stat = await handle.stat();
      if (!stat.isFile() || stat.nlink !== 1) fail(403, 'BRIDGE_FILE_PATH_REJECTED', '不支持此文件类型。');
      await handle.truncate(0); await handle.writeFile(text); await handle.sync();
    } finally { await handle.close(); }
    return { path, saved: true };
  })); }
  mount(ctx: Context) {
    const names = ['workspace_read', 'workspace_write'];
    for (const name of names) ctx.tools.register({
      name, description: name === 'workspace_read' ? 'List a personal directory or read a UTF-8 file. Relative path only; empty path lists root.' : 'Write a personal UTF-8 text file. Relative path only, up to 128 KiB.',
      parameters: { type: 'object', properties: { path: { type: 'string' }, ...(name === 'workspace_write' ? { text: { type: 'string' } } : {}) }, required: name === 'workspace_write' ? ['path', 'text'] : ['path'], additionalProperties: false },
      output: { schema: { type: 'object' }, render: (_args, value) => [{ type: 'text', text: JSON.stringify(value) }] },
      execute: async (args, exec) => {
        exec.signal?.throwIfAborted();
        if (name === 'workspace_read') return this.read(z.object({ path: z.string() }).strict().parse(args).path);
        const input = z.object({ path: z.string(), text: z.string() }).strict().parse(args);
        return this.write(input.path, input.text);
      },
    });
    return names;
  }
}
