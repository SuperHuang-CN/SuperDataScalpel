import { describe, expect, it } from 'vitest';
import type { DocumentHighlight, DocumentSymbol, Range } from 'vscode-languageserver-protocol';
import { entryRenameCandidate } from './javaEntryRename';

const range = (line: number, start: number, end = start + 3): Range => ({ start: { line, character: start }, end: { line, character: end } });
const lines = ['import static org.apache.spark.sql.functions.*;', 'public class Job {', ' public Job() {}',
  ' Job copy() { return new Job(); }', ' String name = "Job"; // Job', ' int Job = 1;', '}'];
const highlights: DocumentHighlight[] = [range(1, 13), range(3, 1), range(3, 25)].map((range) => ({ range }));
const symbols: DocumentSymbol[] = [{ name: 'Job', kind: 5, range: range(1, 0, 18), selectionRange: range(1, 13), children: [
  { name: 'Job()', kind: 9, range: range(2, 0, 16), selectionRange: range(2, 8) },
] }];
const source = lines.join('\n');
const entryOffset = source.indexOf('Job');

describe('entry rename compatibility candidate', () => {
  it.each(['\n', '\r\n'])('changes only semantic type/constructor ranges with %j', (eol) => {
    const text = lines.join(eol);
    const result = entryRenameCandidate(text, text, text.indexOf('Job'), 'Job', 'MeterJob', highlights, symbols);
    expect(result.edits).toHaveLength(4);
    expect(result.source).toContain('public class MeterJob');
    expect(result.source).toContain('public MeterJob()');
    expect(result.source).toContain('MeterJob copy() { return new MeterJob(); }');
    expect(result.source).toContain('String name = "Job"; // Job');
    expect(result.source).toContain('int Job = 1;');
    expect(result.source.startsWith(lines[0] + eol)).toBe(true);
  });

  it('rejects collisions and missing semantic entry metadata', () => {
    expect(() => entryRenameCandidate(source, source, entryOffset, 'Job', 'String', highlights, symbols)).toThrow('已在代码中使用');
    expect(() => entryRenameCandidate(source, source, entryOffset, 'Job', 'OtherJob', highlights, [])).toThrow('定位主类');
    expect(() => entryRenameCandidate(source, source, entryOffset, 'Job', 'OtherJob', highlights.slice(1), symbols)).toThrow('定位主类');
  });

  it('ignores name mentions in strings/comments that the existing tokenizer has masked', () => {
    expect(entryRenameCandidate(source + '// MeterJob', source, entryOffset, 'Job', 'MeterJob', highlights, symbols).source).toContain('// MeterJob');
  });

  it.each([
    [...highlights, highlights[0]],
    [{ range: range(500, 0) }],
    [{ range: range(1, -1) }],
    [{ range: range(1, 12) }],
  ].map((invalid) => ({ invalid })))('rejects overlapping or invalid semantic ranges', ({ invalid }) => {
    expect(() => entryRenameCandidate(source, source, entryOffset, 'Job', 'OtherJob', invalid, symbols)).toThrow('定位');
  });
});
