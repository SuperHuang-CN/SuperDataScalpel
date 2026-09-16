import { Graph } from '@antv/x6';
import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import type { BusinessObjectTypeGraphNode, BusinessObjectTypeGraphRelation } from '../model/businessObjectType';
import { relationCardinalityLabels } from '../model/businessObjectType';

const NODE_WIDTH = 220;
const NODE_HEIGHT = 112;
const COLUMN_GAP = 150;
const ROW_GAP = 48;

export interface OntologyGraphSelection {
  kind: 'node' | 'relation';
  id: string;
}

export interface BusinessOntologyGraphCanvasHandle {
  zoomIn: () => void;
  zoomOut: () => void;
  fit: () => void;
  centerNode: (id: string) => void;
}

interface Props {
  nodes: BusinessObjectTypeGraphNode[];
  relations: BusinessObjectTypeGraphRelation[];
  outOfScopeIds: ReadonlySet<string>;
  selected: OntologyGraphSelection | null;
  validationStates: Map<string, 'valid' | 'invalid'>;
  layoutKey: string;
  onSelect: (selection: OntologyGraphSelection | null) => void;
}

const relationKey = (relation: BusinessObjectTypeGraphRelation, index: number) => relation.id ?? `pending:${relation.sourceObjectTypeId}:${index}`;
const clipped = (value: string, limit: number) => value.length > limit ? `${value.slice(0, limit - 1)}…` : value;

const layoutNodes = (
  nodes: BusinessObjectTypeGraphNode[],
  relations: BusinessObjectTypeGraphRelation[],
  focusId?: string,
) => {
  const ids = new Set(nodes.map((node) => node.id));
  const adjacency = new Map(nodes.map((node) => [node.id, new Set<string>()]));
  relations.forEach((relation) => {
    if (!relation.targetObjectTypeId || !ids.has(relation.sourceObjectTypeId) || !ids.has(relation.targetObjectTypeId)) return;
    adjacency.get(relation.sourceObjectTypeId)?.add(relation.targetObjectTypeId);
    adjacency.get(relation.targetObjectTypeId)?.add(relation.sourceObjectTypeId);
  });
  const byId = new Map(nodes.map((node) => [node.id, node]));
  const compare = (left: string, right: string) => {
    const a = byId.get(left);
    const b = byId.get(right);
    return `${a?.name ?? ''}:${a?.code ?? ''}:${left}`.localeCompare(`${b?.name ?? ''}:${b?.code ?? ''}:${right}`);
  };
  const unvisited = new Set(nodes.map((node) => node.id));
  const components: string[][] = [];
  while (unvisited.size > 0) {
    const seed = [...unvisited].sort(compare)[0];
    const queue = [seed];
    const component: string[] = [];
    unvisited.delete(seed);
    while (queue.length > 0) {
      const current = queue.shift() as string;
      component.push(current);
      [...(adjacency.get(current) ?? [])].sort(compare).forEach((next) => {
        if (!unvisited.delete(next)) return;
        queue.push(next);
      });
    }
    components.push(component);
  }
  components.sort((left, right) => {
    if (focusId && left.includes(focusId)) return -1;
    if (focusId && right.includes(focusId)) return 1;
    return right.length - left.length || compare(left[0], right[0]);
  });

  const positions = new Map<string, { x: number; y: number }>();
  let componentTop = 52;
  components.forEach((component) => {
    const root = focusId && component.includes(focusId) ? focusId : [...component].sort(compare)[0];
    const rank = new Map<string, number>([[root, 0]]);
    const queue = [root];
    while (queue.length > 0) {
      const current = queue.shift() as string;
      [...(adjacency.get(current) ?? [])].filter((id) => component.includes(id)).sort(compare).forEach((next) => {
        if (rank.has(next)) return;
        rank.set(next, (rank.get(current) ?? 0) + 1);
        queue.push(next);
      });
    }
    const columns = new Map<number, string[]>();
    component.forEach((id) => {
      const value = rank.get(id) ?? 0;
      const column = columns.get(value) ?? [];
      column.push(id);
      columns.set(value, column);
    });
    const componentHeight = Math.max(...[...columns.values()].map((items) => items.length * NODE_HEIGHT + Math.max(0, items.length - 1) * ROW_GAP));
    [...columns.entries()].sort(([left], [right]) => left - right).forEach(([column, items]) => {
      items.sort(compare);
      const columnHeight = items.length * NODE_HEIGHT + Math.max(0, items.length - 1) * ROW_GAP;
      let y = componentTop + (componentHeight - columnHeight) / 2;
      items.forEach((id) => {
        positions.set(id, { x: 64 + column * (NODE_WIDTH + COLUMN_GAP), y });
        y += NODE_HEIGHT + ROW_GAP;
      });
    });
    componentTop += componentHeight + 110;
  });
  return positions;
};

