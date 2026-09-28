export interface SdkApiParameter { name: string; type: string; description: string }
export interface SdkApiMember {
  name: string; signature: string; summary: string; returnType: string; returns: string;
  parameters: SdkApiParameter[]; example: string; note: string; deprecated: boolean;
}
export interface SdkApiType {
  name: string; simpleName: string; group: string; mode: 'BOTH' | 'BATCH' | 'STREAMING';
  summary: string; example: string; note: string; parents: string[]; members: SdkApiMember[];
}
export interface SdkApiDocumentation { version: string; fingerprint: string; types: SdkApiType[] }

export function sdkTypesForMode(types: SdkApiType[], mode: 'BATCH' | 'STREAMING') {
  return types.filter((type) => type.mode === 'BOTH' || type.mode === mode);
}
export function matchesSdkSearch(type: SdkApiType, search: string) {
  const words = search.trim().toLocaleLowerCase().split(/\s+/);
  const text = [type.name, type.group, type.summary, type.note, type.example,
    ...type.members.flatMap((member) => [member.signature, member.summary, member.returnType, member.returns, member.note, member.example,
      ...member.parameters.map((p) => p.description)])].join(' ').toLocaleLowerCase();
  return words.every((word) => text.includes(word));
}

export function sdkMemberGroups(type: SdkApiType) {
  const members = new Map<string, SdkApiMember[]>();
  type.members.forEach(member => members.set(member.name, [...(members.get(member.name) ?? []), member]));
  return [...members].map(([name, overloads]) => ({ name, overloads }));
}

/** Reuse authored examples only; never generate executable calls from prose. */
export function sdkMemberExample(type: SdkApiType, member: SdkApiMember) {
  if (member.example) return member.example;
  const escapedName = member.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`\\b${escapedName}\\s*\\(`).test(type.example) ? type.example : '';
}
