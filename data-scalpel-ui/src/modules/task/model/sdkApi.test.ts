import { describe, expect, it } from 'vitest';
import { sdkMemberExample, sdkMemberGroups, type SdkApiType } from './sdkApi';

const type: SdkApiType = { name: 'Resources', simpleName: 'Resources', group: '用途', mode: 'BOTH', summary: '', note: '', parents: [],
  example: 'context.models().read("source");', members: [] };
const member = { name: 'read', signature: 'read(String name)', returnType: 'Rows', summary: '', returns: '', parameters: [], example: '', note: '', deprecated: false };
describe('SDK documentation presentation', () => {
  it('groups overloads without dropping members', () => {
    expect(sdkMemberGroups({ ...type, members: [member, { ...member, signature: 'read(String name, Options options)' }] })[0].overloads).toHaveLength(2);
  });
  it('uses the authored method example first and never shows an unrelated type example', () => {
    expect(sdkMemberExample(type, member)).toBe(type.example);
    expect(sdkMemberExample(type, { ...member, name: 'write' })).toBe('');
    expect(sdkMemberExample(type, { ...member, example: 'specific example' })).toBe('specific example');
    expect(sdkMemberExample(type, { ...member, name: 'read$' })).toBe('');
  });
});
