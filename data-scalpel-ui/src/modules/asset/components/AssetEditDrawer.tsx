import { Alert, Button, Descriptions, Divider, Drawer, Form, Input, Select, Space, Switch, TreeSelect, Typography, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
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
        rootClassName="business-overlay business-drawer-overlay asset-edit-drawer"
        title={readOnly ? '资产详情' : '编辑资产'}
        open={open}
        width={720}
        onClose={onClose}
        destroyOnHidden
        footer={readOnly ? <Button onClick={onClose}>关闭</Button> : <Space><Button onClick={onClose}>取消</Button><Button type="primary" loading={updateMutation.isPending} onClick={() => form.submit()}>保存</Button></Space>}
      >
        {asset && (
          <>
            {asset.syncStatus !== 'IN_SYNC' && <Alert showIcon type="warning" message={assetSyncStatusLabels[asset.syncStatus]} description={asset.syncError ?? '来源元数据可能已变化，请检查或重新同步。'} />}
            <Typography.Title level={5}>门户信息</Typography.Title>
            <Form<AssetFormValues> autoComplete="off" form={form} layout="vertical" disabled={readOnly} onFinish={(values) => void submit(values)}>
              <div className="asset-edit-form-grid">
                <Form.Item label="业务领域" name="directoryId" rules={[{ required: true, message: '请选择业务领域' }]}>
                  <TreeSelect treeDefaultExpandAll treeData={directoryTreeSelectData(directories)} placeholder="选择业务领域" />
                </Form.Item>
                <Form.Item label="资产负责人" name="ownerName" rules={[{ max: 100 }]}><Input /></Form.Item>
                <Form.Item label="门户名称" name="portalName" extra={`留空使用来源名称：${asset.sourceName}`} rules={[{ max: 100 }]}><Input /></Form.Item>
                <Form.Item label="更新频率" name="updateFrequency" rules={[{ max: 100 }]}><Input placeholder="例如：每日更新" /></Form.Item>
                <Form.Item label="敏感级别" name="sensitivityLevel">
                  <Select options={[{ value: 'PUBLIC', label: '公开' }, { value: 'INTERNAL', label: '内部' }, { value: 'SENSITIVE', label: '敏感' }]} />
                </Form.Item>
                <Form.Item label="门户推荐" name="featured" valuePropName="checked"><Switch /></Form.Item>
              </div>
              <Form.Item label="门户简介" name="portalSummary" extra="留空使用来源说明" rules={[{ max: 1000 }]}><Input.TextArea rows={4} /></Form.Item>
              <Form.Item label="标签" name="tags"><Select mode="tags" maxCount={10} tokenSeparators={[',', '，']} placeholder="最多 10 个标签" /></Form.Item>
            </Form>

            <Divider />
            <Typography.Title level={5}>来源快照</Typography.Title>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="资产类型">{assetTypeLabels[asset.assetType]}</Descriptions.Item>
              <Descriptions.Item label="来源状态">{asset.sourceStatus}</Descriptions.Item>
              <Descriptions.Item label="同步状态">{assetSyncStatusLabels[asset.syncStatus]}</Descriptions.Item>
              <Descriptions.Item label="最后检查">{formatDateTime(asset.lastCheckedAt)}</Descriptions.Item>
              <Descriptions.Item label="来源更新时间">{formatDateTime(asset.sourceUpdatedAt)}</Descriptions.Item>
              <Descriptions.Item label="最后同步">{formatDateTime(asset.lastSyncedAt)}</Descriptions.Item>
              <Descriptions.Item label="来源名称">{asset.sourceName}</Descriptions.Item>
              <Descriptions.Item label="来源编码">{asset.sourceCode ?? '—'}</Descriptions.Item>
              {Object.entries((asset.sourceSnapshot.metadata as Record<string, unknown> | undefined) ?? {}).map(([key, value]) => (
                <Descriptions.Item key={key} label={snapshotLabels[key] ?? key}>{snapshotText(value)}</Descriptions.Item>
              ))}
              <Descriptions.Item label="来源说明" span={2}>{asset.sourceDescription ?? '—'}</Descriptions.Item>
            </Descriptions>
          </>
        )}
      </Drawer>
    </>
  );
};
