import {
  DatabaseOutlined,
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
import { Button, Dropdown, Empty, Table, Tooltip, type TableProps } from 'antd';
import {
  ManagementCode,
  ManagementDateTime,
  ManagementListCell,
  ManagementStatusIndicator,
  type ManagementStatusTone,
} from '../../../shared/components/ManagementListCells';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import { DataSourceTypeIcon, type DataSourceType } from '../../datasource';
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
  storageTypes,
  actions,
  canUpdate,
  canDelete,
  canPublish,
  onOpenDetail,
  onEdit,
}: {
  list: DataModelListState;
  storageTypes: ReadonlyMap<string, DataSourceType>;
  actions: DataModelListActions;
  canUpdate: boolean;
  canDelete: boolean;
  canPublish: boolean;
  onOpenDetail: (model: DataModel, tab?: 'basic' | 'fields') => void;
  onEdit: (model: DataModel) => void;
}) => {
  const columns: TableProps<DataModel>['columns'] = [
    {
      title: '模型 / 分层',
      width: '36%',
      dataIndex: 'name',
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
          className="model-list-identity"
          primary={(
            <div className="model-list-name-row">
            <Tooltip title={value} trigger={['hover', 'focus']}>
              <Button
                type="link"
                size="small"
                className="data-model-name-button"
                onClick={() => onOpenDetail(model)}
              >
                {value}
              </Button>
            </Tooltip>
            <ContextHelp ariaLabel={`${model.name}的编码与说明`} presentation="popover" placement="right"
              content={(
                <div className="model-list-identity-detail">
                  <div><span>编码</span><strong>{model.code}</strong></div>
                  <div><span>数仓分层</span><strong>{model.warehouseLayer ? `${model.warehouseLayer.code} · ${model.warehouseLayer.name}` : '未分层'}</strong></div>
                  <div><span>说明</span><p>{model.description || '暂无说明'}</p></div>
                </div>
              )} />
            </div>
          )}
          secondary={(
            <div className="model-list-identity-meta">
              {model.code !== model.name && <ManagementCode value={model.code} />}
              <span className="model-list-layer-name" title={model.warehouseLayer ? `${model.warehouseLayer.code} · ${model.warehouseLayer.name}` : '未分层'}>
                {model.warehouseLayer
                  ? model.code !== model.name ? model.warehouseLayer.code : `${model.warehouseLayer.code} · ${model.warehouseLayer.name}`
                  : '未分层'}
              </span>
              <span className="model-list-version" title={`模型结构版本：Schema v${model.schemaVersion}`}>v{model.schemaVersion}</span>
            </div>
          )}
        />
      ),
    },
    {
      title: '状态 / 类型',
      width: 140,
      render: (_value, model) => (
        <div className="model-list-state-cell">
          <ManagementStatusIndicator
            label={dataModelStatusLabels[model.status]}
            tone={statusColor[model.status]}
          />
          <span className="model-list-type-tags" role="group" aria-label={`${model.name}的管理模式与数据类型`}>
            <Tooltip title={model.physicalTableMode === 'MANAGED' ? '管理模式：平台托管物理表' : '管理模式：逻辑注册已有外部表'}>
              <span className={`model-list-type-tag is-${model.physicalTableMode === 'MANAGED' ? 'managed' : 'external'}`}>{model.physicalTableMode === 'MANAGED' ? '托管表' : '外部表'}</span>
            </Tooltip>
            {typeof model.spatial === 'boolean' && <Tooltip title={model.spatial ? '数据类型：空间表，模型包含空间字段' : '数据类型：属性表，模型不含空间字段'}>
              <span className={`model-list-type-tag is-${model.spatial ? 'spatial' : 'attribute'}`}>{model.spatial ? '空间表' : '属性表'}</span>
            </Tooltip>}
          </span>
        </div>
      ),
    },
    {
      title: '存储位置',
      render: (_value, model) => {
        const sourceType = storageTypes.get(model.storageDataSourceId);
        return (
          <ManagementListCell
            className="model-list-storage-cell"
            icon={sourceType ? <DataSourceTypeIcon type={sourceType} /> : <DatabaseOutlined />}
            iconLabel={sourceType ? `数据库类型：${sourceType}` : '数据存储'}
            primary={<span className="model-list-storage" title={model.storageDataSourceName}>{model.storageDataSourceName}</span>}
            secondary={(
              <div className="model-list-physical-table">
                <TableOutlined aria-hidden />
                <ManagementCode value={model.physicalTableName} title={[model.catalogName, model.schemaName, model.physicalTableName].filter(Boolean).join(' / ')} />
              </div>
            )}
          />
        );
      },
    },
    {
      title: '数据统计',
      width: 112,
      align: 'right',
      render: (_value, model) => (
        <DataModelPhysicalStatisticsCell statistics={model.physicalStatistics} compact />
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 140,
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
            classNames={{ root: 'workspace-directory-menu model-actions-menu' }}
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
      tableLayout="fixed"
      className="management-table"
      rowKey="id"
      columns={columns}
      dataSource={list.modelsQuery.data?.content ?? []}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={list.modelsQuery.isError ? '暂时无法显示模型' : '暂无符合条件的模型'} /> }}
      loading={list.modelsQuery.isFetching && actions.refreshingModelIds.size === 0}
      rowSelection={{
        preserveSelectedRowKeys: true,
        columnWidth: 40,
        selectedRowKeys: list.selectedModelIds,
        onChange: list.updateSelection,
      }}
      scroll={{ x: 1040, y: '100%' }}
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
