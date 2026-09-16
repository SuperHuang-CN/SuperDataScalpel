import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  LoadingOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  ReloadOutlined,
  SendOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Table, Tooltip, type TableProps } from 'antd';
import {
  ManagementCode,
  ManagementDateTime,
  ManagementListCell,
  ManagementStatusIndicator,
  type ManagementStatusTone,
} from '../../../shared/components/ManagementListCells';
import type { DataModelListActions } from '../hooks/useDataModelListActions';
import {
  DEFAULT_DATA_MODEL_PAGE_SIZE,
  type DataModelListState,
} from '../hooks/useDataModelListState';
import {
  dataModelStatusLabels,
  type DataModel,
  type DataModelStatus,
} from '../model/dataModel';
import { DataModelPhysicalStatisticsCell } from './DataModelPhysicalStatisticsCell';
import { ModelWarehouseLayerIcon } from './ModelWarehouseLayerIcon';

const statusColor: Record<DataModelStatus, ManagementStatusTone> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DISABLED: 'warning',
};

const lifecycleIcon = (status: DataModelStatus) => (
  status === 'PUBLISHED' ? <PauseCircleOutlined /> : <SendOutlined />
);

const lifecycleLabel = (status: DataModelStatus) => (
  status === 'PUBLISHED' ? '停用' : '发布'
);

