import type { DirectoryScope } from '../../directory';
import type {
  DataSourceAssistantCreateDraft,
  DataSourceAssistantUpdateDraft,
} from '../../datasource';

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

export interface AssistantChangeSet {
  id: string;
  sessionId: string;
  runId: string;
  changeType: 'DIRECTORY';
  status: AssistantChangeSetStatus;
  summary: string;
  directoryPlan: DirectoryChangePlan;
  directoryResult: DirectoryExecutionResult | null;
  approvedBy: string | null;
  approvedAt: string | null;
  executedAt: string | null;
  failureSummary: string | null;
  createdAt: string;
  updatedAt: string;
}

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
  | { type: 'NAVIGATE_PAGE'; pageKey: string; collapsed: null; scope: null; dataSourceId: null; dataSourceName: null; dataSourceDraft: null }
  | { type: 'SET_APP_SIDEBAR_COLLAPSED'; pageKey: null; collapsed: boolean; scope: null; dataSourceId: null; dataSourceName: null; dataSourceDraft: null }
  | { type: 'DOWNLOAD_DIRECTORY_EXPORT'; pageKey: null; collapsed: null; scope: DirectoryScope; dataSourceId: null; dataSourceName: null; dataSourceDraft: null }
  | { type: 'OPEN_DATA_SOURCE_CREATE'; pageKey: null; collapsed: null; scope: null; dataSourceId: null; dataSourceName: null; dataSourceDraft: DataSourceAssistantCreateDraft }
  | { type: 'OPEN_DATA_SOURCE_EDIT'; pageKey: null; collapsed: null; scope: null; dataSourceId: string; dataSourceName: string; dataSourceDraft: DataSourceAssistantUpdateDraft }
  | { type: 'CONFIRM_DATA_SOURCE_TEST'; pageKey: null; collapsed: null; scope: null; dataSourceId: string; dataSourceName: string; dataSourceDraft: null };

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
}
