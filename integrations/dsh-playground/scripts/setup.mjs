import { randomBytes } from 'node:crypto';
import { writeFile, readFile } from 'node:fs/promises';
const target = new URL('../../../deploy/dsh/.env.playground', import.meta.url);
const lines = ['DSH_PLAYGROUND_ENABLED=true'];
for (const user of ['ALICE', 'BOB']) {
  lines.push(`DSH_PLAYGROUND_${user}_PASSWORD=${randomBytes(15).toString('base64url')}`);
  lines.push(`DSH_PLAYGROUND_MCP_${user}_TOKEN=${randomBytes(32).toString('base64url')}`);
}
try { await writeFile(target, lines.join('\n') + '\n', { flag: 'wx', mode: 0o600 }); console.log('已创建 deploy/dsh/.env.playground，账号为 alice / bob，密码保存在该本地文件。'); }
catch (error) { if (error.code !== 'EEXIST') throw error; await readFile(target); console.log('沿用已有 .env.playground，不轮换凭据。'); }
