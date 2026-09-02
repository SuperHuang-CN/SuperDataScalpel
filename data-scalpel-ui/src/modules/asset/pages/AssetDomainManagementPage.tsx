import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { ApartmentOutlined, FolderOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { Button, Descriptions, Empty, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { DirectoryTreePanel, useDirectoryTree, type DirectorySelection, type DirectoryTreeNode } from '../../directory';
import { useCurrentUser } from '../../system';
import { ApiError } from '../../../shared/api/http';
import './assetDomainManagement.css';

interface DomainContext {
  node: DirectoryTreeNode;
  ancestors: DirectoryTreeNode[];
}

const findDomainContext = (
  nodes: DirectoryTreeNode[],
  id: string,
  ancestors: DirectoryTreeNode[] = [],
): DomainContext | undefined => {
  for (const node of nodes) {
    if (node.id === id) return { node, ancestors };
    const nested = findDomainContext(node.children, id, [...ancestors, node]);
    if (nested) return nested;
  }
  return undefined;
};

const countDescendants = (node: DirectoryTreeNode): number => node.children.reduce(
  (total, child) => total + 1 + countDescendants(child),
  0,
);

export const AssetDomainManagementPage = () => {
  const [selectedDomainId, setSelectedDomainId] = useState<string>();
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canManage = permissions.has('directory.manage');
  const domainsQuery = useDirectoryTree('ASSET');
  const domains = useMemo(() => domainsQuery.data ?? [], [domainsQuery.data]);
  const selectedContext = useMemo(
    () => selectedDomainId ? findDomainContext(domains, selectedDomainId) : undefined,
    [domains, selectedDomainId],
  );

  const selectDomain = (selection: DirectorySelection) => {
    setSelectedDomainId(typeof selection === 'string' ? selection : undefined);
  };

  const renderDetail = () => {
    if (domainsQuery.isError) {
      return (
        <Alert
          showIcon
          type="error"
          message="业务领域加载失败"
          description={domainsQuery.error instanceof ApiError ? domainsQuery.error.message : '请稍后刷新页面重试。'}
        />
      );
    }

    if (!domainsQuery.isFetching && domains.length === 0) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={canManage ? '还没有业务领域，请从左侧新建顶级业务领域。' : '还没有可查看的业务领域。'}
        />
      );
    }

    if (!selectedContext) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="从左侧选择一个业务领域查看详情。"
        />
      );
    }

    const { node, ancestors } = selectedContext;
    const parent = ancestors.at(-1);
    const domainPath = [...ancestors, node].map((domain) => domain.name).join(' / ');

    return (
      <div className="asset-domain-detail-content">
        <div className="asset-domain-detail-identity">
          <span className="asset-domain-detail-icon"><FolderOutlined /></span>
          <div>
            <div className="asset-domain-detail-title-row">
              <Typography.Title level={4}>{node.name}</Typography.Title>
              <Tag color="blue">第 {ancestors.length + 1} 级</Tag>
            </div>
            <Typography.Text type="secondary">{domainPath}</Typography.Text>
          </div>
        </div>

        <Descriptions bordered column={{ xs: 1, md: 2 }} size="small" className="asset-domain-descriptions">
          <Descriptions.Item label="上级领域">{parent?.name ?? '顶级领域'}</Descriptions.Item>
          <Descriptions.Item label="显示排序">{node.sortOrder}</Descriptions.Item>
          <Descriptions.Item label="直属子领域">{node.children.length}</Descriptions.Item>
          <Descriptions.Item label="全部后代领域">{countDescendants(node)}</Descriptions.Item>
          <Descriptions.Item label="领域说明" span={2}>{node.description || '—'}</Descriptions.Item>
        </Descriptions>

        <section className="asset-domain-children-section">
          <div className="asset-domain-section-heading">
            <span><ApartmentOutlined />直属子领域</span>
            <Typography.Text type="secondary">{node.children.length} 项</Typography.Text>
          </div>
          {node.children.length > 0 ? (
            <div className="asset-domain-child-list">
              {node.children.map((child) => (
                <Button key={child.id} type="text" onClick={() => setSelectedDomainId(child.id)}>
                  <span><FolderOutlined /></span>
                  <span className="asset-domain-child-name">{child.name}</span>
                  <Typography.Text type="secondary">{countDescendants(child)} 个后代</Typography.Text>
                </Button>
              ))}
            </div>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该领域暂无子领域" />
          )}
        </section>

        <Alert
          showIcon
          type="info"
          icon={<InfoCircleOutlined />}
          message="业务领域用于资产门户的发现分类，与模型、服务等资源的技术管理目录相互独立。"
        />
      </div>
    );
  };

  return (
    <div className="directory-management-layout asset-domain-management-page">
      <DirectoryTreePanel
        scope="ASSET"
        label="业务领域"
        tree={domains}
        loading={domainsQuery.isFetching}
        selection={selectedDomainId}
        canManage={canManage}
        showResourceCounts={false}
        showVirtualNodes={false}
        onSelectionChange={selectDomain}
      />
      <section className="management-results-surface asset-domain-detail-surface">
        <div className="management-result-toolbar">
          <div className="management-result-title">
            领域详情
            <span className="management-result-count">用于维护门户业务分类体系</span>
          </div>
        </div>
        <div className="asset-domain-detail-body">{renderDetail()}</div>
      </section>
    </div>
  );
};
