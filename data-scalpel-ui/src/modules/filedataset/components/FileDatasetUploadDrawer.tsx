import { UploadOutlined } from '@ant-design/icons';
import { Button, ConfigProvider, Drawer } from 'antd';
import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import type { FileDataset } from '../model/fileDataset';
import { FileDatasetParseHistoryPanel } from './FileDatasetParseHistoryPanel';
import { FileDatasetTypeIcon } from './FileDatasetTypeIcon';
import { FileDatasetUploadControl } from './FileDatasetUploadControl';

export const FileDatasetUploadDrawer = ({ dataset, onClose, onOpenDetail }: {
  dataset: FileDataset | null;
  onClose: () => void;
  onOpenDetail: (tab: 'files' | 'history') => void;
}) => <ConfigProvider theme={workspaceResourceTheme}>
  <Drawer open={Boolean(dataset)} onClose={onClose} destroyOnHidden size="min(860px, 100vw)"
    rootClassName="business-overlay business-drawer-overlay workspace-resource-overlay file-dataset-upload-drawer"
    title={<span className="file-dataset-upload-drawer-title"><UploadOutlined />上传文件</span>}
    footer={<div className="file-dataset-upload-drawer-footer"><Button onClick={() => onOpenDetail('files')}>管理文件</Button><Button onClick={onClose}>关闭</Button></div>}>
    {dataset && <div key={dataset.id} className="file-dataset-upload-workspace">
      <div className="file-dataset-upload-target"><FileDatasetTypeIcon type={dataset.type} /><strong>{dataset.name}</strong></div>
      <FileDatasetUploadControl dataset={dataset} />
      <FileDatasetParseHistoryPanel datasetId={dataset.id} compact onViewAll={() => onOpenDetail('history')} />
    </div>}
  </Drawer>
</ConfigProvider>;
