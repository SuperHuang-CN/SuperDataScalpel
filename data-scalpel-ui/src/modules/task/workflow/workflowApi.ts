import { requestJson } from '../../../shared/api/http';
import type { WorkflowDefinition, WorkflowRun, WorkflowTaskDefinition, WorkflowValidation } from './workflowTypes';

export const workflowDefinitionKey = (id: string) => ['task-workflow-definition', id];
export const fetchWorkflowDefinition = (id: string) => requestJson<WorkflowTaskDefinition>(`/v1/tasks/${id}/workflow-definition`);
export const saveWorkflowDefinition = (id: string, definition: WorkflowDefinition) => requestJson<WorkflowTaskDefinition>(
  `/v1/tasks/${id}/actions/update-workflow-definition`, { method: 'POST', body: JSON.stringify({ definition }) },
);
export const validateWorkflowDefinition = (id: string) => requestJson<WorkflowValidation>(
  `/v1/tasks/${id}/actions/validate-workflow-definition`, { method: 'POST' },
);
export const fetchWorkflowRun = (id: string) => requestJson<WorkflowRun>(`/v1/task-runs/${id}/workflow`);
