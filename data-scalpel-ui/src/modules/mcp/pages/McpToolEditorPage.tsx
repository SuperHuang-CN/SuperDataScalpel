import { ArrowLeftOutlined, PlayCircleOutlined, SaveOutlined } from '@ant-design/icons';
import { Button, Checkbox, Form, Input, InputNumber, Modal, Result, Segmented, Select, Space, Typography, message } from 'antd';
import { useCallback, useEffect, useRef, useState, type PointerEvent } from 'react';
import { useBlocker, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useCurrentUser } from '../../system';
import { McpInputSchemaEditor } from '../components/McpInputSchemaEditor';
import { McpScriptEditor } from '../components/McpScriptEditor';
import { useExecuteMcpDraft, useMcpServer, useMcpTool, useSaveMcpTool } from '../hooks/useMcp';
import type { McpTool, SaveMcpToolRequest } from '../model/mcp';
import './mcp.css';

const DEFAULT_SCHEMA = JSON.stringify({ type: 'object', properties: {}, required: [], additionalProperties: false }, null, 2);
const DEFAULT_SCRIPT = '// args: Map<String, Object>\n// context: serverCode / releaseVersion / toolCode\nreturn [message: "hello", received: args]\n';
type Example = Record<string, unknown> & { name?: string; arguments?: unknown };
function parseExamples(json: string): Example[] {
  const value: unknown = JSON.parse(json);
  if (!Array.isArray(value) || value.length > 10 || value.some(item => !item || typeof item !== 'object' || Array.isArray(item))) {
    throw new Error('测试样例必须是最多 10 项的对象数组');
  }
  return value;
}
function initialArguments(tool?: McpTool) {
  try { return JSON.stringify(parseExamples(tool?.examplesJson ?? '[]')[0]?.arguments ?? {}, null, 2); }
  catch { return '{}'; }
}

export function McpToolEditorPage() {
  const { serverId, toolId } = useParams<{ serverId: string; toolId: string }>();
  const server = useMcpServer(serverId);
  const tool = useMcpTool(serverId, toolId);
  const user = useCurrentUser();
  if (server.isLoading || user.isLoading || (toolId !== 'new' && tool.isLoading)) return <div>正在加载 Tool…</div>;
  if ((server.isError && !server.data) || (toolId !== 'new' && tool.isError && !tool.data)) return <Result status="error" title="Tool 加载失败"
    subTitle={(tool.error ?? server.error) instanceof ApiError ? (tool.error ?? server.error)?.message : '请重试，尚未加载的定义不能编辑。'}
    extra={<Button onClick={() => { void server.refetch(); void tool.refetch(); }}>重试</Button>} />;
  if (!serverId || !toolId || !server.data || (toolId !== 'new' && !tool.data)) return <Result status="404" title="Tool 不存在" />;
  const permissions = new Set(user.data?.permissions ?? []);
  if (!permissions.has('mcp.update')) return <Result status="403" title="没有 Tool 编辑权限" />;
  // Mount once per identity. Background query refreshes never hydrate an active local draft.
  return <ToolEditor key={serverId + '/' + toolId} serverId={serverId} toolId={toolId}
    serverName={server.data.name} initialTool={tool.data} canExecute={permissions.has('mcp.execute')} />;
}

