import { writeClipboardText } from '../../../shared/browser/writeClipboardText';
import type { ReactNode } from 'react';
import { Button, message } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
function textOf(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(textOf).join('');
  if (node && typeof node === 'object' && 'props' in node) return textOf((node.props as {children?: ReactNode}).children);
  return '';
}
function CodeBlock({ children }: { children?: ReactNode }) {
  const [notice, context] = message.useMessage();
  return <div className="dsh-code">{context}<Button type="text" size="small" icon={<CopyOutlined />} aria-label="复制代码" onClick={() => {
    void writeClipboardText(textOf(children)).then(() => notice.success('已复制'), () => notice.error('复制失败'));
  }} /><pre>{children}</pre></div>;
}
export function Markdown({ text }: { text: string }) {
  return <div className="dsh-markdown"><ReactMarkdown remarkPlugins={[remarkGfm]} skipHtml
    urlTransform={url => /^(https?:\/\/|mailto:|\/[^/]|#)/i.test(url) ? url : ''}
    components={{ img: () => null, pre: CodeBlock, a: ({children, href}) => href ? <a href={href} target="_blank" rel="noopener noreferrer">{children}</a> : <span>{children}</span>,
      table: ({children}) => <div className="dsh-table"><table>{children}</table></div> }}>{text}</ReactMarkdown></div>;
}
