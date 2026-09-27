import { DownOutlined } from '@ant-design/icons';
import { Button, Dropdown, Space, message, type MenuProps } from 'antd';
import { useState } from 'react';
import { writeClipboardText } from '../../../shared/browser/writeClipboardText';
import type { SparkJarCodeResource } from '../model/sparkJarCodeResource';

export const SparkJarResourceActions = ({ resource, busy, editingAllowed, fieldsExpanded, onEdit, onRead, onWrite, onToggleFields }: {
  resource: SparkJarCodeResource;
  busy: boolean;
  editingAllowed: boolean;
  fieldsExpanded: boolean;
  onEdit: () => void;
  onRead: (print: boolean) => void;
  onWrite: () => void;
  onToggleFields: () => void;
}) => {
  const [open, setOpen] = useState(false);
  const [keyboardOpen, setKeyboardOpen] = useState(false);
  const [copying, setCopying] = useState(false);
  const [feedback, context] = message.useMessage();
  const items: MenuProps['items'] = [];
  if (resource.accessMode !== 'WRITE' && resource.kind !== 'JDBC_CONNECTION') {
    items.push({ key: 'read', label: '插入读取代码', disabled: !editingAllowed });
    if (resource.kind !== 'KAFKA_TOPIC') items.push({ key: 'print', label: '插入读取并打印前 20 行代码', disabled: !editingAllowed });
  }
  if (resource.accessMode !== 'READ') items.push({ key: 'write', label: '插入写入模板', disabled: !editingAllowed });
  if (resource.kind !== 'KAFKA_TOPIC') {
    if (items.length) items.push({ type: 'divider' });
    items.push({ key: 'fields', label: fieldsExpanded ? '收起字段' : '查看字段' });
  }
  const copy = async () => {
    setCopying(true);
    try {
      await writeClipboardText(resource.bindingName);
      void feedback.success('代码引用名已复制');
    } catch {
      void feedback.error('复制失败，请选中上方代码引用名手动复制');
    } finally {
      setCopying(false);
    }
  };
  return <Space size={4} wrap className="spark-jar-online-resource-actions">
    {context}
    <Button size="small" type="link" disabled={busy} onClick={onEdit}>编辑绑定</Button>
    <Button size="small" type="text" loading={copying} onClick={() => void copy()}>复制引用名</Button>
    <Dropdown trigger={['hover', 'click']} open={open} autoFocus={keyboardOpen}
      onOpenChange={(next) => { setOpen(next); if (!next) setKeyboardOpen(false); }}
      menu={{ items, onClick: ({ key }) => {
        setOpen(false);
        setKeyboardOpen(false);
        if (key === 'fields') { onToggleFields(); return; }
        if (!editingAllowed) return;
        requestAnimationFrame(() => {
          if (key === 'read' || key === 'print') onRead(key === 'print');
          if (key === 'write') onWrite();
        });
      } }}>
      <Button size="small" aria-haspopup="menu" aria-expanded={open}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
            event.preventDefault(); setKeyboardOpen(true); setOpen(true);
          }
          if (event.key === 'Escape') { setOpen(false); setKeyboardOpen(false); }
        }}>代码操作 <DownOutlined /></Button>
    </Dropdown>
  </Space>;
};
