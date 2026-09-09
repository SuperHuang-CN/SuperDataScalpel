import { useEffect, useRef, useState } from 'react';
import { Button, Space, Typography } from 'antd';

export function McpScriptEditor({ value, onChange, readOnly }: {
  value: string; onChange: (value: string) => void; readOnly: boolean;
}) {
  const container = useRef<HTMLDivElement>(null);
  const editor = useRef<import('monaco-editor').editor.IStandaloneCodeEditor | null>(null);
  const currentValue = useRef(value);
  const change = useRef(onChange);
  const locked = useRef(readOnly);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => { change.current = onChange; }, [onChange]);
  useEffect(() => {
    currentValue.current = value;
    if (editor.current && editor.current.getValue() !== value) editor.current.setValue(value);
  }, [value]);
  useEffect(() => { locked.current = readOnly; editor.current?.updateOptions({ readOnly }); }, [readOnly]);
  useEffect(() => {
    let disposed = false;
    let instance: import('monaco-editor').editor.IStandaloneCodeEditor | undefined;
    // Vite emits local chunks; intranet deployments do not use a CDN loader.
    void import('monaco-editor').then(monaco => {
      if (disposed || !container.current) return;
      instance = monaco.editor.create(container.current, {
        value: currentValue.current, language: 'java', theme: 'vs', readOnly: locked.current,
        fontSize: 14, minimap: { enabled: false }, automaticLayout: true,
        scrollBeyondLastLine: false, tabSize: 4,
      });
      editor.current = instance;
      instance.onDidChangeModelContent(() => change.current(instance?.getValue() ?? ''));
    }).catch(() => { if (!disposed) setFailed(true); });
    return () => { disposed = true; instance?.dispose(); editor.current = null; };
  }, [attempt]);
  return <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
    <div ref={container} style={{ height: '100%' }} />
    {failed && <Space style={{ position: 'absolute', inset: 16, alignItems: 'start' }}>
      <Typography.Text type="danger">编辑器加载失败</Typography.Text>
      <Button onClick={() => { setFailed(false); setAttempt(value => value + 1); }}>重试</Button>
    </Space>}
  </div>;
}
