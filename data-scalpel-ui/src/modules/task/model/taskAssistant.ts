import type { CanvasDefinition } from '../canvas/canvasTypes';

export interface TaskAssistantCreateDraft {
  name: string;
  directoryId: string | null;
  description: string | null;
}

export interface TaskAssistantLocationState {
  assistantTaskCanvasAction?: {
    kind: 'CREATE_TASK';
    changeSetId: string;
    draft: TaskAssistantCreateDraft;
  };
}

export interface TaskCanvasProposalLocationState {
  assistantTaskCanvasChangeSetId?: string;
}

export interface TaskCanvasProposalChangeSet {
  id: string;
  status: 'PENDING' | 'APPLIED' | 'REJECTED' | 'SUPERSEDED' | 'STALE' | 'FAILED';
  summary: string;
  failureSummary: string | null;
  taskCanvasProposal: {
    plan: {
      inputs: Array<{ ref: string; name: string; type: 'MODEL' | 'JDBC_TABLE' | 'FILE_DATASET_TABLE' }>;
      steps: Array<{ ref: string; name: string; type: string; inputRefs: string[] }>;
      output: { name: string; inputRef: string; type: 'MODEL_OUTPUT' | 'JDBC_OUTPUT' };
    };
    existingTask: { taskId: string; taskName: string; configured: boolean } | null;
    newTaskDraft: TaskAssistantCreateDraft | null;
    summary: string;
    assumptions: string[];
    needsUserInput: string[];
    nodeCount: number;
    edgeCount: number;
  };
  taskCanvasResult: {
    taskId: string;
    mode: 'CLIENT_DRAFT';
    definition: CanvasDefinition;
  } | null;
}
