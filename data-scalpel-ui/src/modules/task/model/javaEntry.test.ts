import { describe, expect, it } from 'vitest';
import type * as Monaco from 'monaco-editor';
import { javaEntry } from './javaEntry';

function tokenizer(rows: { offset: number; type: string }[][]) {
  return { editor: { tokenize: () => rows } } as unknown as typeof Monaco;
}

describe('javaEntry source positions', () => {
  it.each(['\n', '\r\n'])('preserves offsets across tokenless blank lines with %j', (eol) => {
    const source = ['package com.example.datascalpel;', '', 'import java.util.HashMap;', '',
      'public final class ExampleSparkJob {', '}'].join(eol);
    // Monaco emits an empty token array for blank lines, including CRLF lines.
    const rows = source.split('\n').map((line) => line.trim() ? [{ offset: 0, type: '' }] : []);
    const entry = javaEntry(tokenizer(rows), source, 'Fallback.java');
    expect(entry.offset).toBe(source.indexOf('ExampleSparkJob'));
    expect(source.slice(entry.offset, entry.offset + 'ExampleSparkJob'.length)).toBe('ExampleSparkJob');
    expect(entry.fileName).toBe('ExampleSparkJob.java');
    expect(entry.uri).toBe('file:///datascalpel/src/com/example/datascalpel/ExampleSparkJob.java');
  });

  it('masks comments and strings without shifting mixed-EOL or UTF-16 positions', () => {
    const literal = '"public class Fake {}"';
    const declaration = `public /* 中文🙂 */ final class Actual { String text = ${literal}; }`;
    const source = `// public class Comment {}\r\n\r\npackage demo;\n${declaration}`;
    const commentStart = declaration.indexOf('/*');
    const commentEnd = declaration.indexOf('*/') + 2;
    const stringStart = declaration.indexOf(literal);
    const entry = javaEntry(tokenizer([
      [{ offset: 0, type: 'comment.java' }], [], [{ offset: 0, type: '' }],
      [{ offset: 0, type: '' }, { offset: commentStart, type: 'comment.java' },
        { offset: commentEnd, type: '' }, { offset: stringStart, type: 'string.java' },
        { offset: stringStart + literal.length, type: '' }],
    ]), source, 'Fallback.java');
    expect(entry.className).toBe('demo.Actual');
    expect(entry.offset).toBe(source.indexOf('Actual'));
  });

  it('does not treat a class declaration inside a comment as an entry', () => {
    const entry = javaEntry(tokenizer([[{ offset: 0, type: 'comment.java' }]]),
      '// public class Fake {}', 'Fallback.java');
    expect(entry.offset).toBe(-1);
    expect(entry.fileName).toBe('Fallback.java');
  });
});
