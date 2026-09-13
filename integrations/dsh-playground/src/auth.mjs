import { randomBytes, timingSafeEqual, createHash, scryptSync } from 'node:crypto';
export const owners = ['alice', 'bob'];
export const equal = (a, b) => timingSafeEqual(createHash('sha256').update(a ?? '').digest(), createHash('sha256').update(b ?? '').digest());
export class Logins {
  sessions = new Map();
  constructor(passwords, ttl = 43200000) {
    this.ttl = ttl;
    this.passwords = Object.fromEntries(owners.map(owner => {
      const salt = randomBytes(16);
      return [owner, { salt, hash: scryptSync(passwords[owner], salt, 32) }];
    }));
  }
  login(owner, password) {
    const entry = this.passwords[owners.includes(owner) ? owner : 'alice'];
    const valid = timingSafeEqual(entry.hash, scryptSync(password, entry.salt, 32));
    if (!owners.includes(owner) || !valid) return null;
    this.sweep();
    if (this.sessions.size >= 100) return null;
    const id = randomBytes(32).toString('hex');
    this.sessions.set(id, { owner, expires: Date.now() + this.ttl, sockets: new Set() });
    return id;
  }
  get(id) {
    const item = this.sessions.get(id);
    if (item && item.expires > Date.now()) return item;
    this.logout(id); return null;
  }
  logout(id) {
    const item = this.sessions.get(id);
    for (const socket of item?.sockets ?? []) socket.close(1008, 'login-required');
    this.sessions.delete(id);
  }
  sweep() { for (const id of this.sessions.keys()) this.get(id); }
}
export function cookieId(req) {
  return (req.headers.cookie ?? '').split(';').map(s => s.trim()).find(s => s.startsWith('dsh_playground='))?.slice(15);
}
export function bridgePath(path, owner) {
  if (!owners.includes(owner)) throw new Error('Invalid owner');
  if (!/^\/(capabilities|workspaces\/actions\/ensure|files|sessions(?:\/[0-9a-f-]{36}(?:\/messages|\/actions\/(?:resume|cancel)|\/interactions\/[0-9a-f-]{36}\/actions\/respond)?)?)$/.test(path)) return null;
  return `/bridge/v2/users/${owner}${path}`;
}
