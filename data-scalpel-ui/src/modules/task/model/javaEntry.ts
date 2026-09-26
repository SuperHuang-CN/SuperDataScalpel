import type * as Monaco from 'monaco-editor';

export interface JavaEntry { fileName: string; className: string; uri: string; offset: number }

/** Preserve UTF-16 offsets while hiding comments/literals, not a Java parser. */
export function visibleJavaSource(monaco: typeof Monaco, source: string): string {
  const lines = source.split('\n');
  const tokens = monaco.editor.tokenize(source, 'java');
  return lines.map((line, index) => {
    // Mask in place: Monaco emits no tokens for an empty CRLF line. Rebuilding
    // from tokens would drop its CR and shift every later source offset.
    let masked = line;
    const row = tokens[index] ?? [];
    row.forEach((token, tokenIndex) => {
      if (!/comment|string/.test(token.type)) return;
      const end = row[tokenIndex + 1]?.offset ?? line.length;
      masked = masked.slice(0, token.offset) + ' '.repeat(end - token.offset) + masked.slice(end);
    });
    return masked;
  }).join('\n');
}

/** Display/document identity only; JDK parsing and compilation remain authoritative. */
export function javaEntry(monaco: typeof Monaco, source: string, fallback: string): JavaEntry {
  const visible = visibleJavaSource(monaco, source);
  const declaration = /\bpublic\s+(?:(?:final|abstract|sealed|non-sealed)\s+)*class\s+([A-Za-z_$][\w$]*)/.exec(visible);
  const name = declaration?.[1] ?? fallback.replace(/\.java$/, '');
  const packageMatch = /\bpackage\s+([A-Za-z_$][\w$]*(?:\s*\.\s*[A-Za-z_$][\w$]*)*)\s*;/.exec(visible);
  const packageName = packageMatch?.[1].replace(/\s/g, '') ?? 'com.example.datascalpel';
  return { fileName: `${name}.java`, className: `${packageName}.${name}`,
    uri: `file:///datascalpel/src/${packageName.replaceAll('.', '/')}/${name}.java`,
    offset: declaration ? declaration.index + declaration[0].lastIndexOf(name) : -1 };
}
