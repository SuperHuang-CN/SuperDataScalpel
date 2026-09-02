import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { Button, Select, Table, Tag, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { fetchDataModel, useDataModels, type DataModelDetail } from '../../model';
import { useCurrentUser } from '../../system';
import { useUpdateDataEntryLookups } from '../hooks/useDataEntry';
import type { DataEntryFormDetail, DataEntryLookupInput } from '../model/dataEntry';

interface LookupDraft {
  sourceModelId?: string;
  sourceLabelFieldId?: string;
  sourceDetail?: DataModelDetail;
}

export const DataEntryFieldConfigPanel = ({ detail }: { detail: DataEntryFormDetail }) => {
  const [drafts, setDrafts] = useState<Record<string, LookupDraft>>(() => Object.fromEntries(
    detail.lookups.map((lookup) => [lookup.targetFieldId, {
      sourceModelId: lookup.sourceModelId,
      sourceLabelFieldId: lookup.sourceLabelFieldId,
    }]),
  ));
  const [messageApi, contextHolder] = message.useMessage();
  const mutation = useUpdateDataEntryLookups();
  const currentUser = useCurrentUser();
  const canManage = new Set(currentUser.data?.permissions ?? []).has('dataentry.manage');
  const modelsQuery = useDataModels({ search: 'status==PUBLISHED', page: 0, size: 500, sort: 'name,code' });

  useEffect(() => {
    void Promise.all(detail.lookups.map(async (lookup) => {
      try {
        const sourceDetail = await fetchDataModel(lookup.sourceModelId);
        setDrafts((current) => ({ ...current, [lookup.targetFieldId]: { ...current[lookup.targetFieldId], sourceDetail } }));
      } catch {
        // Keep orphaned configuration visible until the user saves its removal.
      }
    }));
  }, [detail.lookups]);

  const modelOptions = useMemo(() => (modelsQuery.data?.content ?? []).map((model) => ({
    value: model.id, label: `${model.name}（${model.code}）`,
  })), [modelsQuery.data]);

  const selectSource = async (fieldId: string, sourceModelId?: string) => {
    if (!sourceModelId) {
      setDrafts((current) => ({ ...current, [fieldId]: {} }));
      return;
    }
    setDrafts((current) => ({ ...current, [fieldId]: { sourceModelId } }));
    try {
      const sourceDetail = await fetchDataModel(sourceModelId);
      setDrafts((current) => ({ ...current, [fieldId]: { sourceModelId, sourceDetail } }));
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '读取来源模型字段失败');
    }
  };

  const save = async () => {
    const lookups: DataEntryLookupInput[] = [];
    for (const field of detail.fields) {
      const draft = drafts[field.id];
      if (!draft?.sourceModelId && !draft?.sourceLabelFieldId) continue;
      if (!draft.sourceModelId || !draft.sourceLabelFieldId) {
        messageApi.warning(`请完整配置字段“${field.name}”的来源模型和标签字段`);
        return;
      }
      lookups.push({ targetFieldId: field.id, sourceModelId: draft.sourceModelId, sourceLabelFieldId: draft.sourceLabelFieldId });
    }
    try {
      await mutation.mutateAsync({ id: detail.form.id, lookups });
      messageApi.success('字段下拉配置已保存');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存字段配置失败');
    }
  };

  const configurable = detail.form.status !== 'PUBLISHED' && canManage;
  const orphaned = detail.health.issues.filter((issue) => issue.code.startsWith('LOOKUP_'));

  return (
    <div className="data-entry-tab-panel">
      {contextHolder}
      {!configurable && <Alert type="info" showIcon title="已发布表单的字段配置只读；如需调整，请先停用。" />}
      {orphaned.length > 0 && <Alert type="warning" showIcon title="存在失效的关联下拉配置" description={`${orphaned.map((issue) => issue.message).join('；')}。保存时未包含的孤立配置会被删除。`} />}
      <div className="data-entry-config-toolbar"><Button type="primary" disabled={!configurable} loading={mutation.isPending} onClick={() => void save()}>保存关联下拉配置</Button></div>
      <Table
        size="small"
        rowKey="id"
        pagination={false}
        dataSource={detail.fields}
        columns={[
          { title: '模型字段', key: 'field', width: 240, render: (_, field) => <div><div>{field.name}</div><code>{field.code}</code></div> },
          { title: '类型', dataIndex: 'fieldType', width: 110 },
          { title: '控件来源', key: 'source', width: 150, render: (_, field) => field.standardDictionary ? <Tag color="blue">码表：{field.standardDictionary.name}</Tag> : field.lookup ? <Tag color="purple">关联模型</Tag> : <Tag>默认控件</Tag> },
          {
            title: '关联来源模型', key: 'sourceModel', width: 300,
            render: (_, field) => field.standardDictionary ? '码表字段不可配置' : (
              <Select
                allowClear showSearch optionFilterProp="label" disabled={!configurable}
                value={drafts[field.id]?.sourceModelId}
                options={modelOptions}
                placeholder="不配置则使用默认控件"
                style={{ width: '100%' }}
                onChange={(value) => void selectSource(field.id, value)}
              />
            ),
          },
          {
            title: '标签字段（非空 STRING）', key: 'label', width: 260,
            render: (_, field) => {
              if (field.standardDictionary) return '—';
              const draft = drafts[field.id];
              const options = draft?.sourceDetail?.fields.filter((sourceField) => sourceField.fieldType === 'STRING' && !sourceField.nullable)
                .map((sourceField) => ({ value: sourceField.id, label: `${sourceField.name}（${sourceField.code}）` })) ?? [];
              return <Select disabled={!configurable || !draft?.sourceModelId} value={draft?.sourceLabelFieldId} options={options} style={{ width: '100%' }} placeholder="选择标签字段" onChange={(value) => setDrafts((current) => ({ ...current, [field.id]: { ...current[field.id], sourceLabelFieldId: value } }))} />;
            },
          },
        ]}
        scroll={{ x: 1060, y: '100%' }}
      />
    </div>
  );
};
