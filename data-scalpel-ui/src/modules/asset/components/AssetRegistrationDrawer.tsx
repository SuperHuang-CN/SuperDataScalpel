import { AppstoreAddOutlined, DatabaseOutlined, SearchOutlined } from '@ant-design/icons';
import { Badge, Button, Drawer, Input, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useAssetCandidates, useRegisterAssets } from '../hooks/useAssets';
import { assetTypeLabels, type AssetCandidate, type AssetType } from '../model/asset';

interface AssetRegistrationDrawerProps {
  open: boolean;
  onClose: () => void;
}

const assetTypeOptions = (Object.entries(assetTypeLabels) as Array<[AssetType, string]>)
  .map(([value, label]) => ({ value, label }));

export const AssetRegistrationDrawer = ({ open, onClose }: AssetRegistrationDrawerProps) => {
  const [messageApi, contextHolder] = message.useMessage();
  const [assetType, setAssetType] = useState<AssetType>('DATA_MODEL');
  const [keywordDraft, setKeywordDraft] = useState('');
  const [keyword, setKeyword] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const candidatesQuery = useAssetCandidates(assetType, keyword, page - 1, pageSize, open);
  const registerMutation = useRegisterAssets();

  const reset = () => {
    setKeywordDraft('');
    setKeyword(undefined);
    setPage(1);
    setPageSize(20);
    setSelectedIds([]);
  };

  const close = () => {
    reset();
    onClose();
  };

  const currentRows = candidatesQuery.data?.content ?? [];
  const columns: TableProps<AssetCandidate>['columns'] = [
    {
      title: '来源资源',
      render: (_, row) => (
        <div className="asset-primary-cell">
          <Typography.Text strong ellipsis={{ tooltip: row.name }}>{row.name}</Typography.Text>
          <Typography.Text type="secondary" ellipsis={{ tooltip: row.code ?? '无编码' }}>{row.code ?? '无编码'}</Typography.Text>
        </div>
      ),
    },
    { title: '来源状态', dataIndex: 'sourceStatus', width: 130, render: (value: string) => <Tag>{value}</Tag> },
    {
      title: '登记条件',
      width: 220,
      render: (_, row) => row.registered
        ? <Typography.Text type="secondary">已登记</Typography.Text>
        : row.eligible ? <Typography.Text type="success">可登记</Typography.Text>
          : <Typography.Text type="warning">{row.ineligibleReason}</Typography.Text>,
    },
  ];

  const submit = async () => {
    try {
      await registerMutation.mutateAsync({ assetType, resourceIds: selectedIds });
      messageApi.success(`已登记 ${selectedIds.length} 项资产草稿`);
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '登记资产失败');
    }
  };

  return (
    <>
      {contextHolder}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer asset-registration-drawer"
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><AppstoreAddOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>登记数据资产</span>
              <Typography.Text type="secondary">从现有资源中选择对象并创建资产门户草稿</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="data-model-drawer-header-tag">{assetTypeLabels[assetType]}</Tag>}
        open={open}
        width={920}
        onClose={close}
        destroyOnHidden
        footer={(
          <div className="data-model-drawer-footer">
            <Badge status={selectedIds.length ? 'processing' : 'default'} text={selectedIds.length ? `已选择 ${selectedIds.length} 项资源` : '尚未选择资源'} />
            <Space>
              <Button onClick={close}>取消</Button>
              <Button type="primary" disabled={!selectedIds.length} loading={registerMutation.isPending} onClick={() => void submit()}>登记所选 {selectedIds.length ? `(${selectedIds.length})` : ''}</Button>
            </Space>
          </div>
        )}
      >
        <div className="data-model-form asset-registration-workbench">
          <section className="data-model-form-section asset-registration-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><DatabaseOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title-row">
                  <span className="data-model-form-section-title">选择来源资源</span>
                  <ContextHelp ariaLabel="查看资产登记说明" content="登记仅创建资产门户草稿，不会修改、发布或改变原资源状态。" presentation="popover" />
                </span>
                <Typography.Text type="secondary">切换资源类型并筛选可登记对象，已登记或不符合条件的资源不可勾选</Typography.Text>
              </span>
              <Tag className="data-model-drawer-header-tag">共 {candidatesQuery.data?.totalElements ?? 0} 项</Tag>
            </header>
            <div className="asset-registration-section-body">
              <div className="asset-registration-toolbar">
                <Select<AssetType>
                  aria-label="资源类型"
                  value={assetType}
                  options={assetTypeOptions}
                  onChange={(value) => { setAssetType(value); setPage(1); setSelectedIds([]); }}
                />
                <Input
                  name="asset-registration-keyword"
                  autoComplete="off"
                  allowClear
                  prefix={<SearchOutlined />}
                  value={keywordDraft}
                  placeholder="搜索名称或编码"
                  onChange={(event) => setKeywordDraft(event.target.value)}
                  onPressEnter={() => { setKeyword(keywordDraft || undefined); setPage(1); }}
                />
                <Button type="primary" onClick={() => { setKeyword(keywordDraft || undefined); setPage(1); }}>查询</Button>
                {(keywordDraft || keyword) && <Button type="text" onClick={() => { setKeywordDraft(''); setKeyword(undefined); setPage(1); }}>重置</Button>}
              </div>
              {candidatesQuery.isError && <InlineFeedback tone="error" label="候选资源加载失败" detail={candidatesQuery.error instanceof Error ? candidatesQuery.error.message : undefined} />}
              <Table<AssetCandidate>
                size="small"
                rowKey="resourceId"
                columns={columns}
                dataSource={currentRows}
                loading={candidatesQuery.isFetching}
                rowSelection={{
                  preserveSelectedRowKeys: true,
                  selectedRowKeys: selectedIds,
                  getCheckboxProps: (row) => ({ disabled: row.registered || !row.eligible }),
                  onChange: (keys) => setSelectedIds(keys.map(String)),
                }}
                pagination={{
                  current: page,
                  pageSize,
                  total: candidatesQuery.data?.totalElements ?? 0,
                  showSizeChanger: true,
                  hideOnSinglePage: false,
                  showTotal: (total) => `共 ${total} 项`,
                }}
                onChange={(pagination) => {
                  setPage(pagination.current ?? 1);
                  setPageSize(pagination.pageSize ?? 20);
                }}
              />
            </div>
          </section>
        </div>
      </Drawer>
    </>
  );
};
