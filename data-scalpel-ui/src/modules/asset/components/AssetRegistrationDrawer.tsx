import { SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Drawer, Input, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
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
        rootClassName="business-overlay business-drawer-overlay asset-registration-drawer"
        title="登记资产"
        open={open}
        width={860}
        onClose={close}
        destroyOnHidden
        footer={<Space><Button onClick={close}>取消</Button><Button type="primary" disabled={!selectedIds.length} loading={registerMutation.isPending} onClick={() => void submit()}>登记所选 {selectedIds.length ? `(${selectedIds.length})` : ''}</Button></Space>}
      >
        <Alert showIcon type="info" message="登记只创建资产草稿，不会修改或发布原资源。" />
        <div className="asset-registration-toolbar">
          <Select<AssetType>
            value={assetType}
            options={assetTypeOptions}
            onChange={(value) => { setAssetType(value); setPage(1); setSelectedIds([]); }}
          />
          <Input
            allowClear
            prefix={<SearchOutlined />}
            value={keywordDraft}
            placeholder="搜索名称或编码"
            onChange={(event) => setKeywordDraft(event.target.value)}
            onPressEnter={() => { setKeyword(keywordDraft || undefined); setPage(1); }}
          />
          <Button type="primary" onClick={() => { setKeyword(keywordDraft || undefined); setPage(1); }}>查询</Button>
          <Button type="text" onClick={() => { setKeywordDraft(''); setKeyword(undefined); setPage(1); }}>重置</Button>
        </div>
        {candidatesQuery.isError && <Alert showIcon type="error" message="候选资源加载失败" description={candidatesQuery.error instanceof Error ? candidatesQuery.error.message : undefined} />}
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
      </Drawer>
    </>
  );
};
