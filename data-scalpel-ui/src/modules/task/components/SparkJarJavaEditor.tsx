import { EditOutlined } from '@ant-design/icons';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { forwardRef, useEffect, useImperativeHandle, useLayoutEffect, useRef, useState } from 'react';
import { App, Button, Form, Input, Modal, Space, Spin, Typography } from 'antd';
import { attachJavaLanguage, type JavaLanguageStatus } from '../model/javaLanguageMonaco';
import { javaEntry, type JavaEntry } from '../model/javaEntry';
import { dataScalpelJavaSnippets, sparkJavaApiIndex } from '../model/sparkJavaApiIndex';
import type { SparkJarOnlineDiagnostic } from '../model/task';
import './sparkJarEditor.css';
import type { SparkJarCodeResource } from '../model/sparkJarCodeResource';
export type { SparkJarCodeResource } from '../model/sparkJarCodeResource';

// Local document identity only; no secure-context browser API is needed on intranet HTTP.
let editorInstanceSequence = 0;

interface SparkJarJavaEditorProps {
  taskId: string;
  value: string;
  diagnostics: SparkJarOnlineDiagnostic[];
  resources: SparkJarCodeResource[];
  jobMode: 'BATCH' | 'STREAMING';
  onChange: (value: string) => void;
  onEntryChange?: (entry: JavaEntry) => void;
  onReadyChange?: (ready: boolean) => void;
  onEditingAllowedChange?: (allowed: boolean) => void;
  onReturn?: () => void;
}

export interface SparkJarJavaEditorHandle {
  revealDiagnostic(diagnostic: SparkJarOnlineDiagnostic): void;
  focus(): void;
  insertText(value: string): void;
  renameEntry(): void;
}

const explicitVariableType = (source: string, variable: string): string | undefined => {
  const escaped = variable.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = source.match(new RegExp(
    `(?:Dataset\\s*<\\s*Row\\s*>|Column|RelationalGroupedDataset|Row|SparkSession|DataFrameReader|DataFrameWriter(?:\\s*<[^>]+>)?|WindowSpec)\\s+${escaped}\\b`,
  ));
  if (!match) return undefined;
  if (match[0].startsWith('Dataset')) return 'Dataset';
  return match[0].trim().split(/\s+/)[0];
};

const receiverOwner = (source: string, prefix: string): string | undefined => {
  if (/context\.models\(\)\.[A-Za-z_$\w$]*$/.test(prefix)) return 'ModelResources';
  if (/context\.jdbc\(\)\.[A-Za-z_$\w$]*$/.test(prefix)) return 'JdbcResources';
  if (/context\.[A-Za-z_$\w$]*$/.test(prefix)) return 'SparkJobContext';
  if (/context\.models\(\)\.write\([^;]*\)\.[A-Za-z_$\w$]*$/.test(prefix)) return 'ModelWriteOperation';
  const variable = prefix.match(/([A-Za-z_$][\w$]*)\.[A-Za-z_$\w$]*$/)?.[1];
  if (variable === 'functions' || variable === 'Window') return variable;
  if (!variable) {
    if (/\.groupBy\([^;]*\)\.[A-Za-z_$\w$]*$/.test(prefix)) return 'RelationalGroupedDataset';
    if (/\.(?:col|alias|cast|equalTo|notEqual|gt|geq|lt|leq|and|or|isNull|isNotNull|asc|desc|over)\([^;]*\)\.[A-Za-z_$\w$]*$/.test(prefix)) return 'Column';
    const datasetChain = prefix.match(/([A-Za-z_$][\w$]*)\.(?:select|selectExpr|withColumn|withColumnRenamed|drop|filter|where|join|orderBy|limit|distinct|dropDuplicates|unionByName|repartition|coalesce|cache)\([^;]*\)\.[A-Za-z_$\w$]*$/);
    if (datasetChain && explicitVariableType(source, datasetChain[1]) === 'Dataset') return 'Dataset';
    return undefined;
  }
  return explicitVariableType(source, variable);
};

