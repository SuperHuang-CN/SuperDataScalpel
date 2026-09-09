import { ArrowLeftOutlined, EditOutlined, PlusOutlined, PoweroffOutlined, RocketOutlined } from '@ant-design/icons';
import { Button, Modal, Pagination, Result, Space, Table, Tabs, Tag, Typography, message } from 'antd';
import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { ApiError } from '../../../shared/api/http';
import { useDirectoryTree } from '../../directory';
import { useCurrentUser } from '../../system';
import { McpServerDrawer } from '../components/McpServerDrawer';
import { McpServerAccessTokenPanel } from '../components/McpServerAccessTokenPanel';
import { useDeleteMcpTool, useMcpInvocations, useMcpReleases, useMcpServer, useMcpServerAction, useMcpTools } from '../hooks/useMcp';
import { mcpStatusLabels, type McpServerStatus, type McpToolSummary } from '../model/mcp';
import './mcp.css';
const colors: Record<McpServerStatus, string> = { DRAFT: 'default', ENABLED: 'success', DISABLED: 'warning' };
export const McpServerDetailPage = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const [logPage, setLogPage] = useState(0);
    const [logSize, setLogSize] = useState(20);
    const { id } = useParams<{
        id: string;
    }>();
    const navigate = useNavigate();
    const [messageApi, context] = message.useMessage();
    const [modal, modalContext] = Modal.useModal();
    const [edit, setEdit] = useState(false);
    const user = useCurrentUser();
    const permissions = new Set(user.data?.permissions ?? []);
    const serverQuery = useMcpServer(id);
    const tools = useMcpTools(id);
    const releases = useMcpReleases(id);
    const invocations = useMcpInvocations({ page: logPage, size: logSize, sort: '-startedAt', search: id ? `serverId:"${id}"` : undefined });
    const action = useMcpServerAction();
    const removeTool = useDeleteMcpTool(id);
    const server = serverQuery.data;
    const directories = useDirectoryTree('MCP_SERVER', permissions.has('directory.view'));
    if (serverQuery.isLoading)
        return <div className="page-stack">正在加载 MCP Server…</div>;
    if (serverQuery.isError && !server)
        return <Result status="error" title="MCP Server 加载失败" subTitle={serverQuery.error.message} extra={<Button onClick={() => void serverQuery.refetch()}>重试</Button>}/>;
    if (!server)
        return <Result status="404" title="MCP Server 不存在" extra={<Button onClick={() => navigate('/mcp-management')}>返回列表</Button>}/>;
    const doAction = async (type: 'publish' | 'enable' | 'disable') => { try {
        await action.mutateAsync({ id: server.id, action: type });
        messageApi.success(type === 'publish' ? '发布成功' : type === 'enable' ? '已启用' : '已停用');
    }
    catch (e) {
        messageApi.error(e instanceof ApiError ? e.message : '操作失败');
    } };
    const toolColumns = [{ title: 'Tool', render: (_: unknown, t: McpToolSummary) => <div><Typography.Link strong disabled={!permissions.has('mcp.update')} onClick={() => navigate(`/mcp-management/${server.id}/tools/${t.id}/edit`)}>{t.name}</Typography.Link><div className="mcp-secondary">{t.code}</div></div> }, { title: '状态', dataIndex: 'enabled', width: 100, render: (v: boolean) => <Tag color={v ? 'success' : 'default'}>{v ? '参与发布' : '已禁用'}</Tag> }, { title: '修订', dataIndex: 'revision', width: 90, render: (v: number) => `r${v}` }, { title: '操作', width: 140, render: (_: unknown, t: McpToolSummary) => <Space>{permissions.has('mcp.update') && <Button type="link" onClick={() => navigate(`/mcp-management/${server.id}/tools/${t.id}/edit`)}>编辑</Button>}{permissions.has('mcp.delete') && <Button danger type="link" onClick={() => modal.confirm({ title: '删除 Tool？', content: `确认删除“${t.name}”吗？`, okText: '删除', okButtonProps: { danger: true }, onOk: async () => { try {
                    await removeTool.mutateAsync(t.id);
                    messageApi.success('Tool 已删除');
                }
                catch (error) {
                    messageApi.error(error instanceof Error ? error.message : '删除失败');
                    throw error;
                } } })}>删除</Button>}</Space> }];
    const tabs = [
        { key: 'basic', label: '基本信息', children: <BusinessDetailSection title="基本信息" description="Server 身份与调用配置" icon={<RocketOutlined />}><BusinessDetailDescriptions column={2} items={[{ key: 'code', label: '编码', children: server.code }, { key: 'status', label: '状态', children: <Tag color={colors[server.status]}>{mcpStatusLabels[server.status]}</Tag> }, { key: 'endpoint', label: 'MCP 地址', span: 2, children: <Space><Typography.Text copyable>{server.endpoint}</Typography.Text></Space> }, { key: 'description', label: '说明', span: 2, children: server.description || '-' }, { key: 'instructions', label: 'Instructions', span: 2, children: <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{server.instructions || '-'}</Typography.Paragraph> }, { key: 'revision', label: '草稿修订', children: `r${server.draftRevision}` }, { key: 'version', label: '发布版本', children: server.publishedVersion ? `v${server.publishedVersion}` : '未发布' }]}/></BusinessDetailSection> },
        { key: 'tools', label: `Tools (${server.toolCount})`, children: <div className="business-detail-section"><div className="detail-table-toolbar"><Typography.Text type="secondary">发布时会把所有启用的 Tool 固化为不可变快照。</Typography.Text>{permissions.has('mcp.update') && <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`/mcp-management/${server.id}/tools/new/edit`)}>新建 Tool</Button>}</div>{tools.isError && <Space><Typography.Text type="danger">Tools 加载失败</Typography.Text><Button onClick={() => void tools.refetch()}>重试</Button></Space>}<Table rowKey="id" columns={toolColumns} dataSource={tools.data ?? []} loading={tools.isLoading} pagination={false}/></div> },
        { key: 'releases', label: '发布版本', children: <div className="business-detail-section">{releases.isError && <Space><Typography.Text type="danger">发布版本加载失败</Typography.Text><Button onClick={() => void releases.refetch()}>重试</Button></Space>}<Table rowKey="id" dataSource={releases.data ?? []} loading={releases.isLoading} pagination={false} columns={[{ title: '版本', dataIndex: 'version', render: (v: number) => `v${v}` }, { title: 'Tools', dataIndex: 'toolCount' }, { title: '草稿修订', dataIndex: 'draftRevision', render: (v: number) => `r${v}` }, { title: '摘要', dataIndex: 'digest', ellipsis: true }, { title: '发布时间', dataIndex: 'publishedAt' }]}/></div> },
        { key: 'token', label: '访问凭证', children: permissions.has('mcp.token.manage') ? <McpServerAccessTokenPanel serverId={server.id} /> : <div className="business-detail-section"><Typography.Text type="secondary">缺少凭证管理权限</Typography.Text></div> },
        { key: 'logs', label: '调用日志', children: <div className="business-detail-section"><div className="detail-table-toolbar"><Typography.Text>调用日志 · {invocations.data?.totalElements ?? 0} 条</Typography.Text><Space><Pagination simple current={logPage + 1} pageSize={logSize} total={invocations.data?.totalElements ?? 0} showSizeChanger onChange={(page, size) => { setLogPage(size === logSize ? page - 1 : 0); setLogSize(size); }}/><Button onClick={() => void invocations.refetch()}>刷新</Button></Space></div>{invocations.isError && <Space><Typography.Text type="danger">调用日志加载失败</Typography.Text><Button onClick={() => void invocations.refetch()}>重试</Button></Space>}<Table rowKey="id" dataSource={invocations.data?.content ?? []} loading={invocations.isLoading} columns={[{ title: '时间', dataIndex: 'startedAt' }, { title: 'Tool', dataIndex: 'toolCode', render: (v: string | null) => v || '-' }, { title: '访问凭证', render: (_: unknown, item: { accessTokenName: string | null; tokenRevision: number | null }) => item.accessTokenName ? `${item.accessTokenName} · r${item.tokenRevision}` : '—' }, { title: '方法', dataIndex: 'invocationType' }, { title: '状态', dataIndex: 'status', render: (v: string) => <Tag color={v === 'SUCCESS' ? 'success' : 'error'}>{v}</Tag> }, { title: '耗时', dataIndex: 'durationMillis', render: (v: number) => `${v} ms` }]} pagination={false}/></div> },
    ];
    return <div className="page-stack mcp-detail-page">{context}{modalContext}<div className="business-detail-header"><Space><Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/mcp-management')}/><div><Typography.Title level={3} style={{ margin: 0 }}>{server.name}</Typography.Title><Typography.Text type="secondary">{server.code}</Typography.Text></div><Tag color={colors[server.status]}>{mcpStatusLabels[server.status]}</Tag>{server.unpublishedChanges && <Tag color="processing">有未发布改动</Tag>}</Space><Space>{permissions.has('mcp.update') && <Button icon={<EditOutlined />} onClick={() => setEdit(true)}>编辑</Button>}{permissions.has('mcp.publish') && <Button type="primary" icon={<RocketOutlined />} loading={action.isPending} onClick={() => void doAction('publish')}>发布</Button>}{permissions.has('mcp.publish') && (server.status === 'ENABLED' ? <Button danger icon={<PoweroffOutlined />} onClick={() => void doAction('disable')}>停用</Button> : server.publishedVersion && <Button onClick={() => void doAction('enable')}>启用当前版本</Button>)}</Space></div><Tabs activeKey={tabs.some(tab => tab.key === searchParams.get('tab')) ? (searchParams.get('tab') ?? 'basic') : 'basic'} onChange={tab => setSearchParams({ tab }, { replace: true })} items={tabs}/>
    <McpServerDrawer open={edit} server={server} directories={directories.data ?? []} onClose={() => setEdit(false)} onCreated={() => undefined}/>
  </div>;
};
