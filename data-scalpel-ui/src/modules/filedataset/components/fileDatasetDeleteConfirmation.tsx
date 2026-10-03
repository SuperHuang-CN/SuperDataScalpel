import { DeleteOutlined } from '@ant-design/icons';
import type { ModalFuncProps } from 'antd';
import type { FileDataset } from '../model/fileDataset';
import { FileDatasetTypeIcon } from './FileDatasetTypeIcon';

export const fileDatasetDeleteConfirmation = (dataset: FileDataset): ModalFuncProps => ({
  rootClassName: 'business-overlay business-modal-overlay workspace-resource-overlay resource-delete-modal',
  width: 440,
  centered: true,
  icon: null,
  focusable: { autoFocusButton: 'cancel' },
  title: <div className="resource-dialog-title"><DeleteOutlined aria-hidden="true" /><span>删除文件数据集</span></div>,
  content: <div className="resource-delete-content">
    <p>确定删除以下数据集及其文件和表？</p>
    <div className="resource-delete-target"><FileDatasetTypeIcon type={dataset.type} /><strong>{dataset.name}</strong></div>
    <p className="resource-delete-note">包含 {dataset.fileCount} 个文件、{dataset.tableCount} 张表。删除后无法恢复。</p>
  </div>,
  okText: '删除数据集',
  cancelText: '取消',
  okButtonProps: { danger: true },
});