const datasetBindings = (source: string): Map<string, string> => {
  const result = new Map<string, string>();
  const modelPattern = /(?:Dataset\s*<\s*Row\s*>|var)\s+([A-Za-z_$][\w$]*)\s*=\s*context\.models\(\)\.read\("([^"]+)"/g;
  for (const match of source.matchAll(modelPattern)) result.set(match[1], match[2]);
  const jdbcPattern = /(?:Dataset\s*<\s*Row\s*>|var)\s+([A-Za-z_$][\w$]*)\s*=\s*context\.jdbc\(\)\.readTable\("([^"]+)"/g;
  for (const match of source.matchAll(jdbcPattern)) result.set(match[1], match[2]);
  return result;
};

export const SparkJarJavaEditor = forwardRef<SparkJarJavaEditorHandle, SparkJarJavaEditorProps>(({
  taskId,
  value,
  diagnostics,
  resources,
  jobMode,
  onChange,
  onEntryChange,
  onReadyChange,
  onEditingAllowedChange,
  onReturn,
}, ref) => {
  const { message } = App.useApp();
  const [renameForm] = Form.useForm<{ name: string }>();
  const [renameOpen, setRenameOpen] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [editingAllowed, setEditingAllowed] = useState(false);
  const editingAllowedRef = useRef(false);
  const readyChangeRef = useRef(onReadyChange);
  const editingChangeRef = useRef(onEditingAllowedChange);
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<import('monaco-editor').editor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<typeof import('monaco-editor') | null>(null);
  const valueRef = useRef(value);
  const resourcesRef = useRef(resources);
  const onChangeRef = useRef(onChange);
  const onEntryChangeRef = useRef(onEntryChange);
  const applyingValueRef = useRef(false);
  const languageRef = useRef<ReturnType<typeof attachJavaLanguage> | null>(null);
  const [languageStatus, setLanguageStatus] = useState<JavaLanguageStatus>({ ready: false, message: 'Java 语义提示准备中…', retryable: false });

  useEffect(() => { resourcesRef.current = resources; }, [resources]);
  useEffect(() => { onChangeRef.current = onChange; }, [onChange]);
  useEffect(() => { onEntryChangeRef.current = onEntryChange; }, [onEntryChange]);
  useEffect(() => { readyChangeRef.current = onReadyChange; }, [onReadyChange]);
  useEffect(() => { editingChangeRef.current = onEditingAllowedChange; }, [onEditingAllowedChange]);

  const allowEditing = () => {
    editingAllowedRef.current = true;
    setEditingAllowed(true);
    editingChangeRef.current?.(true);
    editorRef.current?.updateOptions({ readOnly: false });
    editorRef.current?.focus();
  };

  // Synchronize controlled updates before the next input event. A passive effect can
  // replay an earlier keystroke over Monaco's newer document and reset the caret.
  useLayoutEffect(() => {
    valueRef.current = value;
    const editor = editorRef.current;
    if (!editor || editor.getValue() === value) return;
    applyingValueRef.current = true;
    editor.setValue(value);
    applyingValueRef.current = false;
  }, [value]);

  useEffect(() => {
    const monaco = monacoRef.current;
    const model = editorRef.current?.getModel();
    if (!monaco || !model) return;
    const severity = (value: SparkJarOnlineDiagnostic['severity']) => {
      if (value === 'ERROR') return monaco.MarkerSeverity.Error;
      if (value === 'WARNING') return monaco.MarkerSeverity.Warning;
      return monaco.MarkerSeverity.Info;
    };
    monaco.editor.setModelMarkers(model, 'datascalpel-online-compiler', diagnostics.map((item) => ({
      severity: severity(item.severity),
      code: item.code,
      message: item.message,
      startLineNumber: Math.max(1, item.line),
      startColumn: Math.max(1, item.column),
      endLineNumber: Math.max(item.line, item.endLine),
      endColumn: item.endLine > item.line ? Math.max(1, item.endColumn) : Math.max(item.column + 1, item.endColumn),
    })));
  }, [diagnostics]);

  useEffect(() => {
    let disposed = false;
    let editor: import('monaco-editor').editor.IStandaloneCodeEditor | undefined;
    let model: import('monaco-editor').editor.ITextModel | undefined;
    const disposables: Array<{ dispose(): void }> = [];
    editingAllowedRef.current = false;
    editingChangeRef.current?.(false);
    void import('monaco-editor').then((monaco) => {
      if (disposed || !containerRef.current) return;
      monacoRef.current = monaco;
      monaco.editor.defineTheme('datascalpel-java-light', {
        base: 'vs',
        inherit: true,
        rules: [
          { token: 'keyword', foreground: '6650A4' },
          { token: 'comment', foreground: '617969', fontStyle: '' },
          { token: 'string', foreground: '33704C' },
          { token: 'number', foreground: '98612E' },
          { token: 'type.identifier', foreground: '286D83' },
          { token: 'annotation', foreground: '87622D' },
        ],
        colors: {
          'editor.background': '#FFFFFF',
          'editor.foreground': '#263449',
          'editor.lineHighlightBackground': '#F3F6FC',
          'editor.lineHighlightBorder': '#00000000',
          'editorLineNumber.foreground': '#8491A5',
          'editorLineNumber.activeForeground': '#345ACA',
          'editor.selectionBackground': '#DCE6FF',
          'editor.inactiveSelectionBackground': '#EAF0FA',
          'editorIndentGuide.background1': '#E8ECF3',
          'editorIndentGuide.activeBackground1': '#B9C7DF',
          'editorBracketMatch.background': '#E8EEFC',
          'editorBracketMatch.border': '#B8C8EF',
          'editorBracketHighlight.foreground1': '#566B96',
          'editorBracketHighlight.foreground2': '#79669A',
          'editorBracketHighlight.foreground3': '#508477',
          'editorBracketHighlight.foreground4': '#566B96',
          'editorBracketHighlight.foreground5': '#79669A',
          'editorBracketHighlight.foreground6': '#508477',
          'editorBracketPairGuide.activeBackground1': '#CBD5E5',
          'editorBracketPairGuide.activeBackground2': '#D8D0E5',
          'editorBracketPairGuide.activeBackground3': '#C9DDD6',
        },
      });
      const fileName = jobMode === 'STREAMING' ? 'ExampleSparkStreamingJob.java' : 'ExampleSparkJob.java';
      const uri = monaco.Uri.parse(`inmemory://datascalpel/task/${taskId}/editor-${++editorInstanceSequence}/com/example/datascalpel/${fileName}`);
      model = monaco.editor.createModel(valueRef.current, 'java', uri);
      editor = monaco.editor.create(containerRef.current, {
        model,
        readOnly: true,
        theme: 'datascalpel-java-light',
        automaticLayout: true,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        fontSize: 14,
        lineHeight: 22,
        fontFamily: '"DataScalpel JetBrains Mono", Consolas, "Microsoft YaHei", monospace',
        fontWeight: '400',
        fontLigatures: false,
        renderLineHighlight: 'all',
        guides: { indentation: true, highlightActiveIndentation: true, bracketPairs: 'active' },
        matchBrackets: 'always',
        folding: true,
        showFoldingControls: 'mouseover',
        bracketPairColorization: { enabled: true },
        stickyScroll: { enabled: false },
        scrollbar: { verticalScrollbarSize: 10, horizontalScrollbarSize: 10 },
        lineNumbersMinChars: 3,
        tabSize: 4,
        insertSpaces: true,
        padding: { top: 12, bottom: 12 },
        quickSuggestionsDelay: 60,
        suggest: { showSnippets: true, snippetsPreventQuickSuggestions: false },
      });
      editorRef.current = editor;
      onEntryChangeRef.current?.(javaEntry(monaco, model.getValue(), fileName));
      languageRef.current = attachJavaLanguage(monaco, model, taskId, fileName, (status) => {
        if (disposed) return;
        setLanguageStatus(status);
        readyChangeRef.current?.(status.ready);
        if (status.ready && !editingAllowedRef.current) allowEditing();
      });
      disposables.push(languageRef.current);
      // Monaco caches glyph widths: measure again once the bundled font is ready.
      void document.fonts.load('14px "DataScalpel JetBrains Mono"').then(() => {
        if (!disposed) monaco.editor.remeasureFonts();
      }).catch(() => { /* System monospace remains usable if the font cannot load. */ });
      disposables.push(editor.onDidChangeModelContent(() => {
        if (model) onEntryChangeRef.current?.(javaEntry(monaco, model.getValue(), fileName));
        if (!applyingValueRef.current) onChangeRef.current(editor?.getValue() ?? '');
      }));

      disposables.push(monaco.languages.registerCompletionItemProvider('java', {
        triggerCharacters: ['.', '"'],
        provideCompletionItems: (currentModel, position) => {
          if (currentModel.uri.toString() !== uri.toString()) return { suggestions: [] };
          const source = currentModel.getValue();
          const line = currentModel.getLineContent(position.lineNumber);
          const prefix = line.slice(0, position.column - 1);
          const word = currentModel.getWordUntilPosition(position);
          const defaultRange = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);
          const afterQuote = prefix.lastIndexOf('"') + 1;
          const stringRange = new monaco.Range(position.lineNumber, Math.max(1, afterQuote + 1), position.lineNumber, position.column);
          const currentResources = resourcesRef.current;
          const suggestions: import('monaco-editor').languages.CompletionItem[] = [];

          const modelBindingContext = /context\.models\(\)\.(?:read|write)\("[^"]*$/.test(prefix);
          const jdbcBindingContext = /context\.jdbc\(\)\.(?:readTable|readQuery|write)\("[^"]*$/.test(prefix);
          const kafkaBindingContext = /context\.kafka\(\)\.(?:readStream|writeStream)\("[^"]*$/.test(prefix);
          if (modelBindingContext || jdbcBindingContext || kafkaBindingContext) {
            const kind = modelBindingContext ? 'MODEL' : jdbcBindingContext ? 'JDBC_TABLE' : 'KAFKA_TOPIC';
            const writes = /\.(?:write|writeStream)\("[^"]*$/.test(prefix);
            const seen = new Set<string>();
            currentResources.filter((item) => (item.kind === kind || jdbcBindingContext && item.kind === 'JDBC_CONNECTION')
              && (writes ? item.accessMode !== 'READ' : item.accessMode !== 'WRITE'))
              .filter((item) => { if (seen.has(item.bindingName)) return false; seen.add(item.bindingName); return true; })
              .forEach((item) => suggestions.push({
              label: item.bindingName,
              kind: monaco.languages.CompletionItemKind.Reference,
              detail: item.label,
              documentation: `${item.fields.length} 个字段`,
              insertText: item.bindingName,
              range: stringRange,
            }));
            return { suggestions };
          }

          const tableContext = prefix.match(/context\.jdbc\(\)\.readTable\("([^"]+)",\s*"[^"]*$/);
          if (tableContext) {
            currentResources.filter((item) => item.kind === 'JDBC_TABLE' && item.bindingName === tableContext[1])
              .forEach((item) => item.table && suggestions.push({
                label: item.table,
                kind: monaco.languages.CompletionItemKind.Value,
                detail: item.label,
                insertText: item.table,
                range: stringRange,
              }));
            return { suggestions };
          }

          if (/(?:\.col|\bcol|\.getAs)\("[^"]*$/.test(prefix)) {
            const variable = prefix.match(/([A-Za-z_$][\w$]*)\.(?:col|getAs)\("[^"]*$/)?.[1];
            const binding = variable ? datasetBindings(source).get(variable) : undefined;
            const candidates = binding
              ? currentResources.filter((item) => item.bindingName === binding)
              : variable ? [] : currentResources;
            const observed = new Set<string>();
            candidates.forEach((resource) => resource.fields.forEach((field) => {
              const key = `${resource.bindingName}\u0000${field.name}`;
              if (observed.has(key)) return;
              observed.add(key);
              suggestions.push({
                label: field.name,
                kind: monaco.languages.CompletionItemKind.Field,
                detail: `${field.type}${field.nullable ? ' · nullable' : ''} · ${resource.bindingName}`,
                documentation: resource.label,
                insertText: field.name,
                sortText: `${binding ? '0' : '1'}-${field.name}`,
                range: stringRange,
              });
            }));
            return { suggestions };
          }

          const owner = receiverOwner(source, prefix);
          if (owner && !languageRef.current?.ready) {
            sparkJavaApiIndex.filter((item) => item.owner === owner).forEach((item) => suggestions.push({
              label: item.name,
              kind: monaco.languages.CompletionItemKind.Method,
              detail: `${item.returnType} · ${item.signature}`,
              documentation: item.summary,
              insertText: item.snippet,
              insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
              range: defaultRange,
            }));
            return { suggestions };
          }

          dataScalpelJavaSnippets.forEach((item) => suggestions.push({
            label: item.label,
            kind: monaco.languages.CompletionItemKind.Snippet,
            detail: item.detail,
            documentation: item.documentation,
            insertText: item.insertText,
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            range: defaultRange,
          }));
          return { suggestions };
        },
      }));

      disposables.push(monaco.languages.registerHoverProvider('java', {
        provideHover: (currentModel, position) => {
          if (languageRef.current?.ready) return null;
          if (currentModel.uri.toString() !== uri.toString()) return null;
          const word = currentModel.getWordAtPosition(position);
          if (!word) return null;
          const candidates = sparkJavaApiIndex.filter((item) => item.name === word.word).slice(0, 8);
          if (!candidates.length) return null;
          return {
            range: new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn),
            contents: candidates.flatMap((item) => [
              { value: `\`${item.returnType} ${item.owner}.${item.signature}\`` },
              { value: item.summary },
            ]),
          };
        },
      }));
    }).catch(() => {
      if (!disposed) setLanguageStatus({ ready: false, message: '编辑器加载失败，请返回后重试', retryable: true });
    });
    return () => {
      disposed = true;
      disposables.forEach((item) => item.dispose());
      editor?.dispose();
      model?.dispose();
      editorRef.current = null;
      monacoRef.current = null;
      languageRef.current = null;
    };
  }, [jobMode, taskId]);

  useImperativeHandle(ref, () => ({
    renameEntry: () => {
      const editor = editorRef.current;
      const monaco = monacoRef.current;
      const model = editor?.getModel();
      if (!editor || !monaco || !model || !languageRef.current?.ready) return;
      const entry = javaEntry(monaco, model.getValue(), jobMode === 'STREAMING' ? 'ExampleSparkStreamingJob.java' : 'ExampleSparkJob.java');
      if (entry.offset < 0) { void message.warning('未找到入口类，请先修正类声明'); return; }
      renameForm.resetFields();
      renameForm.setFieldsValue({ name: entry.fileName.replace(/\.java$/, '') });
      setRenameOpen(true);
    },
    focus: () => editorRef.current?.focus(),
    revealDiagnostic: (diagnostic) => {
      const editor = editorRef.current;
      if (!editor) return;
      editor.setPosition({ lineNumber: Math.max(1, diagnostic.line), column: Math.max(1, diagnostic.column) });
      editor.revealPositionInCenter({ lineNumber: Math.max(1, diagnostic.line), column: Math.max(1, diagnostic.column) });
      editor.focus();
    },
    insertText: (text) => {
      const editor = editorRef.current;
      const selection = editor?.getSelection();
      if (!editor || !selection || !text || !editingAllowedRef.current) return;
      editor.pushUndoStop();
      editor.executeEdits('datascalpel-online-resource', [{ range: selection, text, forceMoveMarkers: true }]);
      editor.pushUndoStop();
      editor.focus();
    },
  }), [jobMode, message, renameForm]);

  const confirmRename = async () => {
    if (renaming) return;
    let name: string;
    try { name = (await renameForm.validateFields()).name.trim(); } catch { return; }
    const editor = editorRef.current;
    const monaco = monacoRef.current;
    const model = editor?.getModel();
    if (!editor || !monaco || !model) return;
    const entry = javaEntry(monaco, model.getValue(), 'ExampleSparkJob.java');
    if (entry.offset < 0) { renameForm.setFields([{ name: 'name', errors: ['未找到入口类，请先修正类声明'] }]); return; }
    if (name === entry.fileName.replace(/\.java$/, '')) { setRenameOpen(false); return; }
    setRenaming(true);
    try {
      const result = await languageRef.current?.rename(model.getPositionAt(entry.offset), name);
      if (!result || result.rejectReason) throw new Error(result?.rejectReason ?? 'Java 语义服务尚未就绪');
      const edits = result.edits.filter((edit): edit is import('monaco-editor').languages.IWorkspaceTextEdit => 'textEdit' in edit);
      if (!edits.length || edits.some((edit) => edit.versionId !== model.getVersionId())) throw new Error('代码已变化，请重新确认重命名');
      editor.pushUndoStop();
      editor.executeEdits('datascalpel-entry-rename', edits.map((edit) => ({ ...edit.textEdit, forceMoveMarkers: true })));
      editor.pushUndoStop();
      setRenameOpen(false);
      void message.success('已同步更新文件名、主类与代码引用');
      editor.focus();
    } catch (error) {
      renameForm.setFields([{ name: 'name', errors: [error instanceof Error ? error.message : '重命名失败，请重试'] }]);
    } finally { setRenaming(false); }
  };

  return <div className="spark-jar-java-editor-shell">
    <div className="spark-jar-java-editor-area">
      <div ref={containerRef} className="spark-jar-java-editor" />
      {!editingAllowed && <div className="spark-jar-java-preparing" role="status" aria-live="polite">
        {!languageStatus.retryable && <Spin size="large" />}
        <Typography.Title level={4}>{languageStatus.retryable ? '开发环境准备失败' : '正在准备 Java 开发环境'}</Typography.Title>
        <Typography.Text type="secondary">{languageStatus.message}</Typography.Text>
        {!languageStatus.retryable && <Typography.Text type="secondary">准备完成后自动开放编辑</Typography.Text>}
        <Space wrap>
          {onReturn && <Button onClick={onReturn}>返回任务定义</Button>}
          {languageStatus.retryable && languageRef.current && <>
            <Button type="primary" onClick={() => languageRef.current?.retry()}>重新准备</Button>
            <Button type="link" onClick={allowEditing}>暂不使用语义提示，继续编辑</Button>
          </>}
        </Space>
      </div>}
    </div>
    <div className="spark-jar-java-language-status" role="status" data-state={languageStatus.ready ? 'ready' : languageStatus.retryable ? 'error' : 'preparing'}>
      <span className="spark-jar-java-language-message"><i aria-hidden="true" />{languageStatus.message}</span>
      <span className="spark-jar-java-language-meta">Java · UTF-8</span>
      {languageStatus.retryable && <Button type="link" size="small" onClick={() => languageRef.current?.retry()}>重新连接</Button>}
    </div>
    <Modal open={renameOpen} title={<OverlayTitle icon={<EditOutlined />} title="重命名 Java 文件" />} rootClassName="business-overlay business-modal-overlay resource-workspace-overlay"
      okText="确认重命名" cancelText="取消" confirmLoading={renaming} maskClosable={false}
      closable={!renaming} keyboard={!renaming} cancelButtonProps={{ disabled: renaming }}
      onCancel={() => setRenameOpen(false)} onOk={() => void confirmRename()}>
      <Form form={renameForm} layout="vertical" autoComplete="off" onFinish={() => void confirmRename()}>
        <Form.Item name="name" label="类名" extra="同步更新文件名、主类与代码引用；包名不变。"
          rules={[{ required: true, message: '请输入类名' }, { pattern: /^[A-Za-z_$][\w$]*$/, message: '请输入 Java 类名，不含 .java 后缀' }]}>
          <Input autoFocus autoComplete="off" disabled={renaming} suffix=".java" />
        </Form.Item>
      </Form>
    </Modal>
  </div>;
});

SparkJarJavaEditor.displayName = 'SparkJarJavaEditor';
