import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import { dataScalpelJavaSnippets, sparkJavaApiIndex } from '../model/sparkJavaApiIndex';
import type { SparkJarOnlineDiagnostic } from '../model/task';

export interface SparkJarCodeResource {
  bindingName: string;
  label: string;
  kind: 'MODEL' | 'JDBC_TABLE' | 'KAFKA_TOPIC';
  accessMode: 'READ' | 'WRITE' | 'READ_WRITE';
  table?: string;
  fields: Array<{ name: string; type: string; nullable: boolean }>;
  fieldsLoading?: boolean;
}

interface SparkJarJavaEditorProps {
  taskId: string;
  value: string;
  diagnostics: SparkJarOnlineDiagnostic[];
  resources: SparkJarCodeResource[];
  jobMode: 'BATCH' | 'STREAMING';
  onChange: (value: string) => void;
}

export interface SparkJarJavaEditorHandle {
  revealDiagnostic(diagnostic: SparkJarOnlineDiagnostic): void;
  focus(): void;
  insertText(value: string): void;
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
  const modelPattern = /Dataset\s*<\s*Row\s*>\s+([A-Za-z_$][\w$]*)\s*=\s*context\.models\(\)\.read\("([^"]+)"/g;
  for (const match of source.matchAll(modelPattern)) result.set(match[1], match[2]);
  const jdbcPattern = /Dataset\s*<\s*Row\s*>\s+([A-Za-z_$][\w$]*)\s*=\s*context\.jdbc\(\)\.readTable\("([^"]+)"/g;
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
}, ref) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<import('monaco-editor').editor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<typeof import('monaco-editor') | null>(null);
  const valueRef = useRef(value);
  const resourcesRef = useRef(resources);
  const onChangeRef = useRef(onChange);
  const applyingValueRef = useRef(false);

  useEffect(() => { resourcesRef.current = resources; }, [resources]);
  useEffect(() => { onChangeRef.current = onChange; }, [onChange]);

  useEffect(() => {
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
      endColumn: Math.max(item.column + 1, item.endColumn),
    })));
  }, [diagnostics]);

  useEffect(() => {
    let disposed = false;
    let editor: import('monaco-editor').editor.IStandaloneCodeEditor | undefined;
    let model: import('monaco-editor').editor.ITextModel | undefined;
    const disposables: Array<{ dispose(): void }> = [];
    void import('monaco-editor').then((monaco) => {
      if (disposed || !containerRef.current) return;
      monacoRef.current = monaco;
      const fileName = jobMode === 'STREAMING' ? 'ExampleSparkStreamingJob.java' : 'ExampleSparkJob.java';
      const uri = monaco.Uri.parse(`inmemory://datascalpel/task/${taskId}/com/example/datascalpel/${fileName}`);
      model = monaco.editor.getModel(uri) ?? monaco.editor.createModel(valueRef.current, 'java', uri);
      editor = monaco.editor.create(containerRef.current, {
        model,
        theme: 'vs',
        automaticLayout: true,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        fontSize: 14,
        lineHeight: 22,
        lineNumbersMinChars: 3,
        tabSize: 4,
        insertSpaces: true,
        padding: { top: 12, bottom: 12 },
        suggest: { showSnippets: true, snippetsPreventQuickSuggestions: false },
      });
      editorRef.current = editor;
      disposables.push(editor.onDidChangeModelContent(() => {
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
          const jdbcBindingContext = /context\.jdbc\(\)\.readTable\("[^"]*$/.test(prefix);
          const kafkaBindingContext = /context\.kafka\(\)\.(?:readStream|writeStream)\("[^"]*$/.test(prefix);
          if (modelBindingContext || jdbcBindingContext || kafkaBindingContext) {
            const kind = modelBindingContext ? 'MODEL' : jdbcBindingContext ? 'JDBC_TABLE' : 'KAFKA_TOPIC';
            currentResources.filter((item) => item.kind === kind).forEach((item) => suggestions.push({
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
              : currentResources;
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
          if (owner) {
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
    });
    return () => {
      disposed = true;
      disposables.forEach((item) => item.dispose());
      editor?.dispose();
      model?.dispose();
      editorRef.current = null;
      monacoRef.current = null;
    };
  }, [jobMode, taskId]);

  useImperativeHandle(ref, () => ({
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
      if (!editor || !selection || !text) return;
      editor.executeEdits('datascalpel-online-resource', [{ range: selection, text, forceMoveMarkers: true }]);
      editor.focus();
    },
  }), []);

  return <div ref={containerRef} className="spark-jar-java-editor" />;
});

SparkJarJavaEditor.displayName = 'SparkJarJavaEditor';
