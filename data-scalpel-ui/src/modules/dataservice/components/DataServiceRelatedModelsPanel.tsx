import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { Empty, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import {
  dataModelStatusLabels,
  type DataModelStatus,
} from '../../model';
import type { DataServiceRelatedModelView } from '../hooks/useDataServiceRelatedModels';
import type { DataServiceDetail } from '../model/dataService';

interface DataServiceRelatedModelsPanelProps {
  dataService: DataServiceDetail;
  relatedModels: DataServiceRelatedModelView[];
  canViewModels: boolean;
}

const statusColors: Record<DataModelStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DISABLED: 'warning',
};

const physicalTableName = (row: DataServiceRelatedModelView) => {
  if (!row.resolved) return '—';
  return row.physicalTableName ?? '—';
};

export const DataServiceRelatedModelsPanel = ({
  dataService,
  relatedModels,
  canViewModels,
}: DataServiceRelatedModelsPanelProps) => {
  if (dataService.type === 'SCRIPT_API') {
    return (
      <div className="data-service-detail-tab-panel data-service-related-models-empty">
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Groovy 脚本服务当前不声明模型级引用"
        >
          <Typography.Text type="secondary">
            血缘页会从脚本使用的数据源开始展示，后续可在脚本契约支持显式模型引用后补充关联模型。
          </Typography.Text>
        </Empty>
      </div>
    );
  }

  if (relatedModels.length === 0) {
    return (
      <div className="data-service-detail-tab-panel data-service-related-models-empty">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前服务没有关联模型" />
      </div>
    );
  }

  const columns: TableProps<DataServiceRelatedModelView>['columns'] = [
    {
      title: '关联角色',
      key: 'role',
      width: 110,
      render: (_value, row) => (
        <Tag color={row.role === 'PRIMARY' ? 'blue' : 'default'}>
          {row.role === 'PRIMARY' ? '主模型' : `引用模型 ${row.order}`}
        </Tag>
      ),
    },
    {
      title: '模型名称',
      key: 'name',
      width: 190,
      ellipsis: true,
      render: (_value, row) => row.error
        ? <Typography.Text type="danger">模型加载失败</Typography.Text>
        : row.name ?? '—',
    },
    {
      title: '模型编码',
      key: 'code',
      width: 170,
      ellipsis: true,
      render: (_value, row) => {
        const modelCode = row.code ?? row.modelId;
        const content = <code>{modelCode}</code>;
        return canViewModels && row.resolved
          ? (
            <Typography.Link
              href={`/model/${row.modelId}`}
              target="_blank"
              rel="opener"
              aria-label={`在新标签页查看模型 ${modelCode}`}
            >
              {content}
            </Typography.Link>
          )
          : content;
      },
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_value, row) => row.status
        ? <Tag color={statusColors[row.status]}>{dataModelStatusLabels[row.status]}</Tag>
        : '—',
    },
    {
      title: '存储数据源',
      key: 'storageDataSource',
      width: 190,
      ellipsis: true,
      render: (_value, row) => row.storageDataSourceName ?? '—',
    },
    {
      title: '物理表',
      key: 'physicalLocation',
      width: 280,
      ellipsis: true,
      render: (_value, row) => <code>{physicalTableName(row)}</code>,
    },
  ];

  return (
    <div className="data-service-detail-tab-panel">
      {!canViewModels && (
        <Alert
          type="warning"
          showIcon
          message="缺少模型查看权限"
          description="当前仅展示数据服务中保存的模型 ID。"
          className="data-service-related-models-alert"
        />
      )}
      <Table<DataServiceRelatedModelView>
        size="small"
        rowKey={(row) => `${row.role}-${row.order}-${row.modelId}`}
        columns={columns}
        dataSource={relatedModels}
        loading={relatedModels.some((row) => row.loading)}
        pagination={false}
        scroll={{ x: 1040, y: '100%' }}
      />
    </div>
  );
};
