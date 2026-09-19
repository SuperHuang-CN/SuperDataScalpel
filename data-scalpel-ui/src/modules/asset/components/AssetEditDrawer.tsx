import { CompassOutlined, DatabaseOutlined, FormOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { Badge, Button, Drawer, Form, Input, Select, Space, Switch, Tag, TreeSelect, Typography, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { directoryTreeSelectData, type DirectoryTreeNode } from '../../directory';
import { useUpdateAsset } from '../hooks/useAssets';
import { assetSyncStatusLabels, assetTypeLabels, type Asset, type AssetSensitivityLevel, type UpdateAssetRequest } from '../model/asset';

interface AssetEditDrawerProps {
  open: boolean;
  asset: Asset | null;
  directories: DirectoryTreeNode[];
  readOnly?: boolean;
  onClose: () => void;
}

interface AssetFormValues {
  directoryId?: string;
  portalName?: string;
  portalSummary?: string;
  tags?: string[];
  ownerName?: string;
  updateFrequency?: string;
  sensitivityLevel?: AssetSensitivityLevel;
  featured: boolean;
}

const snapshotText = (value: unknown): string => {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
};

const snapshotLabels: Record<string, string> = {
  status: '模型状态',
  warehouseLayerCode: '数仓分层编码',
  warehouseLayerName: '数仓分层名称',
  schemaVersion: 'Schema 版本',
  fieldCount: '字段数量',
  datasetType: '数据集类型',
  fileCount: '文件数量',
  tableCount: '表数量',
  readyTableCount: '可用表数量',
  enabled: '启用状态',
  valueType: '取值类型',
  contentVersion: '内容版本',
  itemCount: '码值数量',
  serviceType: '服务类型',
  accessMode: '访问模式',
  routePath: '服务路由',
  definitionVersion: '定义版本',
  deploymentStatus: '部署状态',
};

const formatDateTime = (value: string | null): string => value
  ? new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value))
  : '—';

