import {
  CanvasNodeType,
  type GeometryDeriveKind,
} from '../../canvasTypes';
import {
  NodeBadge,
  NodeBadges,
  NodeContent,
  NodeEmpty,
  NodeFlow,
  NodePreviewList,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const kindLabels: Record<GeometryDeriveKind, string> = {
  CENTROID: '中心点',
  POINT_ON_SURFACE: '面内点',
  ENVELOPE: '包络',
  CONVEX_HULL: '凸包',
  BOUNDARY: '边界',
};

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.GeometryDerive>) => {
  const { sourceTableName, outputTableName, derivations } = data.configuration;
  if (!sourceTableName) {
    return <NodeEmpty>请选择来源表并配置 Geometry 派生字段</NodeEmpty>;
  }
  return (
    <NodeContent variant="spatial">
      <NodeFlow
        source={sourceTableName}
        operation="DERIVE"
        target={outputTableName || '待设置'}
      />
      <NodePreviewList
        items={derivations.slice(0, 2).map((item) => ({
          key: item.derivationId,
          label: `${item.kind ? kindLabels[item.kind] : '待选函数'} · ${item.sourceColumnName || 'Geometry 字段'}`,
          value: '→',
          meta: item.outputColumnName || '输出字段',
        }))}
        total={derivations.length}
        empty="尚未配置派生字段"
      />
      <NodeBadges>
        <NodeBadge tone="spatial">保留来源字段</NodeBadge>
        <NodeBadge>{derivations.length} 个派生字段</NodeBadge>
      </NodeBadges>
    </NodeContent>
  );
};

export const geometryDeriveCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.GeometryDerive
> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 332,
    maxHeight: 196,
    configured: Boolean(configuration.sourceTableName),
    listCount: configuration.derivations.length,
  }),
  Body: body,
};
