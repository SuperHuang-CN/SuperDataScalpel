import { CameraOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ModalFuncProps } from 'antd';
import type { Panorama } from '../model/panorama';

export const panoramaDeleteConfirmation = (panorama: Panorama): ModalFuncProps => ({
  rootClassName: 'business-overlay business-modal-overlay workspace-resource-overlay resource-delete-modal',
  width: 440,
  centered: true,
  icon: null,
  focusable: { autoFocusButton: 'cancel' },
  title: <div className="resource-dialog-title"><DeleteOutlined /><span>删除全景影像</span></div>,
  content: <div className="resource-delete-content">
    <p>确定删除以下全景影像？</p>
    <div className="resource-delete-target"><CameraOutlined /><strong>{panorama.name}</strong></div>
    <p className="resource-delete-note">原图和预览文件将一并删除，删除后无法恢复。</p>
  </div>,
  okText: '删除影像',
  cancelText: '取消',
  okButtonProps: { danger: true },
});
