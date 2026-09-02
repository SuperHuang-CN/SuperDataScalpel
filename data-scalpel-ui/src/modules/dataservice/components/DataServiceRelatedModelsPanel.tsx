import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { Button, Empty, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
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
  const navigate = useNavigate();
  const location = useLocation();

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
      render: (_value, row) => <code>{row.code ?? row.modelId}</code>,
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
    {
      title: '操作',
      key: 'actions',
      width: 86,
      fixed: 'right',
      render: (_value, row) => (
        <Button
          type="link"
          size="small"
          disabled={!canViewModels || !row.resolved}
          onClick={() => navigate(`/model/${row.modelId}`, {
            state: {
              returnTo: `${location.pathname}${location.search}`,
              returnLabel: '返回数据服务',
              returnState: location.state,
            },
          })}
        >
          查看模型
        </Button>
      ),
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
        scroll={{ x: 1100, y: '100%' }}
      />
    </div>
  );
};
