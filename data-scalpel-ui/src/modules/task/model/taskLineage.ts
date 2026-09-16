import type {
  LineageCoverage,
  LineageGraph,
  LineageFieldGraph,
  LineageGraphNodeKind,
  LineageOutputFieldEffect,
  LineageWriteMode,
} from '../../model';

export interface TaskLineageOutputField {
  fieldKey: string;
  modelFieldId: string | null;
  code: string;
  name: string;
  sortOrder: number;
  outputEffect: LineageOutputFieldEffect | null;
}

export interface TaskLineageFlow {
  flowKey: string;
  outputLabel: string;
  outputKind: Extract<LineageGraphNodeKind, 'MODEL' | 'JDBC_TABLE' | 'EXTERNAL_RESOURCE'>;
  writeMode: LineageWriteMode;
  coverage: LineageCoverage;
  outputFields: TaskLineageOutputField[];
}

export interface TaskLineageGraph {
  taskId: string;
  definitionVersion: number | null;
  coverage: LineageCoverage | null;
  flows: TaskLineageFlow[];
  selectedFlowKey: string | null;
  selectedOutputFieldKey: string | null;
  graph: LineageGraph;
}

export interface TaskFieldLineageGraph {
  taskId: string;
  definitionVersion: number | null;
  coverage: LineageCoverage | null;
  flows: TaskLineageFlow[];
  selectedFlowKey: string | null;
  fieldGraph: LineageFieldGraph;
}
