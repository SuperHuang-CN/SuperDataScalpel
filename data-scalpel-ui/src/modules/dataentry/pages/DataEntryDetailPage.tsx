import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { ArrowLeftOutlined, DeleteOutlined, PauseCircleOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons';
import { Button, Modal, Result, Skeleton, Space, Tabs, Tag, Tooltip, message } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCurrentUser } from '../../system';
import { DataEntryDataPanel } from '../components/DataEntryDataPanel';
import { DataEntryFieldConfigPanel } from '../components/DataEntryFieldConfigPanel';
import { DataEntryOperationLogPanel } from '../components/DataEntryOperationLogPanel';
import { DataEntrySubmitPanel } from '../components/DataEntrySubmitPanel';
import { useDataEntryCommand, useDataEntryForm, useDeleteDataEntryForm } from '../hooks/useDataEntry';
import { dataEntryStatusLabels } from '../model/dataEntry';
import '../dataEntry.css';

export const DataEntryDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [messageApi, contextHolder] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const [dataRevision, setDataRevision] = useState(0);
  const query = useDataEntryForm(id);
  const commandMutation = useDataEntryCommand();
  const deleteMutation = useDeleteDataEntryForm();
  const currentUser = useCurrentUser();
  const canManage = new Set(currentUser.data?.permissions ?? []).has('dataentry.manage');
  const detail = query.data;

  if (query.isPending) return <Skeleton active />;
  if (query.error || !detail || !id) return <Result status="warning" title="无法读取填报表单" extra={<Button onClick={() => navigate('/data-entry')}>返回列表</Button>} />;

  const execute = async (command: 'publish' | 'disable') => {
    try {
      await commandMutation.mutateAsync({ id, command });
      messageApi.success(command === 'publish' ? '填报表单已发布' : '填报表单已停用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '表单状态操作失败');
    }
  };

  const remove = () => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay', title: `删除填报表单“${detail.form.modelName ?? detail.form.id}”？`,
    content: '关联下拉配置和操作日志将一并删除；目标物理表数据不会删除。',
    okText: '删除', okButtonProps: { danger: true }, cancelText: '取消',
    onOk: async () => {
      await deleteMutation.mutateAsync(id);
      navigate('/data-entry', { replace: true });
    },
  });

  return (
    <div className="business-detail-page data-entry-detail-page">
      {contextHolder}{modalContext}
      <div className="data-entry-detail-header business-detail-header">
        <div className="data-entry-detail-identity">
          <div className="data-entry-detail-title-row">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/data-entry')}>返回列表</Button>
            <span className="business-detail-resource-icon business-detail-resource-icon-purple">填</span>
            <span className="data-entry-detail-title">{detail.form.modelName ?? '目标模型已删除'}</span>
            <code>{detail.form.modelCode ?? detail.form.modelId}</code>
            <Tag color={detail.form.status === 'PUBLISHED' ? 'success' : detail.form.status === 'DISABLED' ? 'warning' : 'default'}>{dataEntryStatusLabels[detail.form.status]}</Tag>
          </div>
          <div className="data-entry-detail-subtitle">
            <span>模型版本 {detail.form.modelSchemaVersion ?? '—'}</span>
            <span>·</span>
            <span>发布版本 {detail.form.publishedModelSchemaVersion ?? '—'}</span>
          </div>
        </div>
        <Space>
          <Tooltip title="重新检查"><Button type="text" icon={<ReloadOutlined />} loading={query.isFetching} onClick={() => void query.refetch()} /></Tooltip>
          {canManage && detail.form.status !== 'PUBLISHED' && <Button type="primary" icon={<SendOutlined />} disabled={!detail.health.canPublish} loading={commandMutation.isPending} onClick={() => void execute('publish')}>发布</Button>}
          {canManage && detail.form.status === 'PUBLISHED' && <Button icon={<PauseCircleOutlined />} loading={commandMutation.isPending} onClick={() => void execute('disable')}>停用</Button>}
          {canManage && detail.form.status !== 'PUBLISHED' && <Button danger icon={<DeleteOutlined />} loading={deleteMutation.isPending} onClick={remove}>删除表单</Button>}
        </Space>
      </div>
      {detail.health.issues.length > 0 && <Alert className="data-entry-health-alert" type="warning" showIcon title="填报运行状态需要处理" description={detail.health.issues.map((issue) => <div key={`${issue.code}-${issue.fieldId ?? ''}-${issue.sourceModelId ?? ''}`}><code>{issue.code}</code> · {issue.message}</div>)} />}
      <Tabs className="business-detail-tabs" items={[
        { key: 'submit', label: '数据填报', children: <DataEntrySubmitPanel detail={detail} onSubmitted={() => setDataRevision((value) => value + 1)} onImported={(result) => {
          setDataRevision((value) => value + 1);
          if (result.status === 'PARTIALLY_SUCCEEDED') {
            messageApi.warning(result.warningMessage ?? `已确认导入 ${result.affectedCount} 条，结果需要人工核对`);
          } else {
            messageApi.success(`已导入 ${result.affectedCount} 条数据`);
          }
        }} /> },
        { key: 'data', label: '数据列表', children: <DataEntryDataPanel key={dataRevision} detail={detail} onMutated={() => setDataRevision((value) => value + 1)} /> },
        { key: 'fields', label: '字段配置', children: <DataEntryFieldConfigPanel key={detail.form.updatedAt} detail={detail} /> },
        { key: 'logs', label: '操作日志', children: <DataEntryOperationLogPanel formId={id} /> },
      ]} />
    </div>
  );
};
