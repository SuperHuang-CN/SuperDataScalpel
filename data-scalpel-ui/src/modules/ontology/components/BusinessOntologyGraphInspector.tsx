import {
  ApartmentOutlined,
  BranchesOutlined,
  CheckCircleOutlined,
  EditOutlined,
  ExpandOutlined,
  LinkOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { Button, Collapse, Empty, Input, Space, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { ManagementCode } from '../../../shared/components/ManagementListCells';
import { validateBusinessObjectType } from '../api/businessObjectTypeApi';
import { useBusinessObjectType } from '../hooks/useBusinessObjectTypes';
import type {
  BusinessObjectTypeGraphNode,
  BusinessObjectTypeGraphRelation,
  BusinessObjectTypeValidation,
  RelationCardinality,
} from '../model/businessObjectType';
import { capabilityKindLabels, relationCardinalityLabels } from '../model/businessObjectType';
import type { OntologyGraphSelection } from './BusinessOntologyGraphCanvas';
import { FieldName } from './BusinessObjectDefinitionPanels';

interface Props {
  selection: OntologyGraphSelection;
  nodes: BusinessObjectTypeGraphNode[];
  relations: BusinessObjectTypeGraphRelation[];
  canManage: boolean;
  canReadModels: boolean;
  hiddenNeighborCount: number;
  onClose: () => void;
  onFocus: (id: string) => void;
  onExpand: (id: string) => void;
  onOpenObject: (id: string, tab?: string) => void;
  onOpenRelation: (relation: BusinessObjectTypeGraphRelation) => void;
  onValidated: (id: string, result: BusinessObjectTypeValidation) => void;
}

const reverseCardinality = (value: RelationCardinality | null) => value === 'ONE_TO_MANY'
  ? 'MANY_TO_ONE'
  : value === 'MANY_TO_ONE' ? 'ONE_TO_MANY' : value;

const issueTab = (path: string) => path.startsWith('mainSource') || path.startsWith('supplements')
  ? 'sources'
  : path.startsWith('properties') || path.startsWith('groups')
    ? 'properties'
    : path.startsWith('relations') ? 'relations' : path.startsWith('capabilities') ? 'capabilities' : 'basic';

const fieldLabel = (name: string | null, code: string | null, id: string | null) => name || code
  ? `${name || '未命名字段'}${code ? `（${code}）` : ''}`
  : id ? `字段信息不可用（${id}）` : '尚未配置';

export const BusinessOntologyGraphInspector = ({
  selection,
  nodes,
  relations,
  canManage,
  canReadModels,
  hiddenNeighborCount,
  onClose,
  onFocus,
  onExpand,
  onOpenObject,
  onOpenRelation,
  onValidated,
}: Props) => {
  const selectedNode = selection.kind === 'node' ? nodes.find((node) => node.id === selection.id) : undefined;
  const selectedRelation = selection.kind === 'relation' ? relations.find((relation) => relation.id === selection.id) : undefined;
  const detail = useBusinessObjectType(selectedNode?.id ?? '');
  const [propertyKeyword, setPropertyKeyword] = useState('');
  const validation = useMutation({
    mutationFn: () => validateBusinessObjectType(selectedNode?.id ?? ''),
    onSuccess: (result) => {
      if (selectedNode) onValidated(selectedNode.id, result);
    },
  });
  const propertiesByGroup = useMemo(() => {
    const definition = detail.data?.definition;
    if (!definition) return [];
    const keyword = propertyKeyword.trim().toLowerCase();
    const properties = definition.properties.filter((property) => !keyword
      || property.name.toLowerCase().includes(keyword)
      || property.code.toLowerCase().includes(keyword));
    const groups = [...definition.groups].sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0));
    return [
      ...groups.map((group) => ({ id: group.id, name: group.name, properties: properties.filter((property) => property.groupId === group.id) })),
      { id: 'ungrouped', name: '未分组', properties: properties.filter((property) => !property.groupId) },
    ].filter((group) => group.properties.length > 0);
  }, [detail.data?.definition, propertyKeyword]);

  if (!selectedNode && !selectedRelation) {
    return <div className="ontology-graph-inspector-empty"><ApartmentOutlined /><span>选择对象类型或关系查看详情</span></div>;
  }

  if (selectedRelation) {
    const cardinality = selectedRelation.cardinality;
    return (
      <div className="ontology-graph-inspector-content">
        <div className="ontology-graph-inspector-heading">
          <span className="ontology-graph-inspector-icon"><LinkOutlined /></span>
          <div><Typography.Title level={5}>{selectedRelation.forwardName || '未命名关系'}</Typography.Title><ManagementCode value={selectedRelation.code || '未设置编码'} /></div>
          <Button type="text" aria-label="关闭关系详情" onClick={onClose}>关闭</Button>
        </div>
        <div className="ontology-graph-direction-card">
          <div><span>{selectedRelation.sourceObjectTypeName}</span><strong>{selectedRelation.forwardName || '未命名'}</strong><Tag>{cardinality ? relationCardinalityLabels[cardinality] : '未设置数量'}</Tag></div>
          <BranchesOutlined />
          <div><span>{selectedRelation.targetObjectTypeName || '目标引用失效'}</span><strong>{selectedRelation.reverseName || '未命名'}</strong><Tag>{reverseCardinality(cardinality) ? relationCardinalityLabels[reverseCardinality(cardinality) as RelationCardinality] : '未设置数量'}</Tag></div>
        </div>
        <BusinessDetailDescriptions column={1} items={[
          { key: 'forward-code', label: '正向访问编码', children: <ManagementCode value={selectedRelation.forwardAccessCode || '未设置'} /> },
          { key: 'reverse-code', label: '反向访问编码', children: <ManagementCode value={selectedRelation.reverseAccessCode || '未设置'} /> },
          { key: 'mapping', label: '身份映射', children: <code className="ontology-graph-mapping-code">{selectedRelation.sourceObjectTypeName}.{fieldLabel(selectedRelation.sourceFieldName, selectedRelation.sourceFieldCode, selectedRelation.sourceFieldId)} = {selectedRelation.targetObjectTypeName || '目标对象'}.{fieldLabel(selectedRelation.targetFieldName, selectedRelation.targetFieldCode, selectedRelation.targetFieldId)}</code> },
          { key: 'description', label: '业务说明', children: selectedRelation.description || '未填写' },
        ]} />
        <div className="ontology-graph-inspector-footer">
          <Button type="primary" icon={canManage ? <EditOutlined /> : <SearchOutlined />} onClick={() => onOpenRelation(selectedRelation)}>{canManage ? '编辑关系' : '查看关系定义'}</Button>
        </div>
      </div>
    );
  }

  const node = selectedNode as BusinessObjectTypeGraphNode;
  const objectType = detail.data;
  const definition = objectType?.definition;
  const pendingRelations = relations.filter((relation) => relation.sourceObjectTypeId === node.id && !relation.targetObjectTypeName);
  return (
    <div className="ontology-graph-inspector-content">
      <div className="ontology-graph-inspector-heading">
        <span className="ontology-graph-inspector-icon"><ApartmentOutlined /></span>
        <div><Typography.Title level={5}>{node.name}</Typography.Title><ManagementCode value={node.code} /></div>
        <Button type="text" aria-label="关闭对象详情" onClick={onClose}>关闭</Button>
      </div>
      <Space size={6} wrap>
        <Tag color={node.enabled ? 'success' : 'default'}>{node.enabled ? '启用' : '停用'}</Tag>
        <Tag>配置：{validation.data ? validation.data.canPreview ? '已校验' : '有问题' : '未校验'}</Tag>
        <Tag>来源可用性：未检查</Tag>
      </Space>
      {validation.isError && <InlineFeedback tone="error" label={validation.error.message} />}
      {validation.data && !validation.data.canPreview && (
        <div className="ontology-graph-validation-list">
          {validation.data.issues.map((issue, index) => (
            <button key={`${issue.code}-${issue.path}-${index}`} type="button" onClick={() => onOpenObject(node.id, issueTab(issue.path))}>
              <strong>{issue.message}</strong><span>{issue.path}</span>
            </button>
          ))}
        </div>
      )}
      <Collapse
        ghost
        defaultActiveKey={['basic', 'source']}
        items={[
          {
            key: 'basic', label: '基本信息', children: <BusinessDetailDescriptions column={1} items={[
              { key: 'name', label: '对象类型', children: node.name },
              { key: 'code', label: '稳定编码', children: <ManagementCode value={node.code} /> },
              { key: 'owner', label: '负责人', children: objectType?.ownerName || '未填写' },
              { key: 'summary', label: '业务定义', children: objectType?.summary || '未填写' },
            ]} />,
          },
          {
            key: 'source', label: '来源与身份', children: <BusinessDetailDescriptions column={1} items={[
              { key: 'main', label: '主来源', children: node.mainSourceModelId ? node.mainSourceModelName || '来源信息不可用' : '尚未配置' },
              { key: 'identity', label: '唯一标识', children: canReadModels && definition?.mainSource ? <FieldName modelId={definition.mainSource.modelId} fieldId={definition.mainSource.identityFieldId} /> : canReadModels ? '尚未配置' : '需要模型查看权限' },
              { key: 'title', label: '显示名称', children: canReadModels && definition?.mainSource ? <FieldName modelId={definition.mainSource.modelId} fieldId={definition.mainSource.titleFieldId} /> : canReadModels ? '尚未配置' : '需要模型查看权限' },
              { key: 'supplements', label: '补充来源', children: `${node.supplementCount} 个` },
            ]} />,
          },
          {
            key: 'properties', label: `属性与分组（${node.propertyCount} / ${node.groupCount}）`, children: <>
              <Input allowClear prefix={<SearchOutlined />} placeholder="查找属性名称或编码" value={propertyKeyword} onChange={(event) => setPropertyKeyword(event.target.value)} />
              <div className="ontology-graph-property-groups">
                {propertiesByGroup.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={detail.isLoading ? '正在读取属性…' : '没有匹配属性'} /> : propertiesByGroup.map((group) => (
                  <div key={group.id}><strong>{group.name}</strong>{group.properties.map((property) => <span key={property.id}>{property.name}<ManagementCode value={property.code} /></span>)}</div>
                ))}
              </div>
            </>,
          },
          {
            key: 'capabilities', label: `业务能力（${node.capabilityCount}）`, children: definition?.capabilities.length ? <div className="ontology-graph-capabilities">{definition.capabilities.map((capability) => <div key={capability.id}><strong>{capability.name || '未命名能力'}</strong><Tag>{capability.kind ? capabilityKindLabels[capability.kind] : '未设置类型'}</Tag><span>仅定义，未接入执行</span></div>)}</div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未登记业务能力" />,
          },
        ]}
      />
      {pendingRelations.length > 0 && <InlineFeedback tone="warning" label={`${pendingRelations.length} 条关系的目标待补齐或已失效`} />}
      {hiddenNeighborCount > 0 && <InlineFeedback tone="info" label={`还有 ${hiddenNeighborCount} 个直接关联对象未展示`} />}
      <div className="ontology-graph-inspector-footer">
        <Space wrap>
          <Button icon={<CheckCircleOutlined />} loading={validation.isPending} disabled={!canReadModels} onClick={() => validation.mutate()}>校验配置</Button>
          <Button icon={<ExpandOutlined />} onClick={() => onExpand(node.id)}>展开关联</Button>
          <Button onClick={() => onFocus(node.id)}>聚焦此对象</Button>
          <Button type="primary" icon={canManage ? <EditOutlined /> : <SearchOutlined />} onClick={() => onOpenObject(node.id)}>{canManage ? '编辑对象' : '查看详情'}</Button>
        </Space>
      </div>
    </div>
  );
};
