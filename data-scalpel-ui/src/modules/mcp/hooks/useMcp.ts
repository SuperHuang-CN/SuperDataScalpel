import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import * as api from '../api/mcpApi';
import type { SaveMcpAccessTokenRequest, SaveMcpServerRequest, SaveMcpToolRequest } from '../model/mcp';
const KEY = 'mcp-servers';
const invalidate = async (client: ReturnType<typeof useQueryClient>, id?: string) => { await Promise.all([client.invalidateQueries({ queryKey: [KEY, 'list'] }), id ? client.invalidateQueries({ queryKey: [KEY, id] }) : Promise.resolve(), invalidateDirectoryTree(client, 'MCP_SERVER')]); };
export const useMcpServers = (request: SearchRequest) => useQuery({ queryKey: [KEY, 'list', request], queryFn: () => api.fetchMcpServers(request) });
export const useMcpServer = (id?: string) => useQuery({ queryKey: [KEY, id], queryFn: () => api.fetchMcpServer(id as string), enabled: Boolean(id) });
export const useCreateMcpServer = () => { const client = useQueryClient(); return useMutation({ mutationFn: api.createMcpServer, onSuccess: () => invalidate(client) }); };
export const useUpdateMcpServer = () => { const client = useQueryClient(); return useMutation({ mutationFn: ({ id, request }: {
        id: string;
        request: SaveMcpServerRequest;
    }) => api.updateMcpServer(id, request), onSuccess: (_, v) => invalidate(client, v.id) }); };
export const useMcpServerAction = () => { const client = useQueryClient(); return useMutation({ mutationFn: ({ id, action }: {
        id: string;
        action: 'publish' | 'enable' | 'disable';
    }) => api.serverAction(id, action), onSuccess: (_, v) => invalidate(client, v.id) }); };
export const useDeleteMcpServer = () => { const client = useQueryClient(); return useMutation({ mutationFn: api.deleteMcpServer, onSuccess: () => invalidate(client) }); };
export const useMcpTools = (serverId?: string) => useQuery({ queryKey: [KEY, serverId, 'tools'], queryFn: () => api.fetchMcpTools(serverId as string), enabled: Boolean(serverId) });
export const useMcpTool = (serverId?: string, toolId?: string) => useQuery({ queryKey: [KEY, serverId, 'tools', toolId], queryFn: () => api.fetchMcpTool(serverId as string, toolId as string), enabled: Boolean(serverId && toolId && toolId !== 'new') });
export const useSaveMcpTool = (serverId?: string, toolId?: string) => { const client = useQueryClient(); return useMutation({ mutationFn: (request: SaveMcpToolRequest) => toolId && toolId !== 'new' ? api.updateMcpTool(serverId as string, toolId, request) : api.createMcpTool(serverId as string, request), onSuccess: () => Promise.all([client.invalidateQueries({ queryKey: [KEY, serverId] }), client.invalidateQueries({ queryKey: [KEY, 'list'] })]) }); };
export const useDeleteMcpTool = (serverId?: string) => { const client = useQueryClient(); return useMutation({ mutationFn: (toolId: string) => api.deleteMcpTool(serverId as string, toolId), onSuccess: () => invalidate(client, serverId) }); };
export const useExecuteMcpDraft = (serverId?: string) => useMutation({ mutationFn: (request: Parameters<typeof api.executeMcpDraft>[1]) => api.executeMcpDraft(serverId as string, request) });
const TOKEN_KEY = 'mcp-access-tokens';
const invalidateTokens = async (client: ReturnType<typeof useQueryClient>, id?: string) => Promise.all([
    client.invalidateQueries({ queryKey: [TOKEN_KEY, 'list'] }),
    id ? client.invalidateQueries({ queryKey: [TOKEN_KEY, id] }) : Promise.resolve(),
    client.invalidateQueries({ queryKey: [KEY, 'access-tokens'] }),
]);
export const useMcpAccessTokens = (request: SearchRequest, enabled = true) => useQuery({ queryKey: [TOKEN_KEY, 'list', request], queryFn: () => api.fetchMcpAccessTokens(request), enabled });
export const useMcpAccessTokenServerCandidates = (request: SearchRequest) => useQuery({ queryKey: [TOKEN_KEY, 'server-candidates', request], queryFn: () => api.fetchMcpAccessTokenServerCandidates(request) });
export const useMcpAccessToken = (id?: string) => useQuery({ queryKey: [TOKEN_KEY, id], queryFn: () => api.fetchMcpAccessToken(id as string), enabled: Boolean(id) });
export const useCreateMcpAccessToken = () => { const client = useQueryClient(); return useMutation({ mutationFn: api.createMcpAccessToken, onSuccess: () => invalidateTokens(client) }); };
export const useUpdateMcpAccessToken = () => { const client = useQueryClient(); return useMutation({ mutationFn: ({ id, request }: { id: string; request: Omit<SaveMcpAccessTokenRequest, 'serverIds'> }) => api.updateMcpAccessToken(id, request), onSuccess: (_, value) => invalidateTokens(client, value.id) }); };
export const useUpdateMcpAccessTokenServers = () => { const client = useQueryClient(); return useMutation({ mutationFn: ({ id, serverIds }: { id: string; serverIds: string[] }) => api.updateMcpAccessTokenServers(id, serverIds), onSuccess: (_, value) => invalidateTokens(client, value.id) }); };
export const useMcpAccessTokenAction = () => { const client = useQueryClient(); return useMutation({ mutationFn: ({ id, action }: { id: string; action: 'enable' | 'disable' }) => api.mcpAccessTokenAction(id, action), onSuccess: (_, value) => invalidateTokens(client, value.id) }); };
export const useRotateMcpAccessToken = () => { const client = useQueryClient(); return useMutation({ mutationFn: api.rotateMcpAccessToken, onSuccess: (_, id) => invalidateTokens(client, id) }); };
export const useDeleteMcpAccessToken = () => { const client = useQueryClient(); return useMutation({ mutationFn: api.deleteMcpAccessToken, onSuccess: () => invalidateTokens(client) }); };
export const useServerMcpAccessTokens = (serverId: string | undefined, request: SearchRequest, enabled = true) => useQuery({ queryKey: [KEY, 'access-tokens', serverId, request], queryFn: () => api.fetchServerMcpAccessTokens(serverId as string, request), enabled: enabled && Boolean(serverId) });
export const useServerMcpAccessTokenGrant = () => { const client = useQueryClient(); return useMutation({ mutationFn: ({ serverId, accessTokenId, action }: { serverId: string; accessTokenId: string; action: 'grant' | 'revoke' }) => action === 'grant' ? api.grantServerMcpAccessToken(serverId, accessTokenId) : api.revokeServerMcpAccessToken(serverId, accessTokenId), onSuccess: () => invalidateTokens(client) }); };
export const useMcpReleases = (id?: string) => useQuery({ queryKey: [KEY, id, 'releases'], queryFn: () => api.fetchMcpReleases(id as string), enabled: Boolean(id) });
export const useMcpInvocations = (request: SearchRequest) => useQuery({ queryKey: ['mcp-invocations', request], queryFn: () => api.fetchMcpInvocations(request) });
export const useMcpInvocationOverview = () => useQuery({ queryKey: ['mcp-invocations', 'overview'], queryFn: api.fetchMcpInvocationOverview });
