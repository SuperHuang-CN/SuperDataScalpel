import { DeleteOutlined } from '@ant-design/icons';
import type { ModalFuncProps } from 'antd';
import { dataSourceTypeLabels, type DataSource } from '../model/dataSource';
import { DataSourceTypeIcon } from './DataSourceTypeIcon';
import '../../../shared/theme/workspace-resource.css';

/** The list and detail page share the same object identity and deletion guidance. */
export const dataSourceDeleteConfirmation = (
  dataSource: DataSource,
  onConfirm: () => Promise<void>,
): ModalFuncProps => ({
  rootClassName: 'business-overlay business-modal-overlay workspace-resource-overlay resource-delete-modal data-source-delete-modal',
  width: 440,
  centered: true,
  icon: null,
  focusable: { autoFocusButton: 'cancel' },
  title: <div className="resource-dialog-title"><DeleteOutlined aria-hidden="true" /><span>删除数据源</span></div>,
  content: (
    <div className="resource-delete-content">
      <p>确定删除以下数据源？</p>
      <div className="resource-delete-target data-source-delete-target">
        <DataSourceTypeIcon type={dataSource.type} />
        <div className="data-source-delete-identity">
          <strong>{dataSource.name}</strong>
          <span>{dataSourceTypeLabels[dataSource.type]} · {dataSource.code}</span>
        </div>
      </div>
      <p className="resource-delete-note">删除后无法直接恢复。存在关联资源或业务引用时，系统会阻止删除。</p>
    </div>
  ),
  okText: '删除数据源',
  cancelText: '取消',
  okButtonProps: { danger: true },
  onOk: onConfirm,
});
