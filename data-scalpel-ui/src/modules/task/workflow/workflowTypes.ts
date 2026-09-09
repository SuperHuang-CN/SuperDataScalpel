import type { TaskRun } from '../model/task';

export interface WorkflowNode { id: string; taskId: string }
export interface WorkflowEdge { source: string; target: string }
export interface WorkflowDefinition {
  schemaVersion: number;
  maxParallelism: number;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  layout: Record<string, { x: number; y: number }>;
}
export interface WorkflowTaskDefinition {
  taskId: string;
  version: number | null;
  definition: WorkflowDefinition;
}
export interface WorkflowValidation {
  valid: boolean;
  problems: { code: string; message: string; nodeId: string | null; edgeIndex: number | null }[];
}
export interface WorkflowRun {
  run: TaskRun;
  definition: WorkflowDefinition;
  totalNodes: number;
  succeededNodes: number;
  hasActiveChildren: boolean;
  nodes: { id: string; taskId: string; taskName: string | null; status: string; childRun: TaskRun | null; message: string | null }[];
}
