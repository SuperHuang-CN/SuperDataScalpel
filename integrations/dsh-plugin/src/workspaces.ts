import { mkdir, realpath, stat } from 'node:fs/promises';
import type { Context } from '@deepseek-ai/cordis';
import type { Workspace } from '@deepseek-ai/dsh-workspace';
import '@deepseek-ai/dsh-workspace';
import { BridgeError, Serial } from './contracts.js';

export class Workspaces {
  private serial = new Serial();
  constructor(private ctx: Context, readonly root: string) {}
  ensure(): Promise<Workspace> {
    return this.serial.run(async () => {
      try {
        await mkdir(this.root, { recursive: true });
        const canonical = await realpath(this.root);
        if (!(await stat(canonical)).isDirectory()) throw new Error('not a directory');
        return await this.ctx.workspaceRegistry.create(canonical, 'DataScalpel Bridge 验证');
      } catch {
        throw new BridgeError(500, 'BRIDGE_WORKSPACE_UNAVAILABLE', '验证目录或工作区无法初始化。');
      }
    });
  }
}
