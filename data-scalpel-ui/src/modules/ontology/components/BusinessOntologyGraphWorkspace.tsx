import {
  AimOutlined,
  ApartmentOutlined,
  NodeIndexOutlined,
  ReloadOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons';
import { Button, Drawer, Empty, Space, Spin, Tooltip, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type {
  BusinessObjectTypeGraph,
  BusinessObjectTypeGraphNode,
  BusinessObjectTypeGraphRelation,
  BusinessObjectTypeValidation,
} from '../model/businessObjectType';
import {
  BusinessOntologyGraphCanvas,
  type BusinessOntologyGraphCanvasHandle,
  type OntologyGraphSelection,
} from './BusinessOntologyGraphCanvas';
import { BusinessOntologyGraphInspector } from './BusinessOntologyGraphInspector';

const MAX_VISIBLE_NODES = 80;

interface Props {
  graph?: BusinessObjectTypeGraph;
  scopeNodes: BusinessObjectTypeGraphNode[];
  focusId?: string;
  loading: boolean;
  errorMessage?: string;
  emptyDescription: string;
  canManage: boolean;
  canReadModels: boolean;
  onRetry: () => void;
  onFocusChange: (id?: string) => void;
  onOpenObject: (id: string, tab?: string) => void;
  onOpenRelation: (relation: BusinessObjectTypeGraphRelation) => void;
}

export const BusinessOntologyGraphWorkspace = ({
  graph,
  scopeNodes,
  focusId,
  loading,
  errorMessage,
  emptyDescription,
  canManage,
  canReadModels,
  onRetry,
  onFocusChange,
  onOpenObject,
  onOpenRelation,
}: Props) => {
  const [messageApi, messageContext] = message.useMessage();
  const graphHandle = useRef<BusinessOntologyGraphCanvasHandle>(null);
  const workspaceRef = useRef<HTMLDivElement>(null);
  const [selection, setSelection] = useState<OntologyGraphSelection | null>(focusId ? { kind: 'node', id: focusId } : null);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [layoutRevision, setLayoutRevision] = useState(0);
  const [overlayInspector, setOverlayInspector] = useState(false);
  const [validationStates, setValidationStates] = useState<Map<string, 'valid' | 'invalid'>>(new Map());
  const allNodes = useMemo(() => graph?.nodes ?? [], [graph?.nodes]);
  const relations = useMemo(() => graph?.relations ?? [], [graph?.relations]);
  const allIds = useMemo(() => new Set(allNodes.map((node) => node.id)), [allNodes]);
  const scopeIds = useMemo(() => new Set(scopeNodes.map((node) => node.id)), [scopeNodes]);
  const adjacency = useMemo(() => {
    const result = new Map(allNodes.map((node) => [node.id, new Set<string>()]));
    relations.forEach((relation) => {
      if (!relation.targetObjectTypeId || !allIds.has(relation.sourceObjectTypeId) || !allIds.has(relation.targetObjectTypeId)) return;
      result.get(relation.sourceObjectTypeId)?.add(relation.targetObjectTypeId);
      result.get(relation.targetObjectTypeId)?.add(relation.sourceObjectTypeId);
    });
    return result;
  }, [allIds, allNodes, relations]);

  const scopeKey = `${scopeNodes.map((node) => node.id).sort().join(',')}|${focusId ?? ''}`;
  useEffect(() => {
    const workspace = workspaceRef.current;
    if (!workspace) return undefined;
    const observer = new ResizeObserver(([entry]) => setOverlayInspector(entry.contentRect.width < 1000));
    observer.observe(workspace);
    return () => observer.disconnect();
  }, []);

  const baseIds = useMemo(() => {
    if (!focusId || !allIds.has(focusId)) return scopeNodes.length > MAX_VISIBLE_NODES ? new Set<string>() : new Set(scopeNodes.map((node) => node.id));
    const neighborIds = [...(adjacency.get(focusId) ?? [])].sort((left, right) => {
      const a = allNodes.find((node) => node.id === left);
      const b = allNodes.find((node) => node.id === right);
      return `${a?.name ?? ''}:${left}`.localeCompare(`${b?.name ?? ''}:${right}`);
    });
    return new Set([focusId, ...neighborIds.slice(0, MAX_VISIBLE_NODES - 1)]);
  }, [adjacency, allIds, allNodes, focusId, scopeNodes]);
  const visibleIds = useMemo(() => {
    const result = new Set(baseIds);
    [...expandedIds].sort().some((id) => {
      if (result.size >= MAX_VISIBLE_NODES) return true;
      result.add(id);
      return false;
    });
    return result;
  }, [baseIds, expandedIds]);
  const visibleNodes = useMemo(() => allNodes.filter((node) => visibleIds.has(node.id)), [allNodes, visibleIds]);
  const visibleRelations = useMemo(() => relations.filter((relation) => relation.id && relation.targetObjectTypeId
    && visibleIds.has(relation.sourceObjectTypeId) && visibleIds.has(relation.targetObjectTypeId)), [relations, visibleIds]);
  const outOfScopeIds = useMemo(() => new Set([...visibleIds].filter((id) => !scopeIds.has(id))), [scopeIds, visibleIds]);
  const selectedNodeId = selection?.kind === 'node' ? selection.id : undefined;
  const hiddenNeighborCount = selectedNodeId
    ? [...(adjacency.get(selectedNodeId) ?? [])].filter((id) => !visibleIds.has(id)).length
    : 0;
  const capacityExceeded = !focusId && scopeNodes.length > MAX_VISIBLE_NODES;
  const missingFocus = Boolean(focusId && !allIds.has(focusId));

  const expand = (id: string) => {
    const candidates = [...(adjacency.get(id) ?? [])].filter((candidate) => !visibleIds.has(candidate));
    if (candidates.length === 0) {
      messageApi.info('这个对象的直接关联已经全部展示');
      return;
    }
    const available = MAX_VISIBLE_NODES - visibleIds.size;
    if (available <= 0) {
      messageApi.warning('画布已达到 80 个对象类型，请重新聚焦后继续浏览');
      return;
    }
    const additions = candidates.slice(0, available);
    setExpandedIds((current) => new Set([...current, ...additions]));
    if (additions.length < candidates.length) messageApi.warning(`已加入 ${additions.length} 个对象，另有 ${candidates.length - additions.length} 个因容量限制未展示`);
  };
  const select = useCallback((next: OntologyGraphSelection | null) => setSelection(next), []);
  const validated = (id: string, result: BusinessObjectTypeValidation) => setValidationStates((current) => new Map(current).set(id, result.canPreview ? 'valid' : 'invalid'));
  const relayout = () => {
    setLayoutRevision((value) => value + 1);
    requestAnimationFrame(() => graphHandle.current?.fit());
  };
  const inspector = selection ? (
    <BusinessOntologyGraphInspector
      selection={selection}
      nodes={allNodes}
      relations={relations}
      canManage={canManage}
      canReadModels={canReadModels}
      hiddenNeighborCount={hiddenNeighborCount}
      onClose={() => setSelection(null)}
      onFocus={(id) => onFocusChange(id)}
      onExpand={expand}
      onOpenObject={onOpenObject}
      onOpenRelation={onOpenRelation}
      onValidated={validated}
    />
  ) : null;

  return (
    <div ref={workspaceRef} className="ontology-graph-workspace">
      {messageContext}
      <div className="ontology-graph-toolbar">
        <div className="ontology-graph-counts">
          <strong>{capacityExceeded ? '范围过大，等待选择焦点' : `已展示 ${visibleNodes.length} 个对象类型、${visibleRelations.length} 条关系`}</strong>
          {outOfScopeIds.size > 0 && <span>{outOfScopeIds.size} 个范围外关联</span>}
          {focusId && <Button type="link" size="small" onClick={() => onFocusChange(undefined)}>返回筛选范围</Button>}
        </div>
        <Space size={4}>
          <Tooltip title="缩小"><Button type="text" icon={<ZoomOutOutlined />} disabled={!visibleNodes.length} aria-label="缩小本体总览" onClick={() => graphHandle.current?.zoomOut()} /></Tooltip>
          <Tooltip title="放大"><Button type="text" icon={<ZoomInOutlined />} disabled={!visibleNodes.length} aria-label="放大本体总览" onClick={() => graphHandle.current?.zoomIn()} /></Tooltip>
          <Button type="text" icon={<AimOutlined />} disabled={!visibleNodes.length} onClick={() => graphHandle.current?.fit()}>适应画布</Button>
          <Button type="text" icon={<NodeIndexOutlined />} disabled={!visibleNodes.length} onClick={relayout}>重新布局</Button>
          <Tooltip title="重新读取最近保存的本体结构"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新本体总览" onClick={onRetry} /></Tooltip>
        </Space>
      </div>
      <div className="ontology-graph-body">
        <div className="ontology-graph-stage">
          {loading && <div className="ontology-graph-state"><Spin description="正在读取本体结构" /></div>}
          {!loading && errorMessage && <div className="ontology-graph-state"><Empty description={errorMessage}><Button type="primary" onClick={onRetry}>重试</Button></Empty></div>}
          {!loading && !errorMessage && missingFocus && <div className="ontology-graph-state"><Empty description="焦点对象已不存在或无权访问"><Button onClick={() => onFocusChange(undefined)}>返回总览</Button></Empty></div>}
          {!loading && !errorMessage && capacityExceeded && (
            <div className="ontology-graph-state ontology-graph-capacity-state">
              <ApartmentOutlined />
              <Typography.Title level={5}>当前范围有 {scopeNodes.length} 个对象类型</Typography.Title>
              <Typography.Text type="secondary">同屏最多展示 80 个。请在左侧“对象”页签选择一个对象，查看它和直接邻居。</Typography.Text>
            </div>
          )}
          {!loading && !errorMessage && !missingFocus && !capacityExceeded && visibleNodes.length === 0 && <div className="ontology-graph-state"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription} /></div>}
          {!loading && !errorMessage && !missingFocus && !capacityExceeded && visibleNodes.length > 0 && (
            <BusinessOntologyGraphCanvas
              ref={graphHandle}
              nodes={visibleNodes}
              relations={visibleRelations}
              outOfScopeIds={outOfScopeIds}
              selected={selection}
              validationStates={validationStates}
              layoutKey={`${scopeKey}|layout:${layoutRevision}|focus:${focusId ?? ''}`}
              onSelect={select}
            />
          )}
        </div>
        {!overlayInspector && inspector && <aside className="ontology-graph-inspector">{inspector}</aside>}
      </div>
      {overlayInspector && (
        <Drawer
          rootClassName="business-overlay business-drawer-overlay ontology-graph-inspector-drawer"
          open={Boolean(inspector)}
          size={360}
          title="本体详情"
          mask={false}
          onClose={() => setSelection(null)}
        >
          {inspector}
        </Drawer>
      )}
    </div>
  );
};