function ToolEditor({ serverId, toolId, serverName, initialTool, canExecute }: {
  serverId: string; toolId: string; serverName: string; initialTool?: McpTool; canExecute: boolean;
}) {
  const navigate = useNavigate();
  const [messageApi, messageContext] = message.useMessage();
  const [modal, modalContext] = Modal.useModal();
  const save = useSaveMcpTool(serverId, toolId);
  const execute = useExecuteMcpDraft(serverId);
  const [form] = Form.useForm<SaveMcpToolRequest>();
  const [script, setScript] = useState(() => initialTool?.script ?? DEFAULT_SCRIPT);
  const [inputSchema, setInputSchema] = useState(() => initialTool?.inputSchemaJson ?? DEFAULT_SCHEMA);
  const [outputSchema, setOutputSchema] = useState(() => initialTool?.outputSchemaJson ?? '');
  const [examplesJson, setExamplesJson] = useState(() => initialTool?.examplesJson ?? '[]');
  const [argumentsJson, setArgumentsJson] = useState(() => initialArguments(initialTool));
  const [resultMode, setResultMode] = useState<'result' | 'error' | 'logs'>('result');
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const saveLock = useRef(false);
  const allowLeave = useRef(false);
  const [revision, setRevision] = useState(initialTool?.revision);
  const [resultHeight, setResultHeight] = useState(230);
  const resize = useRef<{ y: number; height: number } | null>(null);
  let examples: Example[] = [];
  try { examples = parseExamples(examplesJson); } catch { /* Keep malformed source editable. */ }

  useEffect(() => {
    const listener = (event: BeforeUnloadEvent) => { if (dirty || saving) event.preventDefault(); };
    window.addEventListener('beforeunload', listener);
    return () => window.removeEventListener('beforeunload', listener);
  }, [dirty, saving]);
  const blocker = useBlocker(useCallback(() => !allowLeave.current && (dirty || saving), [dirty, saving]));
  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    const dialog = modal.confirm({
      title: saving ? '正在保存，请稍后离开' : '放弃未保存的修改？',
      content: saving ? '保存完成前请留在当前页面。' : '当前 Tool 定义尚未保存。',
      okText: saving ? '留在页面' : '放弃并离开',
      cancelText: '继续编辑',
      onOk: () => { if (saving) blocker.reset(); else { allowLeave.current = true; blocker.proceed(); } },
      onCancel: () => blocker.reset(),
    });
    return () => dialog.destroy();
  }, [blocker, modal, saving]);
  const mark = <T,>(setter: (value: T) => void) => (value: T) => { setter(value); setDirty(true); };
  const submit = async () => {
    if (saveLock.current) return;
    saveLock.current = true; setSaving(true);
    try {
      const values = await form.validateFields();
      parseExamples(examplesJson);
      const saved = await save.mutateAsync({
        ...values, expectedRevision: revision, inputSchemaJson: inputSchema,
        outputSchemaJson: outputSchema.trim() || undefined, script, examplesJson,
      });
      setRevision(saved.revision); setDirty(false);
      messageApi.success('Tool 已保存');
      if (toolId === 'new') {
        allowLeave.current = true;
        navigate('/mcp-management/' + serverId + '/tools/' + saved.id + '/edit', { replace: true });
      }
    } catch (error) { messageApi.error(error instanceof Error ? error.message : '请检查表单后重试'); }
    finally { saveLock.current = false; setSaving(false); }
  };
  const run = async () => {
    try {
      const response = await execute.mutateAsync({ inputSchemaJson: inputSchema, outputSchemaJson: outputSchema.trim() || undefined, script, argumentsJson });
      setResultMode(response.success ? 'result' : 'error');
    } catch (error) { messageApi.error(error instanceof ApiError ? error.message : '执行失败'); }
  };
  const saveExample = () => {
    try {
      const current = parseExamples(examplesJson);
      if (current.length >= 10) throw new Error('最多保存 10 个样例，请在样例 JSON 中修改已有内容');
      const args: unknown = JSON.parse(argumentsJson);
      if (!args || typeof args !== 'object' || Array.isArray(args)) throw new Error('测试参数必须是 JSON 对象');
      mark(setExamplesJson)(JSON.stringify([...current, { name: '样例 ' + (current.length + 1), arguments: args }], null, 2));
    } catch (error) { messageApi.error(error instanceof Error ? error.message : '样例格式错误'); }
  };
  const finishResize = (event: PointerEvent<HTMLDivElement>) => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    resize.current = null;
  };
  const execution = execute.data;
  return <div className="mcp-tool-editor">{messageContext}{modalContext}
    <header className="mcp-tool-editor-header">
      <Space><Button type="text" aria-label="返回 Tools" icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/mcp-management/' + serverId + '?tab=tools')} />
        <div><Typography.Title level={4} style={{ margin: 0 }}>{initialTool?.name ?? '新建 Tool'}</Typography.Title>
          <Typography.Text type="secondary">{serverName} · Groovy{dirty ? ' · 未保存' : ''}</Typography.Text></div></Space>
      <Space><Button icon={<PlayCircleOutlined />} disabled={!canExecute || saving} loading={execute.isPending} onClick={() => void run()}>运行测试</Button>
        <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => void submit()}>保存</Button></Space>
    </header>
    <div className="mcp-tool-editor-main" inert={saving}>
      <aside className="mcp-tool-inspector">
        <Form form={form} layout="vertical" autoComplete="off" disabled={saving}
          initialValues={initialTool ?? { code: '', name: '', description: '', enabled: true, sortOrder: 0 }}
          onValuesChange={() => setDirty(true)}>
          <Form.Item name="code" label="Tool 编码" rules={[{ required: true }, { pattern: /^[a-z][a-z0-9_-]{1,63}$/, message: '编码格式不正确' }]}><Input /></Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input.TextArea rows={3} /></Form.Item>
          <Space><Form.Item name="enabled" valuePropName="checked"><Checkbox>参与发布</Checkbox></Form.Item>
            <Form.Item name="sortOrder" label="排序"><InputNumber min={0} max={100000} /></Form.Item></Space>
        </Form>
        <Typography.Title level={5}>Input Schema</Typography.Title>
        <McpInputSchemaEditor value={inputSchema} onChange={mark(setInputSchema)} />
        <Typography.Title level={5}>Output Schema（可选）</Typography.Title>
        <Input.TextArea autoComplete="off" value={outputSchema} onChange={e => mark(setOutputSchema)(e.target.value)} autoSize={{ minRows: 6, maxRows: 16 }} spellCheck={false} />
        <Typography.Title level={5}>本次测试参数</Typography.Title>
        <Space wrap style={{ marginBottom: 8 }}><Select placeholder="载入已保存样例" value={null} style={{ width: 180 }}
          options={examples.map((example, index) => ({ value: index, label: typeof example.name === 'string' ? example.name : '样例 ' + (index + 1) }))}
          onChange={(index: number) => setArgumentsJson(JSON.stringify(examples[index].arguments ?? {}, null, 2))} />
          <Button onClick={saveExample}>添加为样例</Button></Space>
        <Input.TextArea autoComplete="off" value={argumentsJson} onChange={e => setArgumentsJson(e.target.value)} autoSize={{ minRows: 6, maxRows: 16 }} spellCheck={false} />
        <Typography.Text type="secondary">临时参数仅用于本次测试；添加为样例后随 Tool 保存。</Typography.Text>
        <Typography.Title level={5}>测试样例 JSON（最多 10 项）</Typography.Title>
        <Input.TextArea autoComplete="off" value={examplesJson} onChange={e => mark(setExamplesJson)(e.target.value)} autoSize={{ minRows: 5, maxRows: 16 }} spellCheck={false} />
      </aside>
      <section className="mcp-tool-code"><div className="mcp-code-title"><span>Groovy Script</span><Typography.Text type="secondary">args · context · log · json</Typography.Text></div>
        <McpScriptEditor value={script} onChange={mark(setScript)} readOnly={saving} /></section>
    </div>
    <div className="mcp-result-resizer" role="separator" aria-label="调整结果区高度" onPointerDown={event => {
      resize.current = { y: event.clientY, height: resultHeight }; event.currentTarget.setPointerCapture(event.pointerId);
    }} onPointerMove={event => {
      if (resize.current) setResultHeight(Math.max(140, Math.min(520, resize.current.height + resize.current.y - event.clientY)));
    }} onPointerUp={finishResize} onPointerCancel={finishResize} />
    <section className="mcp-tool-result" style={{ height: resultHeight }}>
      <div className="mcp-result-header"><Segmented<'result' | 'error' | 'logs'> value={resultMode} onChange={setResultMode}
        options={[{ label: '运行结果', value: 'result' }, { label: '错误', value: 'error' }, { label: '调试日志', value: 'logs' }]} />
        {execution && <Typography.Text type="secondary">{execution.durationMillis} ms</Typography.Text>}</div>
      {!execution ? <div className="mcp-result-empty">运行测试后在这里查看结果</div> : resultMode === 'error'
        ? <Typography.Text type={execution.error ? 'danger' : 'secondary'}>{execution.error || '没有错误'}</Typography.Text>
        : <pre>{resultMode === 'logs' ? (execution.logs?.join('\n') || '没有调试日志') : execution.text ?? JSON.stringify(execution.structuredContent, null, 2)}</pre>}
    </section>
  </div>;
}