export const AssetEditDrawer = ({ open, asset, directories, readOnly = false, onClose }: AssetEditDrawerProps) => {
  const [form] = Form.useForm<AssetFormValues>();
  const [messageApi, contextHolder] = message.useMessage();
  const updateMutation = useUpdateAsset();

  useEffect(() => {
    if (!open || !asset) return;
    form.setFieldsValue({
      directoryId: asset.directoryId ?? undefined,
      portalName: asset.portalName ?? undefined,
      portalSummary: asset.portalSummary ?? undefined,
      tags: asset.tags,
      ownerName: asset.ownerName ?? undefined,
      updateFrequency: asset.updateFrequency ?? undefined,
      sensitivityLevel: asset.sensitivityLevel ?? undefined,
      featured: asset.featured,
    });
  }, [asset, form, open]);

  const submit = async (values: AssetFormValues) => {
    if (!asset) return;
    const request: UpdateAssetRequest = {
      ...values,
      tags: values.tags ?? [],
      featured: values.featured,
    };
    try {
      await updateMutation.mutateAsync({ id: asset.id, request });
      messageApi.success('资产门户信息已保存');
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存资产失败');
    }
  };

  return (
    <>
      {contextHolder}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer asset-edit-drawer"
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><CompassOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>{readOnly ? '资产门户详情' : '编辑资产门户信息'}</span>
              <Typography.Text type="secondary">{asset ? `${asset.sourceName}${asset.sourceCode ? ` · ${asset.sourceCode}` : ''}` : '维护门户呈现与治理属性'}</Typography.Text>
            </span>
          </div>
        )}
        extra={asset && <Tag className="data-model-drawer-header-tag">{assetTypeLabels[asset.assetType]}</Tag>}
        open={open}
        width={820}
        onClose={onClose}
        forceRender
        destroyOnHidden
        footer={(
          <div className="data-model-drawer-footer">
            <Badge status={asset?.syncStatus === 'IN_SYNC' ? 'success' : 'warning'} text={asset ? `${assetSyncStatusLabels[asset.syncStatus]} · ${asset.portalName || asset.sourceName}` : '资产信息'} />
            <Space>
              <Button onClick={onClose}>{readOnly ? '关闭' : '取消'}</Button>
              {!readOnly && <Button type="primary" loading={updateMutation.isPending} onClick={() => form.submit()}>保存修改</Button>}
            </Space>
          </div>
        )}
      >
        {asset && (
          <Form<AssetFormValues>
            name="asset-portal-editor-form"
            className="data-model-form asset-edit-form"
            autoComplete="off"
            form={form}
            layout="vertical"
            disabled={readOnly}
            onFinish={(values) => void submit(values)}
          >
            <section className="data-model-form-section">
              <header className="data-model-form-section-header">
                <span className="data-model-form-section-icon" aria-hidden="true"><FormOutlined /></span>
                <span className="data-model-form-section-copy">
                  <span className="data-model-form-section-title">门户呈现</span>
                  <Typography.Text type="secondary">设置资产在门户中的名称、简介、领域与检索标签</Typography.Text>
                </span>
              </header>
              <div className="data-model-form-section-body">
                <div className="asset-edit-form-grid">
                  <Form.Item label="业务领域" name="directoryId" rules={[{ required: true, message: '请选择业务领域' }]}>
                    <TreeSelect treeDefaultExpandAll treeData={directoryTreeSelectData(directories)} placeholder="选择业务领域" />
                  </Form.Item>
                  <Form.Item
                    label={<span className="data-model-form-label-with-help">门户名称<ContextHelp ariaLabel="门户名称说明" content={`留空时使用来源名称：${asset.sourceName}`} /></span>}
                    name="portalName"
                    rules={[{ max: 100 }]}
                  >
                    <Input name="asset-portal-name" autoComplete="off" placeholder={asset.sourceName} />
                  </Form.Item>
                </div>
                <Form.Item label="门户简介" name="portalSummary" extra="留空时使用来源说明" rules={[{ max: 1000 }]}><Input.TextArea name="asset-portal-summary" autoComplete="off" rows={4} placeholder="概括资产用途、内容范围和适用场景" /></Form.Item>
                <Form.Item label="检索标签" name="tags"><Select mode="tags" maxCount={10} tokenSeparators={[',', '，']} placeholder="输入标签后按 Enter，最多 10 个" /></Form.Item>
              </div>
            </section>

            <section className="data-model-form-section">
              <header className="data-model-form-section-header">
                <span className="data-model-form-section-icon" aria-hidden="true"><SafetyCertificateOutlined /></span>
                <span className="data-model-form-section-copy">
                  <span className="data-model-form-section-title">治理属性</span>
                  <Typography.Text type="secondary">补充负责人、更新节奏与信息敏感等级</Typography.Text>
                </span>
                <span className="data-source-enabled-control">
                  <span>门户推荐</span>
                  <Form.Item name="featured" valuePropName="checked" noStyle><Switch aria-label="设为门户推荐资产" /></Form.Item>
                </span>
              </header>
              <div className="data-model-form-section-body">
                <div className="asset-edit-form-grid">
                  <Form.Item label="资产负责人" name="ownerName" rules={[{ max: 100 }]}><Input name="asset-owner-name" autoComplete="off" placeholder="输入负责人或责任团队" /></Form.Item>
                  <Form.Item label="更新频率" name="updateFrequency" rules={[{ max: 100 }]}><Input name="asset-update-frequency" autoComplete="off" placeholder="例如：每日更新" /></Form.Item>
                  <Form.Item label="敏感级别" name="sensitivityLevel">
                    <Select allowClear placeholder="选择敏感级别" options={[{ value: 'PUBLIC', label: '公开' }, { value: 'INTERNAL', label: '内部' }, { value: 'SENSITIVE', label: '敏感' }]} />
                  </Form.Item>
                </div>
              </div>
            </section>

            <section className="data-model-form-section asset-source-section">
              <header className="data-model-form-section-header">
                <span className="data-model-form-section-icon" aria-hidden="true"><DatabaseOutlined /></span>
                <span className="data-model-form-section-copy">
                  <span className="data-model-form-section-title">来源快照</span>
                  <Typography.Text type="secondary">只读展示当前登记资源的来源状态与同步元数据</Typography.Text>
                </span>
                <Tag className="data-model-drawer-header-tag">{assetSyncStatusLabels[asset.syncStatus]}</Tag>
              </header>
              <div className="asset-source-section-body">
                {asset.syncStatus !== 'IN_SYNC' && <InlineFeedback className="asset-sync-feedback" tone="warning" label={assetSyncStatusLabels[asset.syncStatus]} detail={asset.syncError ?? '来源元数据可能已变化，请检查或重新同步。'} />}
                <div className="asset-source-snapshot-grid">
                  <div className="asset-source-snapshot-item"><span>资产类型</span><strong>{assetTypeLabels[asset.assetType]}</strong></div>
                  <div className="asset-source-snapshot-item"><span>来源状态</span><strong>{asset.sourceStatus}</strong></div>
                  <div className="asset-source-snapshot-item"><span>同步状态</span><strong>{assetSyncStatusLabels[asset.syncStatus]}</strong></div>
                  <div className="asset-source-snapshot-item"><span>最后检查</span><strong>{formatDateTime(asset.lastCheckedAt)}</strong></div>
                  <div className="asset-source-snapshot-item"><span>来源更新时间</span><strong>{formatDateTime(asset.sourceUpdatedAt)}</strong></div>
                  <div className="asset-source-snapshot-item"><span>最后同步</span><strong>{formatDateTime(asset.lastSyncedAt)}</strong></div>
                  <div className="asset-source-snapshot-item"><span>来源名称</span><strong>{asset.sourceName}</strong></div>
                  <div className="asset-source-snapshot-item"><span>来源编码</span><strong className="asset-source-code">{asset.sourceCode ?? '—'}</strong></div>
                  {Object.entries((asset.sourceSnapshot.metadata as Record<string, unknown> | undefined) ?? {}).map(([key, value]) => (
                    <div className="asset-source-snapshot-item" key={key}><span>{snapshotLabels[key] ?? key}</span><strong>{snapshotText(value)}</strong></div>
                  ))}
                  <div className="asset-source-snapshot-item asset-source-snapshot-item-wide"><span>来源说明</span><strong>{asset.sourceDescription ?? '—'}</strong></div>
                </div>
              </div>
            </section>
          </Form>
        )}
      </Drawer>
    </>
  );
};
