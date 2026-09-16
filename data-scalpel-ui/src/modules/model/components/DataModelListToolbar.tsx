import {
  DownOutlined,
  DownloadOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Space, Tooltip, type MenuProps } from 'antd';
import {
  MAX_BATCH_PUBLISH_COUNT,
  MAX_STATISTICS_REFRESH_COUNT,
  type DataModelListActions,
} from '../hooks/useDataModelListActions';
import type { DataModelListState } from '../hooks/useDataModelListState';

export const DataModelListToolbar = ({
  list,
  actions,
  canPublish,
  canCreate,
  createMenuItems,
}: {
  list: DataModelListState;
  actions: DataModelListActions;
  canPublish: boolean;
  canCreate: boolean;
  createMenuItems: MenuProps['items'];
}) => (
  <div className="management-result-toolbar">
    <div className="management-result-title">
      模型列表
      {' '}
      <span className="management-result-count">
        共 {list.modelsQuery.data?.totalElements ?? 0} 项
      </span>
    </div>
    <Space size={4} className="management-result-actions">
      <Tooltip title="刷新列表">
        <Button
          type="text"
          icon={<ReloadOutlined />}
          aria-label="刷新模型列表"
          onClick={() => void list.modelsQuery.refetch()}
        />
      </Tooltip>
      {canPublish && (
        <Tooltip title={
          list.selectedModelIds.length === 0
            ? '请先选择模型'
            : list.selectedModelIds.length > MAX_BATCH_PUBLISH_COUNT
              ? `一次最多批量发布 ${MAX_BATCH_PUBLISH_COUNT} 个模型`
              : list.publishableSelectedModels.length === 0
                ? '所选模型均已发布'
                : actions.batchPublishPending ? '正在批量发布模型' : '发布草稿和已停用模型'
        }>
          <span>
            <Button
              icon={<SendOutlined />}
              loading={actions.batchPublishPending}
              disabled={
                list.selectedModelIds.length === 0
                || list.selectedModelIds.length > MAX_BATCH_PUBLISH_COUNT
                || list.publishableSelectedModels.length === 0
                || actions.batchPublishPending
              }
              onClick={actions.confirmBatchPublish}
            >
              批量发布{list.selectedModelIds.length ? `（${list.selectedModelIds.length}）` : ''}
            </Button>
          </span>
        </Tooltip>
      )}
      <Tooltip title={
        list.selectedModelIds.length === 0
          ? '请先选择模型'
          : list.selectedModelIds.length > MAX_STATISTICS_REFRESH_COUNT
            ? `一次最多刷新 ${MAX_STATISTICS_REFRESH_COUNT} 个模型`
            : actions.refreshingModelIds.size > 0
              ? '数据统计正在刷新'
              : '从目标数据库快速刷新统计快照'
      }>
        <span>
          <Button
            icon={<ReloadOutlined />}
            loading={actions.batchRefreshingStatistics}
            disabled={
              list.selectedModelIds.length === 0
              || list.selectedModelIds.length > MAX_STATISTICS_REFRESH_COUNT
              || actions.refreshingModelIds.size > 0
            }
            onClick={() => void actions.refreshSelectedStatistics()}
          >
            刷新统计{list.selectedModelIds.length ? `（${list.selectedModelIds.length}）` : ''}
          </Button>
        </span>
      </Tooltip>
      <Tooltip title={
        list.selectedIncludesExternal
          ? '只能导出受管模型，当前选择中包含外部模型'
          : list.selectedModelIds.length === 0 ? '请先选择受管模型' : '导出所选模型结构'
      }>
        <span>
          <Button
            icon={<DownloadOutlined />}
            loading={actions.exportPending}
            disabled={list.selectedModelIds.length === 0 || list.selectedIncludesExternal}
            onClick={() => void actions.exportModels(list.selectedModelIds)}
          >
            导出结构{list.selectedModelIds.length ? `（${list.selectedModelIds.length}）` : ''}
          </Button>
        </span>
      </Tooltip>
      {canCreate && (
        <Dropdown menu={{ items: createMenuItems }} trigger={['click']} placement="bottomRight">
          <Button type="primary" icon={<PlusOutlined />}>
            新建模型 <DownOutlined />
          </Button>
        </Dropdown>
      )}
    </Space>
  </div>
);
