import { DatabaseOutlined, LinkOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { Button, Empty, Input, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { ManagementCode } from '../../../shared/components/ManagementListCells';
import {
  queryBusinessObjectCandidates,
  queryBusinessObjectPreview,
  queryBusinessObjectRelated,
} from '../api/businessObjectTypeApi';
import type {
  BusinessObjectPropertyValue,
  BusinessObjectRelationSummary,
  BusinessObjectTypeDefinition,
} from '../model/businessObjectType';

type RelationRequest = { relationId: string; direction: 'OUTBOUND' | 'INBOUND'; pageNo: number };
const statusLabels = { MATCHED: '已匹配', NULL: '空值', NO_MATCH: '未匹配', ERROR: '读取异常' };
const statusColors = { MATCHED: 'success', NULL: 'default', NO_MATCH: 'warning', ERROR: 'error' } as const;

export const BusinessObjectPreviewPanel = ({ id, definition, relations, enabled, dirty, initialKey }: {
  id: string;
  definition: BusinessObjectTypeDefinition;
  relations: BusinessObjectRelationSummary[];
  enabled: boolean;
  dirty: boolean;
  initialKey: string;
}) => {
  const navigate = useNavigate();
  const [key, setKey] = useState(initialKey);
  const [selectedRelation, setSelectedRelation] = useState<RelationRequest | null>(null);
  const candidates = useMutation({ mutationFn: (pageNo: number) => queryBusinessObjectCandidates(id, pageNo, 10) });
  const preview = useMutation({ mutationFn: (objectKey: string) => queryBusinessObjectPreview(id, objectKey) });
  const related = useMutation({ mutationFn: (request: RelationRequest) => queryBusinessObjectRelated(id, { objectKey: preview.data!.object.objectKey, ...request, pageSize: 10 }) });
  const loadInitial = preview.mutate;

  useEffect(() => {
    if (initialKey && enabled && !dirty) loadInitial(initialKey);
  }, [initialKey, enabled, dirty, loadInitial]);

  const blocked = dirty || !enabled;
  const read = (objectKey: string) => {
    if (blocked) return;
    setKey(objectKey);
    related.reset();
    setSelectedRelation(null);
    preview.mutate(objectKey);
  };
  const groups = [...definition.groups].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  const groupOptions = [...groups.map((group) => ({ id: group.id as string | null, name: group.name })), { id: null, name: '未分组' }];
  const propertyColumns = [
    { title: '属性', dataIndex: 'name', width: 180 },
    { title: '当前值', render: (_: unknown, value: BusinessObjectPropertyValue) => <>{value.value == null ? '—' : String(value.value)}{value.unit ? ` ${value.unit}` : ''}</> },
    {
      title: '来源与状态', width: 360,
      render: (_: unknown, value: BusinessObjectPropertyValue) => (
        <Space orientation="vertical" size={2}>
          <span>{value.sourceModelName ?? '未知来源'} · {value.sourceFieldCode ?? '未知字段'} <Tag color={statusColors[value.sourceStatus]}>{statusLabels[value.sourceStatus]}</Tag></span>
          <Typography.Text type="secondary">数据时间：{value.sourceDataTime == null ? '未知' : String(value.sourceDataTime)}{value.diagnostic ? ` · ${value.diagnostic}` : ''}</Typography.Text>
        </Space>
      ),
    },
  ];
  const pager = (pageNo: number, hasNext: boolean, loading: boolean, change: (page: number) => void) => (
    <Space>
      <Button size="small" disabled={blocked || loading || pageNo <= 1} onClick={() => change(pageNo - 1)}>上一页</Button>
      <span>第 {pageNo} 页</span>
      <Button size="small" disabled={blocked || loading || !hasNext} onClick={() => change(pageNo + 1)}>下一页</Button>
    </Space>
  );

  return (
    <div className="ontology-tab-stack ontology-preview-panel">
      {dirty && <InlineFeedback tone="warning" label="配置有未保存修改，请先保存再读取对象。" />}
      {!enabled && !dirty && <InlineFeedback tone="warning" label="对象类型未启用或缺少模型查看权限，当前不能读取数据。" />}

      <BusinessDetailSection
        title="选择具体对象"
        description="输入业务唯一标识，或从主来源读取一页候选对象。"
        icon={<SearchOutlined />}
        extra={candidates.data && pager(candidates.data.pageNo, candidates.data.hasNext, candidates.isPending, (page) => candidates.mutate(page))}
      >
        <div className="ontology-preview-search">
          <Input autoComplete="off" value={key} placeholder="输入对象唯一标识" onChange={(event) => setKey(event.target.value)} onPressEnter={() => key && read(key)} />
          <Button type="primary" disabled={blocked || !key} loading={preview.isPending} onClick={() => read(key)}>读取对象</Button>
          <Button disabled={blocked} loading={candidates.isPending} onClick={() => candidates.mutate(1)}>查询候选对象</Button>
        </div>
        {candidates.isError && <InlineFeedback tone="error" label={candidates.error.message} />}
        {candidates.data && (
          <Table
            size="small"
            pagination={false}
            rowKey="objectKey"
            dataSource={candidates.data.items}
            columns={[
              { title: '唯一标识', dataIndex: 'objectKey', render: (value) => <ManagementCode value={String(value)} /> },
              { title: '显示名称', dataIndex: 'title' },
              { title: '操作', width: 80, render: (_, candidate) => <Button type="link" disabled={blocked} onClick={() => read(candidate.objectKey)}>预览</Button> },
            ]}
          />
        )}
      </BusinessDetailSection>

      {preview.isError && <InlineFeedback tone="error" label={preview.error.message} />}
      {preview.data && (
        <>
          <BusinessDetailSection title="对象属性" description="按业务属性组展示组合结果，并保留每个值的来源与数据时间。" icon={<DatabaseOutlined />}>
            <BusinessDetailDescriptions
              column={3}
              items={[
                { key: 'title', label: '当前对象', children: preview.data.object.title },
                { key: 'key', label: '唯一标识', children: <ManagementCode value={preview.data.object.objectKey} /> },
                { key: 'time', label: '读取时间', children: new Date(preview.data.queriedAt).toLocaleString() },
              ]}
            />
            <div className="ontology-preview-groups">
              {groupOptions.map((group) => {
                const properties = preview.data.properties.filter((value) => value.groupId === group.id);
                return properties.length ? (
                  <div className="ontology-preview-group" key={group.id ?? 'ungrouped'}>
                    <div className="ontology-subsection-title">{group.name}<span>{properties.length} 个属性</span></div>
                    <Table size="small" rowKey="propertyId" pagination={false} dataSource={properties} columns={propertyColumns} />
                  </div>
                ) : null;
              })}
            </div>
            {preview.data.diagnostics.map((issue) => <InlineFeedback key={issue.path} tone="warning" label={`${issue.message}（${issue.path}）`} />)}
          </BusinessDetailSection>

          <BusinessDetailSection
            title="关联对象"
            description="选择关系读取实际关联对象，并可继续进入目标对象。"
            icon={<LinkOutlined />}
            extra={related.data && pager(related.data.pageNo, related.data.hasNext, related.isPending, (pageNo) => selectedRelation && related.mutate({ ...selectedRelation, pageNo }))}
          >
            {relations.length ? (
              <div className="ontology-relation-choices">
                {relations.map((relation) => (
                  <Button
                    key={`${relation.id}:${relation.direction}`}
                    type={selectedRelation?.relationId === relation.id && selectedRelation.direction === relation.direction ? 'primary' : 'default'}
                    disabled={blocked}
                    loading={related.isPending && selectedRelation?.relationId === relation.id && selectedRelation.direction === relation.direction}
                    onClick={() => {
                      const request = { relationId: relation.id, direction: relation.direction, pageNo: 1 };
                      setSelectedRelation(request);
                      related.mutate(request);
                    }}
                  >
                    {relation.name}
                  </Button>
                ))}
              </div>
            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未配置业务关系" />}
            {related.isError && <InlineFeedback tone="error" label={related.error.message} />}
            {related.data?.diagnostics.map((issue) => <InlineFeedback key={issue.code} tone="warning" label={issue.message} />)}
            {related.data && (
              <Table
                size="small"
                pagination={false}
                rowKey="objectKey"
                dataSource={related.data.items}
                columns={[
                  { title: '唯一标识', dataIndex: 'objectKey', render: (value) => <ManagementCode value={String(value)} /> },
                  { title: '显示名称', dataIndex: 'title' },
                  {
                    title: '操作', width: 100,
                    render: (_, candidate) => <Button type="link" disabled={blocked} onClick={() => {
                      const relation = related.data.relation;
                      const targetId = relation.direction === 'OUTBOUND' ? relation.targetObjectTypeId : relation.sourceObjectTypeId;
                      navigate(`/business-object-types/${targetId}?objectKey=${encodeURIComponent(candidate.objectKey)}`);
                    }}>查看对象</Button>,
                  },
                ]}
              />
            )}
          </BusinessDetailSection>
        </>
      )}
    </div>
  );
};
