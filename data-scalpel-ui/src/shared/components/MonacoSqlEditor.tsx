import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import type { CSSProperties } from 'react';

interface MonacoSqlEditorProps {
  value?: string;
  readOnly?: boolean;
  height?: CSSProperties['height'];
  className?: string;
  language?: string;
  wordWrap?: boolean;
  revealAtEnd?: boolean;
  onAtEndChange?: (atEnd: boolean) => void;
  onChange: (value: string) => void;
}

export interface MonacoSqlEditorHandle {
  focus(): void;
  insertText(value: string): void;
  openFind(): void;
  scrollToStart(): void;
  scrollToEnd(): void;
}

/** Shared Monaco editor that keeps Monaco in a dynamic chunk. */
export const MonacoSqlEditor = forwardRef<MonacoSqlEditorHandle, MonacoSqlEditorProps>(({
  value = '',
  readOnly = false,
  height = 420,
  className,
  language = 'sql',
  wordWrap = false,
  revealAtEnd = false,
  onAtEndChange,
  onChange,
}, ref) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<import('monaco-editor').editor.IStandaloneCodeEditor | null>(null);
  const valueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  const onAtEndChangeRef = useRef(onAtEndChange);
  const applyingExternalValueRef = useRef(false);
  const initialOptionsRef = useRef({ language, readOnly, wordWrap, revealAtEnd });

  useEffect(() => {
    valueRef.current = value;
    const editor = editorRef.current;
    if (!editor || editor.getValue() === value) return;
    applyingExternalValueRef.current = true;
    editor.setValue(value);
    applyingExternalValueRef.current = false;
  }, [value]);

  useEffect(() => {
    const editor = editorRef.current;
    if (!editor) return;
    const monaco = editor.getModel();
    if (monaco) import('monaco-editor').then((api) => api.editor.setModelLanguage(monaco, language));
    editor.updateOptions({ readOnly, wordWrap: wordWrap ? 'on' : 'off' });
  }, [language, readOnly, wordWrap]);

  useEffect(() => {
    if (!revealAtEnd || !editorRef.current) return;
    const editor = editorRef.current;
    const line = editor.getModel()?.getLineCount() ?? 1;
    editor.revealLine(line);
  }, [revealAtEnd, value]);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    onAtEndChangeRef.current = onAtEndChange;
  }, [onAtEndChange]);

  useEffect(() => {
    let disposed = false;
    let editorInstance: import('monaco-editor').editor.IStandaloneCodeEditor | undefined;
    void import('monaco-editor').then((monaco) => {
      if (disposed || !containerRef.current) return;
      const initial = initialOptionsRef.current;
      editorInstance = monaco.editor.create(containerRef.current, {
        value: valueRef.current,
        language: initial.language,
        theme: 'vs',
        readOnly: initial.readOnly,
        automaticLayout: true,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        fontSize: 14,
        lineNumbersMinChars: 3,
        wordWrap: initial.wordWrap ? 'on' : 'off',
      });
      editorRef.current = editorInstance;
      if (initial.revealAtEnd) {
        const line = editorInstance.getModel()?.getLineCount() ?? 1;
        editorInstance.revealLine(line);
      }
      editorInstance.onDidChangeModelContent(() => {
        if (!applyingExternalValueRef.current) {
          onChangeRef.current(editorInstance?.getValue() ?? '');
        }
      });
      editorInstance.onDidScrollChange(() => {
        const editor = editorInstance;
        if (!editor) return;
        const atEnd = editor.getScrollTop() + editor.getLayoutInfo().height >= editor.getScrollHeight() - 8;
        onAtEndChangeRef.current?.(atEnd);
      });
    });
    return () => {
      disposed = true;
      editorInstance?.dispose();
      if (editorRef.current === editorInstance) editorRef.current = null;
    };
  }, []);

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
    openFind: () => editorRef.current?.getAction('actions.find')?.run(),
    scrollToStart: () => editorRef.current?.revealLine(1),
    scrollToEnd: () => {
      const editor = editorRef.current;
      editor?.revealLine(editor.getModel()?.getLineCount() ?? 1);
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