export const BusinessOntologyGraphCanvas = forwardRef<BusinessOntologyGraphCanvasHandle, Props>(({
  nodes,
  relations,
  outOfScopeIds,
  selected,
  validationStates,
  layoutKey,
  onSelect,
}, ref) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const positionsRef = useRef(new Map<string, { x: number; y: number }>());
  const previousLayoutKeyRef = useRef(layoutKey);
  const selectionRef = useRef(selected);
  selectionRef.current = selected;

  useImperativeHandle(ref, () => ({
    zoomIn: () => graphRef.current?.zoom(0.12),
    zoomOut: () => graphRef.current?.zoom(-0.12),
    fit: () => graphRef.current?.zoomToFit({ padding: 46, maxScale: 1 }),
    centerNode: (id) => {
      const node = graphRef.current?.getCellById(id);
      if (node?.isNode()) graphRef.current?.centerCell(node);
    },
  }), []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;
    if (previousLayoutKeyRef.current !== layoutKey) {
      positionsRef.current.clear();
      previousLayoutKeyRef.current = layoutKey;
    }
    const positionCache = positionsRef.current;
    const focusId = layoutKey.split('|focus:')[1]?.split('|')[0] || undefined;
    const calculated = layoutNodes(nodes, relations, focusId);
    const canvas = new Graph({
      container,
      autoResize: true,
      background: { color: 'transparent' },
      grid: {
        visible: true,
        type: 'doubleMesh',
        size: 20,
        args: [{ color: '#e8ebf7', thickness: 1 }, { color: '#f4f5fb', thickness: 1, factor: 5 }],
      },
      panning: { enabled: true, eventTypes: ['leftMouseDown', 'mouseWheel'] },
      mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'], minScale: 0.42, maxScale: 1.8 },
      interacting: { edgeLabelMovable: false },
    });
    nodes.forEach((node) => {
      const position = positionCache.get(node.id) ?? calculated.get(node.id) ?? { x: 64, y: 52 };
      const validation = validationStates.get(node.id);
      const outOfScope = outOfScopeIds.has(node.id);
      canvas.addNode({
        id: node.id,
        x: position.x,
        y: position.y,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
        data: node,
        markup: [
          { tagName: 'rect', selector: 'body' },
          { tagName: 'rect', selector: 'accent' },
          { tagName: 'text', selector: 'title' },
          { tagName: 'text', selector: 'code' },
          { tagName: 'rect', selector: 'scopeBackground' },
          { tagName: 'text', selector: 'scope' },
          { tagName: 'text', selector: 'source' },
          { tagName: 'text', selector: 'counts' },
          { tagName: 'rect', selector: 'statusBackground' },
          { tagName: 'text', selector: 'status' },
          { tagName: 'text', selector: 'diagnostic' },
          { tagName: 'title', selector: 'tooltip' },
        ],
        attrs: {
          body: { refWidth: '100%', refHeight: '100%', rx: 12, ry: 12, fill: node.enabled ? '#fff' : '#f6f7fa', stroke: node.enabled ? '#cbd4ee' : '#d5d8e0', strokeWidth: 1.2 },
          accent: { x: 0, y: 0, width: 4, refHeight: '100%', rx: 2, fill: node.enabled ? '#596fe7' : '#a8afc2' },
          title: { refX: 16, refY: 23, text: clipped(node.name, 12), textAnchor: 'start', textVerticalAnchor: 'middle', fill: node.enabled ? '#202b49' : '#626a79', fontSize: 15, fontWeight: 600 },
          code: { refX: 16, refY: 46, text: clipped(node.code, outOfScope ? 17 : 25), textAnchor: 'start', textVerticalAnchor: 'middle', fill: node.enabled ? '#73809e' : '#969daa', fontSize: 11, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' },
          scopeBackground: { x: 158, y: 37, width: 50, height: 18, rx: 9, ry: 9, fill: '#f0edff', stroke: '#d9d2ff', strokeWidth: 0.8, opacity: outOfScope ? 1 : 0 },
          scope: { refX: 183, refY: 46, text: '范围外', textAnchor: 'middle', textVerticalAnchor: 'middle', fill: '#6b5acb', fontSize: 9, opacity: outOfScope ? 1 : 0 },
          source: { refX: 16, refY: 73, text: clipped(`主来源：${node.mainSourceModelId ? node.mainSourceModelName ?? '来源信息不可用' : '尚未配置'}`, 23), textAnchor: 'start', textVerticalAnchor: 'middle', fill: node.enabled ? '#52617f' : '#858c99', fontSize: 12 },
          counts: { refX: 16, refY: 96, text: `${node.propertyCount} 个属性 · ${node.groupCount} 个组`, textAnchor: 'start', textVerticalAnchor: 'middle', fill: node.enabled ? '#7c87a0' : '#a0a5af', fontSize: 11 },
          statusBackground: { x: 169, y: 12, width: 39, height: 20, rx: 10, ry: 10, fill: node.enabled ? '#edf8f2' : '#f1f2f5', stroke: 'none' },
          status: { refX: 188.5, refY: 22, text: node.enabled ? '启用' : '停用', textAnchor: 'middle', textVerticalAnchor: 'middle', fill: node.enabled ? '#2f8a5e' : '#7c8493', fontSize: 10 },
          diagnostic: { refX: 204, refY: 96, text: validation === 'valid' ? '已校验' : validation === 'invalid' ? '有问题' : '未校验', textAnchor: 'end', textVerticalAnchor: 'middle', fill: validation === 'valid' ? '#2f8a5e' : validation === 'invalid' ? '#c47a24' : '#9aa2b3', fontSize: 10 },
          tooltip: { text: `${node.name}\n${node.code}\n${node.mainSourceModelId ? node.mainSourceModelName ?? '来源信息不可用' : '尚未配置主来源'}` },
        },
      });
    });

    const visibleIds = new Set(nodes.map((node) => node.id));
    const pairOffsets = new Map<string, number>();
    relations.forEach((relation, index) => {
      if (!relation.id || !relation.targetObjectTypeId || !visibleIds.has(relation.sourceObjectTypeId) || !visibleIds.has(relation.targetObjectTypeId)) return;
      const selfLoop = relation.sourceObjectTypeId === relation.targetObjectTypeId;
      const pair = [relation.sourceObjectTypeId, relation.targetObjectTypeId].sort().join(':');
      const pairIndex = pairOffsets.get(pair) ?? 0;
      pairOffsets.set(pair, pairIndex + 1);
      const cardinality = relation.cardinality ? relationCardinalityLabels[relation.cardinality] : '未设置数量';
      const defaultLabel = `${relation.forwardName || '未命名关系'} · ${cardinality}`;
      const sourcePosition = positionCache.get(relation.sourceObjectTypeId) ?? calculated.get(relation.sourceObjectTypeId) ?? { x: 0, y: 0 };
      const edge = canvas.addEdge({
        id: relationKey(relation, index),
        source: { cell: relation.sourceObjectTypeId, anchor: selfLoop ? 'top' : 'right' },
        target: { cell: relation.targetObjectTypeId, anchor: selfLoop ? 'right' : 'left' },
        data: { relation, defaultLabel },
        ...(selfLoop ? {
          vertices: [
            { x: sourcePosition.x + NODE_WIDTH / 2, y: sourcePosition.y - 58 },
            { x: sourcePosition.x + NODE_WIDTH + 68, y: sourcePosition.y - 58 },
            { x: sourcePosition.x + NODE_WIDTH + 68, y: sourcePosition.y + NODE_HEIGHT / 2 },
          ],
          connector: { name: 'rounded' },
        } : pairIndex > 0 ? {
          connector: { name: 'smooth' },
          vertices: [{
            x: sourcePosition.x + NODE_WIDTH + 58,
            y: sourcePosition.y - 30 - pairIndex * 25,
          }],
        } : { connector: { name: 'rounded' } }),
        attrs: {
          line: { stroke: '#7588ce', strokeWidth: 1.5, targetMarker: { name: 'block', width: 8, height: 7 }, strokeLinejoin: 'round' },
        },
        labels: [{
          position: selfLoop ? 0.58 : 0.5,
          attrs: {
            label: { text: defaultLabel, fill: '#52617f', fontSize: 11 },
            body: { fill: '#f8f9ff', stroke: '#d9def1', strokeWidth: 1, rx: 5, ry: 5, refWidth: '112%', refHeight: '150%', refX: '-6%', refY: '-25%' },
          },
        }],
      });
      edge.toBack();
    });

    const highlight = (selection: OntologyGraphSelection | null) => {
      const selectedNodeId = selection?.kind === 'node' ? selection.id : undefined;
      const selectedRelationId = selection?.kind === 'relation' ? selection.id : undefined;
      const neighborIds = new Set<string>();
      const connectedRelationIds = new Set<string>();
      if (selectedNodeId) {
        neighborIds.add(selectedNodeId);
        relations.forEach((relation) => {
          if (!relation.id) return;
          if (relation.sourceObjectTypeId === selectedNodeId || relation.targetObjectTypeId === selectedNodeId) {
            connectedRelationIds.add(relation.id);
            neighborIds.add(relation.sourceObjectTypeId);
            if (relation.targetObjectTypeId) neighborIds.add(relation.targetObjectTypeId);
          }
        });
      }
      canvas.getNodes().forEach((cell) => {
        const active = !selection || selection.kind === 'relation' || neighborIds.has(cell.id);
        const node = cell.getData<BusinessObjectTypeGraphNode>();
        ['body', 'accent', 'title', 'code', 'source', 'counts', 'status', 'statusBackground', 'diagnostic'].forEach((selector) => cell.attr(`${selector}/opacity`, active ? 1 : 0.34));
        const outOfScope = outOfScopeIds.has(cell.id);
        const scopeOpacity = outOfScope ? (active ? 1 : 0.34) : 0;
        cell.attr('scopeBackground/opacity', scopeOpacity);
        cell.attr('scope/opacity', scopeOpacity);
        cell.attr('body/stroke', cell.id === selectedNodeId ? '#5068dc' : node.enabled ? '#cbd4ee' : '#d5d8e0');
        cell.attr('body/strokeWidth', cell.id === selectedNodeId ? 2 : 1.2);
      });
      canvas.getEdges().forEach((cell) => {
        const data = cell.getData<{ relation: BusinessObjectTypeGraphRelation; defaultLabel: string }>();
        const active = !selection || data.relation.id === selectedRelationId || connectedRelationIds.has(data.relation.id ?? '');
        cell.attr('line/opacity', active ? 1 : 0.16);
        cell.attr('line/stroke', data.relation.id === selectedRelationId ? '#5b4ed5' : '#7588ce');
        cell.attr('line/strokeWidth', data.relation.id === selectedRelationId ? 2.4 : 1.5);
        cell.setLabels(cell.getLabels().map((label) => ({ ...label, attrs: { ...label.attrs, label: { ...label.attrs?.label, opacity: active ? 1 : 0.22 } } })));
      });
    };
    canvas.on('node:click', ({ node }) => onSelect({ kind: 'node', id: node.id }));
    canvas.on('edge:click', ({ edge }) => {
      const data = edge.getData<{ relation: BusinessObjectTypeGraphRelation }>();
      if (data.relation.id) onSelect({ kind: 'relation', id: data.relation.id });
    });
    canvas.on('blank:click', () => onSelect(null));
    canvas.on('edge:mouseenter', ({ edge }) => {
      const data = edge.getData<{ relation: BusinessObjectTypeGraphRelation; defaultLabel: string }>();
      edge.setLabelAt(0, { attrs: { label: { text: `${data.relation.forwardName || '未命名'} / ${data.relation.reverseName || '未命名'}` } } });
    });
    canvas.on('edge:mouseleave', ({ edge }) => {
      const data = edge.getData<{ defaultLabel: string }>();
      if (selectionRef.current?.kind !== 'relation' || selectionRef.current.id !== edge.id) {
        edge.setLabelAt(0, { attrs: { label: { text: data.defaultLabel } } });
      }
    });
    highlight(selectionRef.current);
    requestAnimationFrame(() => canvas.zoomToFit({ padding: 46, maxScale: 1 }));
    graphRef.current = canvas;
    return () => {
      canvas.getNodes().forEach((node) => positionCache.set(node.id, node.position()));
      graphRef.current = null;
      canvas.dispose();
    };
  }, [layoutKey, nodes, onSelect, outOfScopeIds, relations, validationStates]);

  useEffect(() => {
    const canvas = graphRef.current;
    if (!canvas) return;
    const selectedNodeId = selected?.kind === 'node' ? selected.id : undefined;
    const selectedRelationId = selected?.kind === 'relation' ? selected.id : undefined;
    const neighborIds = new Set<string>();
    const connectedRelationIds = new Set<string>();
    if (selectedNodeId) {
      neighborIds.add(selectedNodeId);
      relations.forEach((relation) => {
        if (!relation.id) return;
        if (relation.sourceObjectTypeId === selectedNodeId || relation.targetObjectTypeId === selectedNodeId) {
          connectedRelationIds.add(relation.id);
          neighborIds.add(relation.sourceObjectTypeId);
          if (relation.targetObjectTypeId) neighborIds.add(relation.targetObjectTypeId);
        }
      });
    }
    canvas.getNodes().forEach((cell) => {
      const active = !selected || selected.kind === 'relation' || neighborIds.has(cell.id);
      const node = cell.getData<BusinessObjectTypeGraphNode>();
      ['body', 'accent', 'title', 'code', 'source', 'counts', 'status', 'statusBackground', 'diagnostic'].forEach((selector) => cell.attr(`${selector}/opacity`, active ? 1 : 0.34));
      const outOfScope = outOfScopeIds.has(cell.id);
      const scopeOpacity = outOfScope ? (active ? 1 : 0.34) : 0;
      cell.attr('scopeBackground/opacity', scopeOpacity);
      cell.attr('scope/opacity', scopeOpacity);
      cell.attr('body/stroke', cell.id === selectedNodeId ? '#5068dc' : node.enabled ? '#cbd4ee' : '#d5d8e0');
      cell.attr('body/strokeWidth', cell.id === selectedNodeId ? 2 : 1.2);
    });
    canvas.getEdges().forEach((cell) => {
      const data = cell.getData<{ relation: BusinessObjectTypeGraphRelation; defaultLabel: string }>();
      const active = !selected || data.relation.id === selectedRelationId || connectedRelationIds.has(data.relation.id ?? '');
      cell.attr('line/opacity', active ? 1 : 0.16);
      cell.attr('line/stroke', data.relation.id === selectedRelationId ? '#5b4ed5' : '#7588ce');
      cell.attr('line/strokeWidth', data.relation.id === selectedRelationId ? 2.4 : 1.5);
      cell.setLabelAt(0, { attrs: { label: { text: data.relation.id === selectedRelationId ? `${data.relation.forwardName || '未命名'} / ${data.relation.reverseName || '未命名'}` : data.defaultLabel, opacity: active ? 1 : 0.22 } } });
    });
  }, [outOfScopeIds, relations, selected]);

  return <div ref={containerRef} className="ontology-graph-canvas" aria-label="业务对象类型本体关系画布" />;
});

BusinessOntologyGraphCanvas.displayName = 'BusinessOntologyGraphCanvas';
