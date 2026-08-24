import { CanvasNodeType, type FileOutputWrite } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeOutputTarget, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { inputTable, metadataSourceName, resourceListNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const targetDisplayName = (write: FileOutputWrite): string => {
  const baseName = 'baseName' in write.formatOptions ? write.formatOptions.baseName : null;
  return baseName ? `${write.targetPath}/${baseName}` : write.targetPath;
};

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.FileOutput>) => {
  const { dataSourceId } = data.configuration;
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return <NodeEmpty>请选择来源表并添加文件输出目标</NodeEmpty>;
  const formats = [...new Set(writes.map((write) => write.formatOptions.type))].join(' · ');
  return <NodeContent variant="output">
    <NodeTitleLine primary={dataSourceId ? metadataSourceName(data) : '待选择文件数据源'} secondary={`${writes.length} 个文件目标`} accent />
    <NodePreviewList
      items={writes.map((write) => ({
        key: write.writeId,
        label: write.sourceTableName || '来源表',
        value: `${write.formatOptions.type} · ${write.conflictPolicy}`,
        meta: <NodeOutputTarget
          target={targetDisplayName(write) || '目标目录'}
          sourceTable={inputTable(data, write.sourceTableName)}
        />,
      }))}
      total={writes.length}
      limit={3}
      moreLabel={(remaining) => `另 ${remaining} 个写入`}
    />
    <NodeBadges><NodeBadge tone="strong">{writes.length} 个写入</NodeBadge><NodeBadge>{formats}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const fileOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.FileOutput> = {
  resolveSize: (configuration) => resourceListNodeSize({ width: 420, count: configuration.writes?.length ?? 0, emptyHeight: 112 }),
  Body: body,
};
