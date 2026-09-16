import {
  ApartmentOutlined,
  ArrowLeftOutlined,
  CheckCircleOutlined,
  EditOutlined,
  MoreOutlined,
  ReloadOutlined,
  SaveOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Dropdown, Modal, Spin, Tabs, Tag, Tooltip, message } from 'antd';
import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { ManagementCode } from '../../../shared/components/ManagementListCells';
import { useCurrentUser } from '../../system';
import {
  deleteBusinessObjectType,
  executeBusinessObjectTypeCommand,
  saveBusinessObjectTypeDefinition,
  validateBusinessObjectType,
} from '../api/businessObjectTypeApi';
import {
  CapabilitiesPanel,
  FieldName,
  PropertiesPanel,
  RelationsPanel,
  SourcesPanel,
} from '../components/BusinessObjectDefinitionPanels';
import { BusinessObjectPreviewPanel } from '../components/BusinessObjectPreviewPanel';
import { BusinessObjectTypeBasicsDrawer } from '../components/BusinessObjectTypeBasicsDrawer';
import {
  invalidateBusinessObjectTypes,
  useBusinessObjectRelations,
  useBusinessObjectType,
  useBusinessObjectTypes,
} from '../hooks/useBusinessObjectTypes';
import { emptyBusinessObjectDefinition, type BusinessObjectTypeDefinition } from '../model/businessObjectType';
import '../ontology.css';

const savedAt = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
}).format(new Date(value));

export const BusinessObjectTypeDetailPage = () => {
  const { id = '' } = useParams();
  const [params] = useSearchParams();
  const tab = params.get('tab') ?? '';
  const relationId = params.get('relationId') ?? '';
  return <BusinessObjectTypeEditor
    key={`${id}:${params.get('objectKey') ?? ''}`}
    id={id}
    initialKey={params.get('objectKey') ?? ''}
    initialTab={tab}
    initialRelationId={relationId}
    returnTo={params.get('returnTo') ?? ''}
  />;
};

const detailTabs = new Set(['basic', 'sources', 'properties', 'relations', 'capabilities', 'preview']);

