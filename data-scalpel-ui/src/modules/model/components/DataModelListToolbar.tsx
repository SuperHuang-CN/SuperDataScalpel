import {
  DownloadOutlined,
  TableOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { Button, Space, Tooltip } from 'antd';
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
}: {
  list: DataModelListState;
  actions: DataModelListActions;
  canPublish: boolean;
}) => (
  <div className="management-result-toolbar">
    <div className="management-result-title">
      <TableOutlined aria-hidden />
      模型列表
      {' '}
      <span className="management-result-count">
        共 {list.modelsQuery.data?.totalElements ?? 0} 项
      </span>
    </div>
    <Space size={8} wrap className="management-result-actions">
      {list.selectedModelIds.length > 0 && (
        <span className="model-list-selection">
          已选 {list.selectedModelIds.length} 项
          <Button type="link" onClick={() => list.replaceSelection([])}>取消选择</Button>
        </span>
      )}
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
              批量发布
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
            刷新统计
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
            导出结构
          </Button>
        </span>
      </Tooltip>
    </Space>
  </div>
);
