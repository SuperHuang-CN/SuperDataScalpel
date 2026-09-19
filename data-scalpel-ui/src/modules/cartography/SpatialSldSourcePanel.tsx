import { writeClipboardText } from '../../shared/browser/writeClipboardText';
import { CopyOutlined, ExpandOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Collapse, Modal, Space, Spin, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { InlineFeedback } from '../../shared/components/ContextualFeedback';
import { MonacoSqlEditor, type MonacoSqlEditorHandle } from '../../shared/components/MonacoSqlEditor';
import type { SpatialGeometryFamily, SpatialStyleDocument, SpatialStyleMode } from './model';

interface SpatialSldSourcePanelProps {
  mode: SpatialStyleMode;
  geometryFamily: SpatialGeometryFamily;
  document: SpatialStyleDocument | null;
  file: File | null;
  uploadedSldText: string | null;
  onQuerySld(document: SpatialStyleDocument, signal: AbortSignal): Promise<string>;
}

const ignoreReadOnlyChange = () => undefined;

export const SpatialSldSourcePanel = (props: SpatialSldSourcePanelProps) => {
  const { mode, geometryFamily, document, file, uploadedSldText, onQuerySld } = props;
  const [expanded, setExpanded] = useState(false);
  const [enlarged, setEnlarged] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [messageApi, messageContext] = message.useMessage();
  const editorRef = useRef<MonacoSqlEditorHandle>(null);
  const modalEditorRef = useRef<MonacoSqlEditorHandle>(null);
  const input = useMemo(() => ({ mode, geometryFamily, document, file, uploadedSldText, onQuerySld, attempt }),
    [mode, geometryFamily, document, file, uploadedSldText, onQuerySld, attempt]);
  const [result, setResult] = useState<{ input: typeof input; text?: string; error?: string }>();
  const emptyHint = mode === 'CARTOGRAPHY'
    ? geometryFamily === 'GENERIC' ? '使用 GeoServer 内置 generic 样式' : !document ? '请先配置在线制图样式' : undefined
    : !file && !uploadedSldText
      ? geometryFamily === 'GENERIC' ? '使用 GeoServer 内置 generic 样式' : '尚未上传 SLD 文件'
      : undefined;

  useEffect(() => {
    if (emptyHint || input.mode === 'CARTOGRAPHY' && !expanded) return;
    const controller = new AbortController();
    const readSource = async () => {
      try {
        let text: string;
        if (input.mode === 'CARTOGRAPHY') {
          if (!input.document) return;
          text = await input.onQuerySld(input.document, controller.signal);
        } else if (input.file) {
          if (!/\.(sld|xml)$/i.test(input.file.name)) throw new Error('仅支持 .sld 或 .xml 文件');
          if (input.file.size > 512 * 1024) throw new Error('SLD 文件不能超过 512KB');
          if (input.file.size === 0) throw new Error('SLD 文件不能为空');
          const bytes = await input.file.arrayBuffer();
          try {
            text = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes);
          } catch {
            throw new Error('SLD 文件必须使用 UTF-8 编码');
          }
        } else {
          text = input.uploadedSldText ?? '';
        }
        if (!controller.signal.aborted) setResult({ input, text });
      } catch (error: unknown) {
        if (!controller.signal.aborted) setResult({ input, error: error instanceof Error ? error.message : '读取 SLD 源码失败' });
      }
    };
    // Uploaded files are read immediately, even while the source section is collapsed.
    const timer = input.mode === 'CARTOGRAPHY' ? window.setTimeout(() => void readSource(), 450) : undefined;
    if (input.mode === 'UPLOADED_SLD') void readSource();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [input, expanded, emptyHint]);

  // Hide an old result immediately when the draft changes, before effect cleanup runs.
  const current = result?.input === input ? result : undefined;
  const text = emptyHint ? undefined : current?.text;
  const copySource = async () => {
    if (text == null) return;
    try {
      await writeClipboardText(text);
      void messageApi.success('SLD 源码已复制');
    } catch {
      void messageApi.error('复制失败，请在源码中选择文本后复制');
      (enlarged ? modalEditorRef : editorRef).current?.focus();
    }
  };
  const searchSource = () => editorRef.current?.openFind();
  const searchEnlargedSource = () => modalEditorRef.current?.openFind();
  const content = (large: boolean) => <div className="cartography-sld-content">
    <div className="cartography-sld-toolbar">
      <Tag>{mode === 'UPLOADED_SLD' ? file ? '待保存校验' : '已保存上传原文' : '当前配置草稿'}</Tag>
      <Space size={4} wrap>
        <Button size="small" icon={<SearchOutlined />} disabled={text == null}
          onClick={large ? searchEnlargedSource : searchSource}>搜索</Button>
        <Button size="small" icon={<CopyOutlined />} disabled={text == null} onClick={() => void copySource()}>复制</Button>
        {!large && <Button size="small" icon={<ExpandOutlined />} onClick={() => setEnlarged(true)}>放大</Button>}
      </Space>
    </div>
    {emptyHint ? <Typography.Text type="secondary">{emptyHint}</Typography.Text>
      : current?.error ? <InlineFeedback tone="error" label="SLD 源码读取失败" detail={current.error}
        action={<Button size="small" onClick={() => setAttempt(value => value + 1)}>重试</Button>} />
        : text == null ? <Space><Spin size="small" /><Typography.Text type="secondary">正在生成或读取 SLD…</Typography.Text></Space>
          : <MonacoSqlEditor ref={large ? modalEditorRef : editorRef} value={text} language="xml" readOnly
            height={large ? '65vh' : 300} onChange={ignoreReadOnlyChange} />}
  </div>;

  return <div className="cartography-sld-source">{messageContext}
    <Collapse size="small" activeKey={expanded ? ['source'] : []}
      onChange={keys => { setExpanded(keys.includes('source')); if (!keys.includes('source')) setEnlarged(false); }}
      items={[{ key: 'source', label: 'SLD 源码（只读）', children: content(false) }]} />
    <Modal title="SLD 源码（当前草稿，只读）" open={enlarged} onCancel={() => setEnlarged(false)}
      footer={null} width="min(1100px, calc(100vw - 32px))" className="business-modal cartography-sld-modal" destroyOnHidden>
      {content(true)}
    </Modal>
  </div>;
};
