import { Button, Modal, Space, Table, Typography, message } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { DataModelPublishConfirmationContent } from '../components/DataModelPublishConfirmationContent';
import { DataModelReferenceModalContent } from '../components/DataModelReferenceModalContent';
import {
  useBatchPublishDataModels,
  useDataModelCommand,
  useDataModelReferences,
  useDeleteDataModel,
  useExportModelMetadata,
  useRefreshDataModelPhysicalStatistics,
} from './useDataModels';
import type {
  DataModel,
  DataModelPhysicalStatistics,
} from '../model/dataModel';
import type { DataModelListState } from './useDataModelListState';

export const MAX_STATISTICS_REFRESH_COUNT = 20;
export const STATISTICS_REFRESH_CONCURRENCY = 3;
export const MAX_BATCH_PUBLISH_COUNT = 50;

export const useDataModelListActions = (list: DataModelListState) => {
  const [refreshingModelIds, setRefreshingModelIds] = useState<Set<string>>(() => new Set());
  const [batchRefreshingStatistics, setBatchRefreshingStatistics] = useState(false);
  const [publishingModelIds, setPublishingModelIds] = useState<Set<string>>(() => new Set());
  const [referenceModel, setReferenceModel] = useState<DataModel | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const deleteMutation = useDeleteDataModel();
  const exportMutation = useExportModelMetadata();
  const refreshStatisticsMutation = useRefreshDataModelPhysicalStatistics();
  const publishMutation = useDataModelCommand('publish');
  const disableMutation = useDataModelCommand('disable');
  const batchPublishMutation = useBatchPublishDataModels();
  const referencesQuery = useDataModelReferences(referenceModel?.id, Boolean(referenceModel));

  const executeCommand = async (model: DataModel, command: 'publish' | 'disable') => {
    try {
      const mutation = command === 'publish' ? publishMutation : disableMutation;
      await mutation.mutateAsync(model.id);
      messageApi.success(command === 'publish' ? '模型已发布，物理表已就绪' : '模型已停用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '模型状态操作失败');
    }
  };

  const transition = (model: DataModel) => {
    if (publishingModelIds.has(model.id)) return;
    if (model.status !== 'PUBLISHED') {
      modalApi.confirm({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '发布模型',
        content: <DataModelPublishConfirmationContent model={model} />,
        okText: '发布',
        cancelText: '取消',
        onOk: () => executeCommand(model, 'publish'),
      });
      return;
    }
    void executeCommand(model, 'disable');
  };

  const confirmRemove = async (model: DataModel) => {
    try {
      await deleteMutation.mutateAsync(model.id);
      messageApi.success('模型已删除');
      setReferenceModel(null);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除模型失败');
      throw error;
    }
  };

  const exportModels = async (modelIds: string[]) => {
    try {
      const blob = await exportMutation.mutateAsync(modelIds);
      downloadBlob(blob, `DataScalpel-模型元数据-${new Date().toISOString().slice(0, 10)}.xlsx`);
      messageApi.success('模型元数据导出已开始');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '导出模型元数据失败');
    }
  };

  const markModelsPublishing = (modelIds: string[], publishing: boolean) => {
    setPublishingModelIds((current) => {
      const next = new Set(current);
      modelIds.forEach((modelId) => {
        if (publishing) next.add(modelId);
        else next.delete(modelId);
      });
      return next;
    });
  };

  const batchPublish = async (models: DataModel[], skippedModels: DataModel[]) => {
    const modelIds = models.map((model) => model.id);
    markModelsPublishing(modelIds, true);
    try {
      const results = await batchPublishMutation.mutateAsync(
        models.map((model) => ({ id: model.id, name: model.name })),
      );
      const failures = results.filter((result) => !result.detail);
      const successCount = results.length - failures.length;
      const failedIds = new Set(failures.map((failure) => failure.id));
      list.replaceSelection(models.filter((model) => failedIds.has(model.id)));

      if (failures.length === 0) {
        const skippedText = skippedModels.length > 0
          ? `，跳过已发布模型 ${skippedModels.length} 个`
          : '';
        messageApi.success(`批量发布完成：成功 ${successCount} 个${skippedText}`);
        return;
      }

      const allFailed = successCount === 0;
      const failureRows = failures.map((failure) => ({
        id: failure.id,
        name: failure.name,
        reason: failure.error instanceof ApiError
          ? failure.error.message
          : failure.error instanceof Error ? failure.error.message : '发布失败',
      }));
      const resultModal = {
        rootClassName: 'business-overlay business-modal-overlay',
        title: allFailed ? '批量发布失败' : '批量发布部分成功',
        width: 760,
        okText: '关闭',
        content: (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Typography.Text>
              成功 {successCount} 个，失败 {failures.length} 个，跳过已发布模型 {skippedModels.length} 个。
              已成功发布的模型不会回滚，失败项已保留选中。
            </Typography.Text>
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              scroll={{ y: 320 }}
              dataSource={failureRows}
              columns={[
                { title: '模型', dataIndex: 'name', width: 220 },
                { title: '失败原因', dataIndex: 'reason', ellipsis: true },
              ]}
            />
          </Space>
        ),
      };
      if (allFailed) modalApi.error(resultModal);
      else modalApi.warning(resultModal);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '批量发布请求失败');
    } finally {
      markModelsPublishing(modelIds, false);
    }
  };

  const confirmBatchPublish = () => {
    if (
      list.selectedModelIds.length === 0
      || list.selectedModelIds.length > MAX_BATCH_PUBLISH_COUNT
    ) return;
    if (list.publishableSelectedModels.length === 0) {
      messageApi.info('所选模型均已发布，无需重复发布');
      return;
    }
    const models = [...list.publishableSelectedModels];
    const skippedModels = [...list.publishedSelectedModels];
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '批量发布模型',
      width: 620,
      okText: `发布 ${models.length} 个模型`,
      cancelText: '取消',
      content: (
        <Alert
          type="warning"
          showIcon
          message={`将发布 ${models.length} 个模型${skippedModels.length ? `，跳过已发布模型 ${skippedModels.length} 个` : ''}`}
          description="发布会实时检查物理表；缺失的受管物理表将自动创建，外部表只校验、不创建。各模型独立执行，允许部分成功。"
        />
      ),
      onOk: () => batchPublish(models, skippedModels),
    });
  };

  const markStatisticsRefreshing = (modelIds: string[], refreshing: boolean) => {
    setRefreshingModelIds((current) => {
      const next = new Set(current);
      modelIds.forEach((modelId) => {
        if (refreshing) next.add(modelId);
        else next.delete(modelId);
      });
      return next;
    });
  };

  const showStatisticsRefreshResult = (statistics: DataModelPhysicalStatistics) => {
    const detail = statistics.message ? `：${statistics.message}` : '';
    switch (statistics.lastRefreshStatus) {
      case 'SUCCESS':
        messageApi.success('数据统计刷新成功');
        break;
      case 'PARTIAL':
        messageApi.warning(`仅获取到部分数据统计${detail}`);
        break;
      case 'NOT_FOUND':
        messageApi.warning(`物理表未创建或不存在${detail}`);
        break;
      case 'UNSUPPORTED':
        messageApi.warning(`当前数据库或物理对象无法提供统计${detail}`);
        break;
      case 'FAILED':
        messageApi.error(`数据统计刷新失败${detail}`);
        break;
    }
  };

  const refreshModelStatistics = async (model: DataModel) => {
    if (refreshingModelIds.has(model.id)) return;
    markStatisticsRefreshing([model.id], true);
    try {
      const statistics = await refreshStatisticsMutation.mutateAsync(model.id);
      await list.modelsQuery.refetch();
      showStatisticsRefreshResult(statistics);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '数据统计刷新失败');
    } finally {
      markStatisticsRefreshing([model.id], false);
    }
  };

  const refreshSelectedStatistics = async () => {
    if (list.selectedModelIds.length === 0) return;
    if (list.selectedModelIds.length > MAX_STATISTICS_REFRESH_COUNT) {
      messageApi.warning(`一次最多刷新 ${MAX_STATISTICS_REFRESH_COUNT} 个模型的数据统计`);
      return;
    }

    const modelIds = [...list.selectedModelIds];
    const results: Array<DataModelPhysicalStatistics | Error | undefined> = new Array(modelIds.length);
    let nextIndex = 0;
    setBatchRefreshingStatistics(true);
    markStatisticsRefreshing(modelIds, true);
    try {
      const workers = Array.from(
        { length: Math.min(STATISTICS_REFRESH_CONCURRENCY, modelIds.length) },
        async () => {
          while (nextIndex < modelIds.length) {
            const index = nextIndex;
            nextIndex += 1;
            try {
              results[index] = await refreshStatisticsMutation.mutateAsync(modelIds[index]);
            } catch (error) {
              results[index] = error instanceof Error ? error : new Error('数据统计刷新失败');
            }
          }
        },
      );
      await Promise.all(workers);
      await list.modelsQuery.refetch();

      const successCount = results.filter((result) => (
        result && !(result instanceof Error) && result.lastRefreshStatus === 'SUCCESS'
      )).length;
      const partialCount = results.filter((result) => (
        result && !(result instanceof Error) && result.lastRefreshStatus === 'PARTIAL'
      )).length;
      const unavailableCount = modelIds.length - successCount - partialCount;
      if (successCount === modelIds.length) {
        messageApi.success(`已刷新 ${successCount} 个模型的数据统计`);
      } else if (successCount + partialCount > 0) {
        messageApi.warning(
          `统计刷新完成：成功 ${successCount} 个，部分获取 ${partialCount} 个，未获取 ${unavailableCount} 个`,
        );
      } else {
        messageApi.error(`所选 ${modelIds.length} 个模型均未能获取数据统计`);
      }
    } finally {
      markStatisticsRefreshing(modelIds, false);
      setBatchRefreshingStatistics(false);
    }
  };

  const lifecycleLoading = (model: DataModel) => (
    publishingModelIds.has(model.id)
    || (model.status === 'DRAFT' && publishMutation.isPending && publishMutation.variables === model.id)
    || (model.status === 'PUBLISHED' && disableMutation.isPending && disableMutation.variables === model.id)
    || (model.status === 'DISABLED' && publishMutation.isPending && publishMutation.variables === model.id)
  );

  const referenceModal = (
    <Modal
      rootClassName="business-overlay business-modal-overlay"
      open={Boolean(referenceModel)}
      title={referenceModel ? `删除模型：${referenceModel.name}` : '删除模型'}
      width={760}
      okText="确认删除"
      okButtonProps={{
        danger: true,
        disabled: referencesQuery.isPending
          || referencesQuery.isError
          || !referencesQuery.data?.deletable,
        loading: deleteMutation.isPending,
      }}
      cancelText="取消"
      onCancel={() => setReferenceModel(null)}
      onOk={() => referenceModel && confirmRemove(referenceModel)}
    >
      {referencesQuery.data?.deletable && referenceModel && (
        <Alert
          type="warning"
          showIcon
          message={`确认删除“${referenceModel.name}”吗？`}
          description="只删除模型元数据，不操作物理表。"
        />
      )}
      {referencesQuery.isPending && <Typography.Text>正在检查模型引用…</Typography.Text>}
      {referencesQuery.isError && (
        <Alert
          type="error"
          showIcon
          message="模型引用检查失败"
          description={referencesQuery.error instanceof ApiError
            ? referencesQuery.error.message
            : '请稍后重试。'}
          action={<Button size="small" onClick={() => void referencesQuery.refetch()}>重试</Button>}
        />
      )}
      {referencesQuery.data && (
        <DataModelReferenceModalContent references={referencesQuery.data} />
      )}
    </Modal>
  );

  return {
    messageContext,
    modalContext,
    referenceModal,
    refreshingModelIds,
    batchRefreshingStatistics,
    publishingModelIds,
    batchPublishPending: batchPublishMutation.isPending,
    exportPending: exportMutation.isPending,
    transition,
    remove: setReferenceModel,
    exportModels,
    confirmBatchPublish,
    refreshModelStatistics,
    refreshSelectedStatistics,
    lifecycleLoading,
  };
};

export type DataModelListActions = ReturnType<typeof useDataModelListActions>;
