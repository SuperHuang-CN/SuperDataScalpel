import { Button, Tag } from 'antd';
import { useLayoutEffect, useRef, useState } from 'react';
import { toolStatus } from '../model/toolResult';
import { Markdown } from './Markdown';
import { object, type Message } from '../model/types';
import { SentAttachment } from './Attachments';

export function ToolCard({ name, args, result, ended }: { name: string; args?: unknown; result?: unknown; ended?: boolean }) {
  const status = !result && ended ? { text: '无完整结果，请核对状态', color: 'warning' } : toolStatus(result);
  return <details className="dsh-tool"><summary><span>{name.replace(/^mcp__.+?__/, '')}</span><Tag color={status.color}>{status.text}</Tag></summary>
    <div className="dsh-tool-details"><strong>参数</strong><pre>{typeof args === 'string' ? args : JSON.stringify(args, null, 2)}</pre>
      {result !== undefined && <><strong>返回结果</strong><pre>{JSON.stringify(result, null, 2)}</pre></>}
    </div></details>;
}
export function Messages({ user, sessionId, items, text, tools, running, older, loadOlder, children }: {
  user: string; sessionId: string;
  items: Message[]; text: string; tools: Record<string, unknown>; running: boolean; older: boolean;
  loadOlder: () => Promise<void>; children?: React.ReactNode;
}) {
  const scroll = useRef<HTMLDivElement>(null); const following = useRef(true); const anchor = useRef<number | null>(null);
  const [newMessages, setNewMessages] = useState(false); const [loading, setLoading] = useState(false); const [error, setError] = useState('');
  const results = new Map<string, unknown>(); const calls = new Set<string>();
  for (const msg of items) for (const block of msg.content) {
    const b = object(block);
    if (b.type === 'tool-result') results.set(String(b.toolCallId), b);
    if (b.type === 'tool-call') calls.add(String(b.id));
  }
  useLayoutEffect(() => {
    const el = scroll.current;
    if (!el) return;
    if (anchor.current !== null) { el.scrollTop += el.scrollHeight - anchor.current; anchor.current = null; return; }
    if (following.current) el.scrollTop = el.scrollHeight;
    else setNewMessages(true);
  }, [items, text, tools, children]);
  return <div className="dsh-message-area"><div className="dsh-messages" ref={scroll} onScroll={() => {
    const el = scroll.current!; following.current = el.scrollHeight - el.scrollTop - el.clientHeight < 72;
    if (following.current) setNewMessages(false);
  }}>
    {older && <Button block type="text" loading={loading} onClick={() => {
      anchor.current = scroll.current?.scrollHeight ?? null; setLoading(true); setError('');
      void loadOlder().catch(e => { anchor.current = null; setError(e instanceof Error ? e.message : '历史读取失败'); }).finally(() => setLoading(false));
    }}>加载更早的消息</Button>}
    {error && <div role="alert" className="dsh-error">{error}</div>}
    {items.filter(m => m.role !== 'tool').map(msg => <article className={`dsh-message dsh-message-${msg.role}`} key={msg.id}>
      <div className="dsh-message-label">{msg.role === 'user' ? '你' : 'AI 助手'}</div>
      {msg.attachments?.map(attachment => <SentAttachment key={attachment.id} user={user} sessionId={sessionId} attachment={attachment} />)}
      {msg.content.map((block, index) => { const b = object(block);
        if (b.type === 'text') return <Markdown key={index} text={String(b.text ?? '')} />;
        if (b.type === 'tool-call') return <ToolCard key={String(b.id)} name={String(b.name)} args={b.arguments} result={results.get(String(b.id))} ended={!running} />;
        return null;
      })}</article>)}
    {text && <article className="dsh-message dsh-message-assistant"><div className="dsh-message-label">AI 助手 · 正在回答</div><Markdown text={text} /></article>}
    {Object.entries(tools).filter(([id]) => !calls.has(id)).map(([id, value]) => { const tool = object(value); return <ToolCard key={id} name={String(tool.name)} args={tool.arguments} />; })}
    {children}
  </div>{newMessages && <Button className="dsh-new-messages" size="small" onClick={() => {
    following.current = true; setNewMessages(false); if (scroll.current) scroll.current.scrollTop = scroll.current.scrollHeight;
  }}>有新消息 ↓</Button>}</div>;
}
