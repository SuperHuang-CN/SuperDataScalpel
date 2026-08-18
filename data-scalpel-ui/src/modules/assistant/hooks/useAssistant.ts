import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import {
  approveAssistantChangeSet,
  archiveAssistantSession,
  commandLlmModel,
  createAssistantSession,
  createLlmModel,
  deleteLlmModel,
  fetchAssistantMessages,
  fetchAssistantSession,
  fetchAssistantSessions,
  fetchAvailableAssistantModels,
  fetchLlmModels,
  rejectAssistantChangeSet,
  selectAssistantModel,
  sendAssistantMessage,
  updateLlmModel,
} from '../api/assistantApi';
import type { SaveLlmModelRequest, SendAssistantMessageRequest } from '../model/assistant';

const llmModelsKey = ['assistant', 'llm-models'] as const;
const availableModelsKey = ['assistant', 'available-models'] as const;
const sessionsKey = ['assistant', 'sessions'] as const;
const sessionKey = (id: string) => ['assistant', 'session', id] as const;
const messagesKey = (id: string) => ['assistant', 'messages', id] as const;

export const useLlmModels = (request: SearchRequest) => useQuery({
  queryKey: [...llmModelsKey, request],
  queryFn: () => fetchLlmModels(request),
});

const useInvalidateLlmModels = () => {
  const queryClient = useQueryClient();
  return () => Promise.all([
    queryClient.invalidateQueries({ queryKey: llmModelsKey }),
    queryClient.invalidateQueries({ queryKey: availableModelsKey }),
  ]);
};

export const useCreateLlmModel = () => {
  const invalidate = useInvalidateLlmModels();
  return useMutation({ mutationFn: createLlmModel, onSuccess: invalidate });
};

export const useUpdateLlmModel = () => {
  const invalidate = useInvalidateLlmModels();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: SaveLlmModelRequest }) => updateLlmModel(id, request),
    onSuccess: invalidate,
  });
};

export const useLlmModelCommand = () => {
  const invalidate = useInvalidateLlmModels();
  return useMutation({
    mutationFn: ({ id, command }: { id: string; command: 'test' | 'enable' | 'disable' | 'set-default' }) => (
      commandLlmModel(id, command)
    ),
    onSuccess: invalidate,
  });
};

export const useDeleteLlmModel = () => {
  const invalidate = useInvalidateLlmModels();
  return useMutation({ mutationFn: deleteLlmModel, onSuccess: invalidate });
};

export const useAvailableAssistantModels = (enabled: boolean) => useQuery({
  queryKey: availableModelsKey,
  queryFn: fetchAvailableAssistantModels,
  enabled,
});

export const useAssistantSessions = (enabled: boolean) => useQuery({
  queryKey: sessionsKey,
  queryFn: () => fetchAssistantSessions({ page: 0, size: 50, sort: '-lastMessageAt' }),
  enabled,
});

export const useAssistantSession = (id: string | null, enabled: boolean) => useQuery({
  queryKey: sessionKey(id ?? 'none'),
  queryFn: () => fetchAssistantSession(id as string),
  enabled: enabled && Boolean(id),
});

export const useAssistantMessages = (id: string | null, enabled: boolean) => useQuery({
  queryKey: messagesKey(id ?? 'none'),
  queryFn: () => fetchAssistantMessages(id as string),
  enabled: enabled && Boolean(id),
});

const useInvalidateSession = () => {
  const queryClient = useQueryClient();
  return (id?: string) => Promise.all([
    queryClient.invalidateQueries({ queryKey: sessionsKey }),
    ...(id ? [
      queryClient.invalidateQueries({ queryKey: sessionKey(id) }),
      queryClient.invalidateQueries({ queryKey: messagesKey(id) }),
    ] : []),
  ]);
};

export const useCreateAssistantSession = () => {
  const invalidate = useInvalidateSession();
  return useMutation({ mutationFn: createAssistantSession, onSuccess: (session) => invalidate(session.id) });
};

export const useSelectAssistantModel = () => {
  const invalidate = useInvalidateSession();
  return useMutation({
    mutationFn: ({ id, modelId }: { id: string; modelId: string }) => selectAssistantModel(id, modelId),
    onSuccess: (session) => invalidate(session.id),
  });
};

export const useArchiveAssistantSession = () => {
  const invalidate = useInvalidateSession();
  return useMutation({ mutationFn: archiveAssistantSession, onSuccess: (session) => invalidate(session.id) });
};

export const useSendAssistantMessage = () => {
  const invalidate = useInvalidateSession();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: SendAssistantMessageRequest }) => (
      sendAssistantMessage(id, request)
    ),
    onSuccess: (_turn, variables) => invalidate(variables.id),
  });
};

export const useApproveAssistantChangeSet = (sessionId: string | null) => {
  const invalidate = useInvalidateSession();
  return useMutation({
    mutationFn: approveAssistantChangeSet,
    onSuccess: () => invalidate(sessionId ?? undefined),
  });
};

export const useRejectAssistantChangeSet = (sessionId: string | null) => {
  const invalidate = useInvalidateSession();
  return useMutation({
    mutationFn: rejectAssistantChangeSet,
    onSuccess: () => invalidate(sessionId ?? undefined),
  });
};
