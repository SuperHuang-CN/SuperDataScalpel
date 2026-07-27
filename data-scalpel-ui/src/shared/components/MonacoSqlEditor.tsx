import { useEffect, useRef } from 'react';

interface MonacoSqlEditorProps {
  value?: string;
  readOnly?: boolean;
  height?: number;
  onChange: (value: string) => void;
}

/** Shared SQL editor that keeps Monaco in a dynamic chunk. */
export const MonacoSqlEditor = ({ value = '', readOnly = false, height = 420, onChange }: MonacoSqlEditorProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const valueRef = useRef(value);
  const onChangeRef = useRef(onChange);

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    let disposed = false;
    let editorInstance: import('monaco-editor').editor.IStandaloneCodeEditor | undefined;
    void import('monaco-editor').then((monaco) => {
      if (disposed || !containerRef.current) return;
      editorInstance = monaco.editor.create(containerRef.current, {
        value: valueRef.current,
        language: 'sql',
        theme: 'vs',
        readOnly,
        automaticLayout: true,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        fontSize: 14,
        lineNumbersMinChars: 3,
      });
      editorInstance.onDidChangeModelContent(() => onChangeRef.current(editorInstance?.getValue() ?? ''));
    });
    return () => {
      disposed = true;
      editorInstance?.dispose();
    };
  }, [readOnly]);

  return <div ref={containerRef} style={{ height, border: '1px solid #d9d9d9', borderRadius: 6, overflow: 'hidden' }} />;
};
