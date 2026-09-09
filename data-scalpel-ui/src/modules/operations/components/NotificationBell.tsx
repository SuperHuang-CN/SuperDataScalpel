import { BellOutlined, CheckOutlined } from '@ant-design/icons';
import { Badge, Button, Drawer, Empty, Pagination, Space, Spin, Tooltip, message } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { useNotifications, useOperationsMutation, useUnreadNotifications } from '../hooks/useOperations';
import { readNotification } from '../api/operationsApi';
import { eventLabels, type InAppNotification } from '../model/operations';
import './operations.css';

export const NotificationBell = () => {
  const [open, setOpen] = useState(false); const [page, setPage] = useState(0); const unread = useUnreadNotifications();
  const query = useNotifications({ page, size: 10, sort: '-createdAt' }, open); const read = useOperationsMutation(readNotification);
  const navigate = useNavigate(); const [notice, context] = message.useMessage();
  const mark = async (n: InAppNotification, view: boolean) => {
    try {
      if (!n.readAt) await read.mutateAsync(n.id);
      if (view) { setOpen(false); navigate(`/operations/alerts?incident=${n.incidentId}`); }
    } catch (error) { notice.error(error instanceof Error ? error.message : '通知更新失败'); }
  };
  return <>{context}<Tooltip title={unread.error ? '通知未读数暂不可用' : '站内通知'}>
    <Badge count={unread.data?.unreadCount} overflowCount={99} dot={Boolean(unread.error)}><Button type="text" icon={<BellOutlined />} aria-label="打开站内通知" onClick={() => setOpen(true)} /></Badge>
  </Tooltip><Drawer rootClassName="business-overlay business-drawer-overlay" title={<Space><BellOutlined />站内通知</Space>} open={open} onClose={() => setOpen(false)} width={500}
    footer={<Pagination current={page + 1} pageSize={10} total={query.data?.totalElements ?? 0} size="small" showSizeChanger={false} onChange={p => setPage(p - 1)} />}>
    {query.isPending && <Spin />}{query.error && <InlineFeedback tone="error" label="通知加载失败" detail={query.error.message} action={<Button type="link" onClick={() => void query.refetch()}>重试</Button>} />}
    <div className="ops-notification-list">{query.data?.content.map(n => <div className="ops-notification-row" key={n.id}>
      <div><Badge dot={!n.readAt}><Button type="link" style={{ padding: 0, height: 'auto', textAlign: 'left', whiteSpace: 'normal' }} onClick={() => void mark(n, true)}>{n.summary}</Button></Badge>
        <div>{eventLabels[n.eventType]} · {formatManagementDateTime(n.createdAt)}</div></div>
      {!n.readAt && <Tooltip title="标记已读"><Button type="text" size="small" icon={<CheckOutlined />} aria-label={`标记通知已读：${n.summary}`} loading={read.isPending && read.variables === n.id} onClick={() => void mark(n, false)} /></Tooltip>}
    </div>)}</div>
    {query.data?.content.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无通知" />}
  </Drawer></>;
};
