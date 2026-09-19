import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeOutputTarget, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { inputTable, resourceListNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.ModelOutput>) => {
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return <NodeEmpty>请选择来源表并添加目标模型</NodeEmpty>;
  const summary = data.summary?.kind === 'MODEL' ? data.summary : null;
  const modeSummary = [...new Set(writes.map((write) => write.writeMode ?? '待设置'))].join(' · ');
  return <NodeContent variant="output">
    <NodeTitleLine
      primary={writes.length === 1 ? summary?.modelName ?? '等待目标模型元数据' : `${writes.length} 个目标模型`}
      secondary={writes.length === 1 && summary ? `${summary.modelCode} · v${summary.modelSchemaVersion}` : '模型写入组'}
      accent
    />
    <NodePreviewList
      items={writes.map((write, index) => ({
        key: write.writeId,
        label: write.sourceTableName || '来源表',
        value: write.writeMode ?? '待设置',
        meta: <NodeOutputTarget
          target={(index === 0 ? summary?.modelCode : null) ?? write.targetModelId.slice(0, 8) ?? '目标模型'}
          sourceTable={inputTable(data, write.sourceTableName)}
          mappedCount={write.columnMappings.length}
          mappedColumnNames={write.columnMappings.map((mapping) => mapping.sourceColumnName)}
        />,
      }))}
      total={writes.length}
      limit={3}
      moreLabel={(remaining) => `另 ${remaining} 个写入`}
    />
    <NodeBadges><NodeBadge tone="strong">{writes.length} 个写入</NodeBadge><NodeBadge>{modeSummary}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const modelOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.ModelOutput> = {
  resolveSize: (configuration) => resourceListNodeSize({ width: 400, count: configuration.writes?.length ?? 0, emptyHeight: 112 }),
  Body: body,
};
