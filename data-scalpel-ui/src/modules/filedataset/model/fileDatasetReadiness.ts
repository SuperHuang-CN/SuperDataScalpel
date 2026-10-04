import type { ManagementStatusTone } from '../../../shared/components/ManagementListCells';
import type { FileDataset } from './fileDataset';

/** Counts describe availability, not whether a background job is running. */
export const fileDatasetReadiness = (
  dataset: Pick<FileDataset, 'fileCount' | 'tableCount' | 'readyTableCount'>,
): { label: string; detail: string; tone: ManagementStatusTone; help: string } => {
  if (dataset.tableCount === 0) {
    return dataset.fileCount === 0
      ? { label: '尚未上传', detail: '进入数据集上传文件', tone: 'default', help: '先上传文件，再查看解析得到的表。' }
      : { label: '暂无数据表', detail: '已上传文件', tone: 'default', help: '已有文件，但尚无数据表。可进入数据集或解析队列查看处理情况。' };
  }
  const ready = dataset.readyTableCount === dataset.tableCount;
  return {
    label: ready ? '全部就绪' : dataset.readyTableCount > 0 ? '部分就绪' : '未就绪',
    detail: `${dataset.readyTableCount} / ${dataset.tableCount} 张表已就绪`,
    tone: ready ? 'success' : 'warning',
    help: ready ? '全部数据表已就绪。' : '未就绪不等同于正在解析。请进入数据集查看表状态，或在解析队列查看进度与失败原因。',
  };
};
