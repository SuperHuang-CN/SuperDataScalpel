import type { DirectoryScope } from '../../directory';
import type {
  DataSourceAssistantCreateDraft,
  DataSourceAssistantUpdateDraft,
} from '../../datasource';
import type { CanvasDefinition, TaskAssistantCreateDraft } from '../../task';

export type LlmProtocol = 'OPENAI_COMPATIBLE';
export type LlmModelTestStatus = 'UNTESTED' | 'AVAILABLE' | 'UNAVAILABLE' | 'INCOMPATIBLE';

export interface LlmModelConfiguration {
  id: string;
  name: string;
  protocol: LlmProtocol;
  baseUrl: string;
  modelName: string;
  apiKeyConfigured: boolean;
  extraRequestParameters: string;
  enabled: boolean;
  defaultModel: boolean;
  testStatus: LlmModelTestStatus;
  lastTestedAt: string | null;
  testMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SaveLlmModelRequest {
  name: string;
  protocol: LlmProtocol;
  baseUrl: string;
  modelName: string;
  apiKey?: string;
  clearApiKey?: boolean;
  extraRequestParameters?: string;
}

export interface AvailableLlmModel {
  id: string;
  name: string;
  modelName: string;
  defaultModel: boolean;
}

export type AssistantSessionStatus = 'ACTIVE' | 'ARCHIVED';

export interface AssistantSession {
  id: string;
  title: string;
  selectedModelId: string;
  status: AssistantSessionStatus;
  lastMessageAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface AssistantMessage {
  id: string;
  sessionId: string;
  runId: string | null;
  role: 'USER' | 'ASSISTANT';
  content: string;
  createdAt: string;
}

export type AssistantChangeSetStatus = 'PENDING' | 'APPLIED' | 'REJECTED' | 'SUPERSEDED' | 'STALE' | 'FAILED';

export interface DirectoryCurrentState {
  parentId: string | null;
  name: string;
  sortOrder: number;
  description: string | null;
  path: string;
  directResourceCount: number;
  resourceCount: number;
}

export interface DirectoryCreateOperation {
  ref: string;
  parentId: string | null;
  parentRef: string | null;
  name: string;
  sortOrder: number;
  description: string | null;
  targetPath: string;
}

export interface DirectoryUpdateOperation {
  id: string;
  parentId: string | null;
  parentRef: string | null;
  name: string;
  sortOrder: number;
  description: string | null;
  expectedUpdatedAt: string;
  current: DirectoryCurrentState;
  targetPath: string;
}

export interface DirectoryDeleteOperation {
  id: string;
  expectedUpdatedAt: string;
  name: string;
  path: string;
  directResourceCount: number;
  resourceCount: number;
  depth: number;
}

export interface DirectoryChangePlan {
  scope: DirectoryScope;
  summary: string;
  creates: DirectoryCreateOperation[];
  updates: DirectoryUpdateOperation[];
  deletes: DirectoryDeleteOperation[];
}

export interface DirectoryExecutionResult {
  scope: DirectoryScope;
  created: Array<{ ref: string; id: string; name: string }>;
  updated: Array<{ id: string; name: string }>;
  deleted: Array<{ id: string; name: string }>;
}

interface AssistantChangeSetBase {
  id: string;
  sessionId: string;
  runId: string;
  status: AssistantChangeSetStatus;
  summary: string;
  approvedBy: string | null;
  approvedAt: string | null;
  executedAt: string | null;
  failureSummary: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TaskCanvasPlanSummary {
  target: { taskId: string | null; newTask: TaskAssistantCreateDraft | null };
  inputs: Array<{
    ref: string;
    name: string;
    type: 'MODEL' | 'JDBC_TABLE' | 'FILE_DATASET_TABLE';
  }>;
  steps: Array<{
    ref: string;
    name: string;
    type: 'FILTER' | 'SELECT_COLUMNS' | 'RENAME' | 'TYPE_CAST' | 'NULL_HANDLING' | 'DEDUPLICATE' | 'JOIN' | 'AGGREGATE';
    inputRefs: string[];
  }>;
  output: {
    name: string;
    inputRef: string;
    type: 'MODEL_OUTPUT' | 'JDBC_OUTPUT';
  };
  summary: string;
  assumptions: string[];
  needsUserInput: string[];
}

export interface TaskCanvasProposal {
  plan: TaskCanvasPlanSummary;
  definition: CanvasDefinition;
  resources: Array<{
    kind: string;
    resourceId: string;
    subResourceId: string | null;
    code: string;
    name: string;
    fingerprint: string;
  }>;
  existingTask: {
    taskId: string;
    taskName: string;
    definitionVersion: number;
    definitionUpdatedAt: string | null;
    taskUpdatedAt: string;
    configured: boolean;
  } | null;
  newTaskDraft: TaskAssistantCreateDraft | null;
  summary: string;
  assumptions: string[];
  needsUserInput: string[];
  nodeCount: number;
  edgeCount: number;
}

export interface TaskCanvasApplicationResult {
  taskId: string;
  mode: 'CLIENT_DRAFT';
  definition: CanvasDefinition;
}

export type AssistantChangeSet = AssistantChangeSetBase & (
  | {
    changeType: 'DIRECTORY';
    directoryPlan: DirectoryChangePlan;
    directoryResult: DirectoryExecutionResult | null;
    taskCanvasProposal: null;
    taskCanvasResult: null;
  }
  | {
    changeType: 'TASK_CANVAS';
    directoryPlan: null;
    directoryResult: null;
    taskCanvasProposal: TaskCanvasProposal;
    taskCanvasResult: TaskCanvasApplicationResult | null;
  }
);

export interface AssistantSessionDetail {
  session: AssistantSession;
  latestChangeSet: AssistantChangeSet | null;
  latestRun: AssistantRun | null;
}

export interface AssistantToolInvocation {
  id: string;
  toolName: string;
  risk: 'READ_ONLY' | 'CLIENT_ACTION' | 'WRITE_DRAFT';
  status: 'SUCCEEDED' | 'FAILED';
  argumentsJson: string;
  resultJson: string;
  createdAt: string;
}

export interface AssistantRun {
  id: string;
  modelName: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  startedAt: string;
  completedAt: string | null;
  failureSummary: string | null;
  toolInvocations: AssistantToolInvocation[];
}

export type AssistantClientAction =
  | { type: 'NAVIGATE_PAGE'; pageKey: string; collapsed: null; scope: null; dataSourceId: null; dataSourceName: null; dataSourceDraft: null; changeSetId: null; taskId: null; taskDraft: null }
  | { type: 'SET_APP_SIDEBAR_COLLAPSED'; pageKey: null; collapsed: boolean; scope: null; dataSourceId: null; dataSourceName: null; dataSourceDraft: null; changeSetId: null; taskId: null; taskDraft: null }
  | { type: 'DOWNLOAD_DIRECTORY_EXPORT'; pageKey: null; collapsed: null; scope: DirectoryScope; dataSourceId: null; dataSourceName: null; dataSourceDraft: null; changeSetId: null; taskId: null; taskDraft: null }
  | { type: 'OPEN_DATA_SOURCE_CREATE'; pageKey: null; collapsed: null; scope: null; dataSourceId: null; dataSourceName: null; dataSourceDraft: DataSourceAssistantCreateDraft; changeSetId: null; taskId: null; taskDraft: null }
  | { type: 'OPEN_DATA_SOURCE_EDIT'; pageKey: null; collapsed: null; scope: null; dataSourceId: string; dataSourceName: string; dataSourceDraft: DataSourceAssistantUpdateDraft; changeSetId: null; taskId: null; taskDraft: null }
  | { type: 'CONFIRM_DATA_SOURCE_TEST'; pageKey: null; collapsed: null; scope: null; dataSourceId: string; dataSourceName: string; dataSourceDraft: null; changeSetId: null; taskId: null; taskDraft: null }
  | { type: 'OPEN_TASK_CANVAS_PROPOSAL'; pageKey: null; collapsed: null; scope: null; dataSourceId: null; dataSourceName: null; dataSourceDraft: null; changeSetId: string; taskId: string | null; taskDraft: TaskAssistantCreateDraft | null };

export interface AssistantTurn {
  runId: string;
  assistantMessage: AssistantMessage;
  clientActions: AssistantClientAction[];
  pendingChangeSet: AssistantChangeSet | null;
}

export interface SendAssistantMessageRequest {
  content: string;
  pageKey?: string;
  sidebarCollapsed: boolean;
  currentTaskId?: string;
}
