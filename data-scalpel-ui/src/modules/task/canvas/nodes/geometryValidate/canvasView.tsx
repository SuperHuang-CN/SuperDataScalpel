import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeSplit } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.GeometryValidate>) => {
  const { sourceTableName, outputTableName, geometryColumnName, validColumnName, reasonColumnName } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择需要诊断的 Geometry 字段</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={`${sourceTableName}.${geometryColumnName || '?'}`} operation="VALIDATE" target={outputTableName} />
    <NodeSplit leftLabel="合法性字段" left={validColumnName || '待设置'} rightLabel="原因字段" right={reasonColumnName || '不输出'} />
    <NodeBadges><NodeBadge tone="spatial">ST_IsValid</NodeBadge>{reasonColumnName && <NodeBadge>ST_IsValidReason</NodeBadge>}<NodeBadge tone="info">保留原 Geometry</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const geometryValidateCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.GeometryValidate> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 188, configured: Boolean(configuration.sourceTableName) }),
  Body: body,
};
