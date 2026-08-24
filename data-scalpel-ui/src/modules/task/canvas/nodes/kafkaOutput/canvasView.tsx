import { CanvasNodeType, type CanvasTableSchema, type KafkaOutputWrite } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeOutputTarget, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataSourceName, resourceListNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const kafkaTargetSchema = (write: KafkaOutputWrite): CanvasTableSchema => ({
  name: write.topic || 'Kafka Value Schema',
  origin: null,
  columns: write.valueSchema.columns.map((column) => ({
    ...column,
    defaultValue: null,
    autoIncrement: false,
    generated: false,
    geometry: null,
  })),
  datasetKind: 'UNBOUNDED',
  eventTimeColumn: null,
  watermarkDelay: null,
});

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.KafkaOutput>) => {
  const { dataSourceId } = data.configuration;
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return <NodeEmpty>请选择来源表并添加 Kafka Topic</NodeEmpty>;
  return <NodeContent variant="output">
    <NodeTitleLine primary={dataSourceId ? metadataSourceName(data) : '待选择 Kafka 数据源'} secondary={`${writes.length} 个 Topic`} accent />
    <NodePreviewList
      items={writes.map((write) => ({
        key: write.writeId,
        label: write.sourceTableName || '来源流表',
        value: write.keyColumnName ? `KEY ${write.keyColumnName}` : 'KEY 待配置',
        meta: <NodeOutputTarget
          target={write.topic || 'Topic'}
          sourceTable={kafkaTargetSchema(write)}
          mappedCount={write.columnMappings.length}
          mappedColumnNames={write.columnMappings.map((mapping) => mapping.targetColumnName)}
        />,
      }))}
      total={writes.length}
      limit={3}
      moreLabel={(remaining) => `另 ${remaining} 个写入`}
    />
    <NodeBadges><NodeBadge tone="strong">{writes.length} 个子查询</NodeBadge><NodeBadge>Kafka Streaming</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const kafkaOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.KafkaOutput> = {
  resolveSize: (configuration) => resourceListNodeSize({ width: 420, count: configuration.writes?.length ?? 0, emptyHeight: 112 }),
  Body: body,
};
