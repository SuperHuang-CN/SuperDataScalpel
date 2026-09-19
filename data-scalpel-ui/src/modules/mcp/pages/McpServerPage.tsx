import { writeClipboardText } from '../../../shared/browser/writeClipboardText';
import { CopyOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Input, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { findDirectoryDescendantIds, DirectoryTreePanel, useDirectoryTree } from '../../directory';
import { useCurrentUser } from '../../system';
import { McpServerDrawer } from '../components/McpServerDrawer';
import { useMcpServers } from '../hooks/useMcp';
import { mcpStatusLabels, type McpServer } from '../model/mcp';
import './mcp.css';
const escape = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');
export const McpServerPage = () => {
    const navigate = useNavigate();
    const [messageApi, messageContext] = message.useMessage();
    const [keyword, setKeyword] = useState('');
    const [appliedKeyword, setAppliedKeyword] = useState('');
    const [directory, setDirectory] = useState<string | null | undefined>(undefined);
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(20);
    const [editor, setEditor] = useState<McpServer | null | undefined>(undefined);
    const user = useCurrentUser();
    const permissions = new Set(user.data?.permissions ?? []);
    const directories = useDirectoryTree('MCP_SERVER', permissions.has('directory.view'));
    const search = useMemo(() => { const conditions: string[] = []; if (appliedKeyword.trim())
        conditions.push(`(name:*"${escape(appliedKeyword.trim())}"* OR code:*"${escape(appliedKeyword.trim())}"*)`); if (directory === null)
        conditions.push('directoryId:null');
    else if (directory) {
        const ids = findDirectoryDescendantIds(directories.data ?? [], directory);
        if (ids.length)
            conditions.push(`(${ids.map(id => `directoryId:"${id}"`).join(' OR ')})`);
    } return conditions.join(' AND ') || undefined; }, [appliedKeyword, directory, directories.data]);
    const query = useMcpServers({ page, size, sort: '-updatedAt', search });
    const columns = [
        { title: 'MCP Server', key: 'name', render: (_: unknown, item: McpServer) => <div><Typography.Link strong onClick={() => navigate(`/mcp-management/${item.id}`)}>{item.name}</Typography.Link><div className="mcp-secondary">{item.code}</div></div> },
        { title: '状态', dataIndex: 'status', width: 110, render: (value: McpServer['status']) => <Tag color={value === 'ENABLED' ? 'success' : value === 'DISABLED' ? 'warning' : 'default'}>{mcpStatusLabels[value]}</Tag> },
        { title: 'Tools', dataIndex: 'toolCount', width: 90 },
        { title: '发布', width: 150, render: (_: unknown, item: McpServer) => <Space size={4}><span>{item.publishedVersion ? `v${item.publishedVersion}` : '未发布'}</span>{item.unpublishedChanges && <Tag color="processing">有改动</Tag>}</Space> },
        { title: '访问地址', dataIndex: 'endpoint', ellipsis: true, render: (value: string) => <Space size={4}><Typography.Text ellipsis style={{ maxWidth: 260 }}>{value}</Typography.Text><Tooltip title="复制地址"><Button type="text" size="small" icon={<CopyOutlined />} onClick={() => void writeClipboardText(value).then(() => messageApi.success('已复制'))}/></Tooltip></Space> },
        { title: '操作', width: 80, fixed: 'right' as const, render: (_: unknown, item: McpServer) => permissions.has('mcp.update') ? <Button type="text" icon={<EditOutlined />} aria-label="编辑" onClick={() => setEditor(item)}/> : null },
    ];
    return <div className={permissions.has('directory.view') ? 'directory-management-layout' : 'page-stack'}>
    {messageContext}
    {permissions.has('directory.view') && <DirectoryTreePanel scope="MCP_SERVER" label="MCP 目录" tree={directories.data ?? []} loading={directories.isLoading} selection={directory} canManage={permissions.has('directory.manage')} onSelectionChange={value => { setDirectory(value); setPage(0); }}/>}
    <section className="management-workbench mcp-management-workbench">
      <div className="management-toolbar"><div><Typography.Title level={4} style={{ margin: 0 }}>MCP Server</Typography.Title><Typography.Text type="secondary">在线开发并发布独立的 Groovy MCP Tools</Typography.Text></div><Space><Tooltip title="刷新"><Button icon={<ReloadOutlined />} onClick={() => void query.refetch()}/></Tooltip>{permissions.has('mcp.create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditor(null)}>新建 Server</Button>}</Space></div>
      <div className="management-filters"><Input.Search allowClear placeholder="搜索名称或编码" value={keyword} onChange={e => setKeyword(e.target.value)} onSearch={() => { setAppliedKeyword(keyword); setPage(0); }} autoComplete="off" style={{ width: 320 }}/></div>
      {query.isError && <Alert type="error" showIcon message="MCP Server 加载失败" description={query.error instanceof ApiError ? query.error.message : undefined} action={<Button onClick={() => void query.refetch()}>重试</Button>}/>}
      <div className="management-table"><Table rowKey="id" columns={columns} dataSource={query.data?.content ?? []} loading={query.isLoading} pagination={{ current: page + 1, pageSize: size, total: query.data?.totalElements ?? 0, showSizeChanger: true, onChange: (p, s) => { setPage(s === size ? p - 1 : 0); setSize(s); } }} scroll={{ x: 900, y: 'calc(100vh - 350px)' }}/></div>
    </section>
    <McpServerDrawer open={editor !== undefined} server={editor} defaultDirectoryId={directory ?? undefined} directories={directories.data ?? []} onClose={() => setEditor(undefined)} onCreated={(server) => { setEditor(undefined); navigate(`/mcp-management/${server.id}`); }}/>
  </div>;
};
