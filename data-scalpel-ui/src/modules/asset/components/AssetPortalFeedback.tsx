import { Button, Empty, Skeleton } from 'antd';
import { CompactAlert } from '../../../shared/components/ContextualFeedback';

export const AssetPortalFeedback = ({ pending, error, empty, retry }: { pending?: boolean; error?: Error | null; empty?: string; retry: () => void }) => {
  if (pending) return <div className="portal-loading" role="status" aria-label="正在加载资产"><Skeleton active paragraph={{ rows: 3 }} /></div>;
  if (error) return <CompactAlert type="error" showIcon message="加载失败" description={error.message} action={<Button onClick={retry}>重试</Button>} />;
  return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={empty || '暂无已发布资产'} />;
};

export const AssetPortalFooter = () => <footer className="portal-footer"><div className="asset-portal-container"><span><strong>DataScalpel</strong> 数据资产门户</span><span>连接数据，服务业务</span></div></footer>;