export const DataModelListTable = ({
  list,
  actions,
  canUpdate,
  canDelete,
  canPublish,
  onOpenDetail,
  onEdit,
}: {
  list: DataModelListState;
  actions: DataModelListActions;
  canUpdate: boolean;
  canDelete: boolean;
  canPublish: boolean;
  onOpenDetail: (model: DataModel, tab?: 'basic' | 'fields') => void;
  onEdit: (model: DataModel) => void;
}) => {
  const columns: TableProps<DataModel>['columns'] = [
    {
      title: '模型',
      dataIndex: 'name',
      width: 245,
      render: (value: string, model: DataModel) => (
        <ManagementListCell
          icon={model.warehouseLayer
            ? <ModelWarehouseLayerIcon
              code={model.warehouseLayer.code}
              color={model.warehouseLayer.color}
            />
            : <TableOutlined />}
          iconLabel={model.warehouseLayer
            ? `${model.name}所属数仓分层：${model.warehouseLayer.code}`
            : `${model.name}尚未设置数仓分层`}
          iconTone="slate"
          primary={(
            <Button
              type="link"
              size="small"
              className="data-model-name-button"
              onClick={() => onOpenDetail(model)}
            >
              {value}
            </Button>
          )}
          secondary={<><ManagementCode value={model.code} /> {model.description || ''}</>}
        />
      ),
    },
    {
      title: '状态',
      width: 100,
      render: (_value, model) => (
        <ManagementStatusIndicator
          label={dataModelStatusLabels[model.status]}
          tone={statusColor[model.status]}
        />
      ),
    },
    {
      title: '存储位置',
      width: 310,
      render: (_value, model) => (
        <ManagementListCell
          primary={model.storageDataSourceName}
          secondary={<ManagementCode value={model.physicalTableName} />}
        />
      ),
    },
    {
      title: '模式 / 版本',
      width: 120,
      render: (_value, model) => (
        <ManagementListCell
          primary={model.physicalTableMode === 'MANAGED' ? '托管表' : '外部表'}
          secondary={`Schema v${model.schemaVersion}`}
        />
      ),
    },
    {
      title: '数据统计',
      width: 180,
      align: 'right',
      render: (_value, model) => (
        <DataModelPhysicalStatisticsCell statistics={model.physicalStatistics} />
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 160,
      render: (value: string) => <ManagementDateTime value={value} />,
    },
    {
      title: '操作',
      key: 'actions',
      width: 112,
      render: (_value, model) => (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            {canUpdate && (
              <Tooltip title={model.status === 'DRAFT' ? '字段管理' : '查看字段'}>
                <Button
                  type="text"
                  size="small"
                  icon={<TableOutlined />}
                  aria-label={`${model.status === 'DRAFT' ? '管理' : '查看'}${model.name}字段`}
                  disabled={actions.refreshingModelIds.has(model.id)}
                  onClick={() => onOpenDetail(model, 'fields')}
                />
              </Tooltip>
            )}
            {canUpdate && (
              <Tooltip title="修改模型">
                <Button
                  type="text"
                  size="small"
                  icon={<EditOutlined />}
                  aria-label={`修改${model.name}`}
                  disabled={actions.refreshingModelIds.has(model.id)}
                  onClick={() => onEdit(model)}
                />
              </Tooltip>
            )}
          </div>
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'refresh-statistics',
                  label: actions.refreshingModelIds.has(model.id) ? '刷新统计中…' : '刷新统计',
                  icon: <ReloadOutlined />,
                  disabled: actions.refreshingModelIds.has(model.id),
                },
                ...(canUpdate ? [
                  {
                    key: 'fields',
                    label: model.status === 'DRAFT' ? '字段管理' : '查看字段',
                    icon: <TableOutlined />,
                  },
                  { key: 'edit', label: '修改模型', icon: <EditOutlined /> },
                ] : []),
                ...(canPublish ? [{
                  key: 'lifecycle',
                  label: actions.lifecycleLoading(model)
                    ? `${lifecycleLabel(model.status)}中…`
                    : lifecycleLabel(model.status),
                  icon: actions.lifecycleLoading(model)
                    ? <LoadingOutlined spin />
                    : lifecycleIcon(model.status),
                  disabled: actions.lifecycleLoading(model),
                }] : []),
                ...(model.physicalTableMode === 'MANAGED'
                  ? [{ key: 'export', label: '导出 Excel 结构', icon: <DownloadOutlined /> }]
                  : []),
                ...(canDelete
                  ? [{ key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true }]
                  : []),
              ],
              onClick: ({ key }) => {
                if (key === 'refresh-statistics') void actions.refreshModelStatistics(model);
                if (key === 'fields') onOpenDetail(model, 'fields');
                if (key === 'edit') onEdit(model);
                if (key === 'lifecycle') actions.transition(model);
                if (key === 'export') void actions.exportModels([model.id]);
                if (key === 'delete') actions.remove(model);
              },
            }}
          >
            <Tooltip title="更多操作">
              <Button
                className="management-row-actions-more"
                type="text"
                size="small"
                icon={actions.lifecycleLoading(model)
                  ? <LoadingOutlined spin />
                  : <MoreOutlined />}
                aria-label={`${model.name}的更多操作`}
                loading={actions.refreshingModelIds.has(model.id)}
              />
            </Tooltip>
          </Dropdown>
        </div>
      ),
    },
  ];

  return (
    <Table<DataModel>
      size="small"
      className="management-table"
      rowKey="id"
      columns={columns}
      dataSource={list.modelsQuery.data?.content ?? []}
      loading={list.modelsQuery.isFetching && actions.refreshingModelIds.size === 0}
      rowSelection={{
        preserveSelectedRowKeys: true,
        selectedRowKeys: list.selectedModelIds,
        onChange: list.updateSelection,
      }}
      scroll={{ y: '100%' }}
      pagination={{
        current: list.page + 1,
        pageSize: list.size,
        total: list.modelsQuery.data?.totalElements ?? 0,
        size: 'small',
        placement: ['bottomEnd'],
        hideOnSinglePage: false,
        showSizeChanger: true,
        showTotal: (total) => `共 ${total} 项`,
      }}
      onChange={(pagination) => list.changePage(
        (pagination.current ?? 1) - 1,
        pagination.pageSize ?? DEFAULT_DATA_MODEL_PAGE_SIZE,
      )}
    />
  );
};