const BusinessObjectTypeEditor = ({ id, initialKey, initialTab, initialRelationId, returnTo }: {
  id: string;
  initialKey: string;
  initialTab: string;
  initialRelationId: string;
  returnTo: string;
}) => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const query = useBusinessObjectType(id);
  const relationsQuery = useBusinessObjectRelations(id);
  const typesQuery = useBusinessObjectTypes({ page: 0, size: 100, sort: 'name' });
  const user = useCurrentUser();
  const permissions = new Set(user.data?.permissions ?? []);
  const canManage = permissions.has('ontology.manage');
  const canReadModels = permissions.has('model.view');
  const client = useQueryClient();
  const [modal, modalContext] = Modal.useModal();
  const [messageApi, messageContext] = message.useMessage();
  const [draft, setDraft] = useState<BusinessObjectTypeDefinition | null>(null);
  const [dirty, setDirty] = useState(false);
  const [editingBasics, setEditingBasics] = useState(false);
  const [activeTab, setActiveTab] = useState(detailTabs.has(initialTab) ? initialTab : initialKey ? 'preview' : 'basic');
  const definition = dirty && draft ? draft : query.data?.definition ?? emptyBusinessObjectDefinition();

  const changeDefinition = (next: BusinessObjectTypeDefinition) => {
    if (!canManage || save.isPending) return;
    setDraft(next);
    setDirty(true);
  };

  const save = useMutation({
    mutationFn: () => saveBusinessObjectTypeDefinition(id, definition),
    onSuccess: async (saved) => {
      client.setQueryData(['business-object-types', 'detail', id], saved);
      setDraft(saved.definition);
      setDirty(false);
      await invalidateBusinessObjectTypes(client);
      messageApi.success('当前配置已保存并生效');
    },
  });
  const validate = useMutation({
    mutationFn: () => validateBusinessObjectType(id),
    onSuccess: (result) => messageApi[result.canPreview ? 'success' : 'warning'](result.canPreview ? '当前配置可以预览' : `发现 ${result.issues.length} 个配置问题`),
  });
  const command = useMutation({
    mutationFn: async (action: 'enable' | 'disable' | 'delete') => {
      if (action === 'delete') await deleteBusinessObjectType(id);
      else await executeBusinessObjectTypeCommand(id, action);
    },
    onSuccess: async (_, action) => {
      await invalidateBusinessObjectTypes(client);
      if (action === 'delete') navigate('/business-object-types');
      else messageApi.success(action === 'enable' ? '对象类型已启用' : '对象类型已停用');
    },
  });

  if (query.isError) {
    return <div className="page-stack"><InlineFeedback tone="error" label={query.error.message} action={<Button onClick={() => void query.refetch()}>重试</Button>} /></div>;
  }
  if (!query.data) return <Spin />;

  const objectType = query.data;
  const currentRelations = relationsQuery.data ?? [];
  const targets = (typesQuery.data?.content ?? [])
    .filter((type) => type.enabled)
    .map((type) => ({ id: type.id, name: type.name, definition: type.definition }));

  const refresh = () => {
    const execute = () => {
      setDraft(null);
      setDirty(false);
      void query.refetch();
      void relationsQuery.refetch();
    };
    if (!dirty) {
      execute();
      return;
    }
    modal.confirm({
      title: '放弃未保存的修改？',
      content: '刷新会重新读取最近保存的当前配置。',
      okText: '放弃并刷新',
      cancelText: '继续编辑',
      onOk: execute,
    });
  };

  const returnTarget = returnTo.startsWith('/business-object-types') ? returnTo : '/business-object-types';
  const overviewTarget = () => {
    const [path, queryString = ''] = (returnTo.startsWith('/business-object-types') ? returnTo : '/business-object-types?view=graph').split('?');
    const next = new URLSearchParams(queryString);
    next.set('view', 'graph');
    next.set('focusId', id);
    return `${path}?${next.toString()}`;
  };
  const openOverview = () => {
    const target = overviewTarget();
    if (!dirty) {
      navigate(target);
      return;
    }
    const confirmation = modal.confirm({
      title: '当前配置尚未保存',
      content: <div className="ontology-unsaved-navigation"><span>前往本体总览前，请选择如何处理本次修改。</span><Button danger type="link" onClick={() => { confirmation.destroy(); setDraft(null); setDirty(false); navigate(target); }}>放弃修改前往</Button></div>,
      okText: '保存后前往',
      cancelText: '取消',
      onOk: async () => {
        await save.mutateAsync();
        navigate(target);
      },
    });
  };

  const runLifecycle = (action: 'enable' | 'disable' | 'delete') => {
    const deleting = action === 'delete';
    modal.confirm({
      title: deleting ? `删除对象类型“${objectType.name}”？` : `${action === 'enable' ? '启用' : '停用'}对象类型“${objectType.name}”？`,
      content: deleting ? '只删除当前定义和引用投影，不删除来源业务数据。' : action === 'disable' ? '停用后不能预览，也不能被新的关系引用。' : '启用后可以预览，并可被新的关系引用。',
      okText: deleting ? '删除' : action === 'enable' ? '启用' : '停用',
      okButtonProps: deleting ? { danger: true } : undefined,
      onOk: () => command.mutateAsync(action),
    });
  };

  const issues = validate.data?.issues ?? [];
  const failure = save.error ?? validate.error ?? command.error;

  return (
    <div className="model-detail-page business-detail-page ontology-detail-page">
      {modalContext}
      {messageContext}
      <div className="model-detail-header business-detail-header">
        <div className="model-detail-identity">
          <div className="model-detail-title-row">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate(returnTarget)}>{returnTo.includes('view=graph') ? '返回本体总览' : '返回列表'}</Button>
            <span className="business-detail-resource-icon business-detail-resource-icon-purple"><ApartmentOutlined /></span>
            <span className="model-detail-title">{objectType.name}</span>
            <code>{objectType.code}</code>
            <Tag color={objectType.enabled ? 'success' : 'default'}>{objectType.enabled ? '启用' : '停用'}</Tag>
            {dirty && <Tag color="warning">有未保存修改</Tag>}
          </div>
          <div className="model-detail-subtitle">
            <span>负责人：{objectType.ownerName ?? '未填写'}</span>
            <span>·</span>
            <span>最后保存：{savedAt(objectType.updatedAt)}</span>
          </div>
        </div>
        <div className="ontology-header-actions">
          <Tooltip title="重新读取当前配置"><Button icon={<ReloadOutlined />} aria-label="刷新对象类型" onClick={refresh} /></Tooltip>
          <Button icon={<CheckCircleOutlined />} loading={validate.isPending} disabled={!canReadModels || dirty} onClick={() => validate.mutate()}>校验</Button>
          {canManage && <Button type="primary" icon={<SaveOutlined />} loading={save.isPending} disabled={!dirty || !canReadModels} onClick={() => save.mutate()}>保存配置</Button>}
          {canManage && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'basics', icon: <EditOutlined />, label: '编辑基本资料' },
                  { key: 'lifecycle', icon: <SafetyCertificateOutlined />, label: objectType.enabled ? '停用对象类型' : '启用对象类型' },
                  { type: 'divider' },
                  { key: 'delete', danger: true, label: '删除对象类型' },
                ],
                onClick: ({ key }) => {
                  if (key === 'basics') setEditingBasics(true);
                  else if (key === 'lifecycle') runLifecycle(objectType.enabled ? 'disable' : 'enable');
                  else if (key === 'delete') runLifecycle('delete');
                },
              }}
            >
              <Button icon={<MoreOutlined />} aria-label="更多对象类型操作">更多</Button>
            </Dropdown>
          )}
        </div>
      </div>

      {failure && <InlineFeedback tone="error" label={failure.message ?? '操作失败'} />}
      {validate.data && (
        <InlineFeedback
          tone={validate.data.canPreview ? 'success' : 'warning'}
          label={validate.data.canPreview ? '当前保存的配置可以预览' : `当前保存的配置有 ${issues.length} 个问题`}
          detail={issues.length ? <div className="ontology-validation-details">{issues.map((issue, index) => <div key={`${issue.code}-${issue.path}-${index}`}><strong>{issue.message}</strong><ManagementCode value={`${issue.code} · ${issue.path}`} /></div>)}</div> : '主来源、身份、属性和关系引用均通过结构校验。'}
        />
      )}

      <Tabs
        activeKey={activeTab}
        onChange={(tab) => {
          setActiveTab(tab);
          const next = new URLSearchParams(searchParams);
          if (tab === 'basic') next.delete('tab');
          else next.set('tab', tab);
          if (tab !== 'relations') next.delete('relationId');
          setSearchParams(next, { replace: true });
        }}
        className="model-detail-tabs business-detail-tabs"
        destroyOnHidden
        items={[
          {
            key: 'basic',
            label: '基本信息',
            children: (
              <div className="ontology-tab-stack">
                <BusinessDetailSection title="业务身份" description="说明这个对象类型在业务中的含义和管理责任。" icon={<UserOutlined />}>
                  <BusinessDetailDescriptions
                    column={3}
                    items={[
                      { key: 'name', label: '对象类型', children: objectType.name },
                      { key: 'code', label: '稳定编码', children: <ManagementCode value={objectType.code} /> },
                      { key: 'owner', label: '负责人', children: objectType.ownerName || '未填写' },
                      { key: 'status', label: '状态', children: <Tag color={objectType.enabled ? 'success' : 'default'}>{objectType.enabled ? '启用' : '停用'}</Tag> },
                      { key: 'summary', label: '业务定义', span: 2, children: objectType.summary || '未填写' },
                    ]}
                  />
                </BusinessDetailSection>
                <BusinessDetailSection title="对象身份规则" description="主来源决定对象集合，唯一标识与显示名称来自主来源。" icon={<SafetyCertificateOutlined />}>
                  <BusinessDetailDescriptions
                    column={3}
                    items={[
                      { key: 'model', label: '主来源', children: objectType.mainSourceModelName || '尚未配置' },
                      { key: 'identity', label: '唯一标识', children: definition.mainSource ? <FieldName modelId={definition.mainSource.modelId} fieldId={definition.mainSource.identityFieldId} /> : '尚未配置' },
                      { key: 'title', label: '显示名称', children: definition.mainSource ? <FieldName modelId={definition.mainSource.modelId} fieldId={definition.mainSource.titleFieldId} /> : '尚未配置' },
                      { key: 'mode', label: '生效方式', span: 3, children: '保存后立即生效。当前只维护一份配置，不维护草稿、发布版本或历史快照。' },
                    ]}
                  />
                </BusinessDetailSection>
              </div>
            ),
          },
          { key: 'sources', label: '数据来源', children: <SourcesPanel definition={definition} canManage={canManage} onChange={changeDefinition} /> },
          { key: 'properties', label: '属性与分组', children: <PropertiesPanel definition={definition} canManage={canManage} onChange={changeDefinition} /> },
          { key: 'relations', label: '业务关系', children: <RelationsPanel definition={definition} relations={currentRelations} objectName={objectType.name} targets={targets} canManage={canManage} loading={relationsQuery.isLoading} initialRelationId={initialRelationId || undefined} onChange={changeDefinition} onNavigate={(targetId) => navigate(`/business-object-types/${targetId}`)} onOpenOverview={openOverview} /> },
          { key: 'capabilities', label: '业务能力', children: <CapabilitiesPanel definition={definition} canManage={canManage} onChange={changeDefinition} /> },
          { key: 'preview', label: '数据预览', children: <BusinessObjectPreviewPanel key={objectType.updatedAt} id={id} definition={objectType.definition} relations={currentRelations} enabled={objectType.enabled && canReadModels} dirty={dirty} initialKey={initialKey} /> },
        ]}
      />

      {editingBasics && <BusinessObjectTypeBasicsDrawer objectType={objectType} onClose={() => setEditingBasics(false)} onSaved={() => setEditingBasics(false)} />}
    </div>
  );
};
