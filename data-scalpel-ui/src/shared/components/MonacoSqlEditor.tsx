import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import type { CSSProperties } from 'react';

interface MonacoSqlEditorProps {
  value?: string;
  readOnly?: boolean;
  height?: CSSProperties['height'];
  className?: string;
  onChange: (value: string) => void;
}

export interface MonacoSqlEditorHandle {
  focus(): void;
  insertText(value: string): void;
}

/** Shared SQL editor that keeps Monaco in a dynamic chunk. */
export const MonacoSqlEditor = forwardRef<MonacoSqlEditorHandle, MonacoSqlEditorProps>(({
  value = '',
  readOnly = false,
  height = 420,
  className,
  onChange,
}, ref) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<import('monaco-editor').editor.IStandaloneCodeEditor | null>(null);
  const valueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  const applyingExternalValueRef = useRef(false);

  useEffect(() => {
    valueRef.current = value;
    const editor = editorRef.current;
    if (!editor || editor.getValue() === value) return;
    applyingExternalValueRef.current = true;
    editor.setValue(value);
    applyingExternalValueRef.current = false;
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
      editorRef.current = editorInstance;
      editorInstance.onDidChangeModelContent(() => {
        if (!applyingExternalValueRef.current) {
          onChangeRef.current(editorInstance?.getValue() ?? '');
        }
      });
    });
    return () => {
      disposed = true;
      editorInstance?.dispose();
      if (editorRef.current === editorInstance) editorRef.current = null;
    };
  }, [readOnly]);

  useImperativeHandle(ref, () => ({
    focus: () => editorRef.current?.focus(),
    insertText: (text) => {
      if (!text || readOnly) return;
      const editor = editorRef.current;
      if (!editor) return;
      const selection = editor.getSelection();
      if (!selection) return;
      editor.executeEdits('datascalpel-sql-reference', [{
        range: selection,
        text,
        forceMoveMarkers: true,
      }]);
      editor.focus();
    },
  }), [readOnly]);

  return (
    <div
      ref={containerRef}
      className={className}
      style={{ height, border: '1px solid #d9d9d9', borderRadius: 6, overflow: 'hidden' }}
    />
  );
});

MonacoSqlEditor.displayName = 'MonacoSqlEditor';
