export type McpServerStatus = 'DRAFT' | 'ENABLED' | 'DISABLED';
export type McpInvocationStatus = 'SUCCESS' | 'ERROR' | 'REJECTED';
export interface McpServer {
    id: string;
    code: string;
    name: string;
    directoryId: string | null;
    description: string | null;
    instructions: string | null;
    status: McpServerStatus;
    draftRevision: number;
    publishedVersion: number | null;
    activeReleaseId: string | null;
    lastPublishedAt: string | null;
    toolCount: number;
    unpublishedChanges: boolean;
    endpoint: string;
    createdAt: string;
    updatedAt: string;
}
export interface SaveMcpServerRequest {
    code?: string;
    name: string;
    directoryId?: string;
    description?: string;
    instructions?: string;
}
export type McpAccessTokenStatus = 'ENABLED' | 'DISABLED';
export interface McpAccessToken {
    id: string;
    name: string;
    description: string | null;
    status: McpAccessTokenStatus;
    expiresAt: string | null;
    hint: string;
    revision: number;
    rotatedAt: string;
    lastUsedAt: string | null;
    authorizedServerCount: number;
    createdAt: string;
    updatedAt: string;
}
export interface McpAuthorizedServer {
    id: string;
    code: string;
    name: string;
    status: McpServerStatus;
}
export interface McpAccessTokenDetail {
    token: McpAccessToken;
    authorizedServers: McpAuthorizedServer[];
}
export interface McpAccessTokenIssued {
    token: McpAccessToken;
    accessToken: string;
}
export interface SaveMcpAccessTokenRequest {
    name: string;
    description?: string;
    expiresAt?: string;
    serverIds?: string[];
}
export interface McpTool {
    id: string;
    serverId: string;
    code: string;
    name: string;
    description: string | null;
    inputSchemaJson: string;
    outputSchemaJson: string | null;
    script: string;
    examplesJson: string | null;
    enabled: boolean;
    sortOrder: number;
    revision: number;
    createdAt: string;
    updatedAt: string;
}
export interface SaveMcpToolRequest {
    expectedRevision?: number;
    code: string;
    name: string;
    description?: string;
    inputSchemaJson: string;
    outputSchemaJson?: string;
    script: string;
    examplesJson?: string;
    enabled: boolean;
    sortOrder: number;
}
export type McpToolSummary = Omit<McpTool, 'inputSchemaJson' | 'outputSchemaJson' | 'script' | 'examplesJson'>;
export interface McpDraftExecution {
    success: boolean;
    structuredContent: unknown;
    text: string | null;
    durationMillis: number;
    error: string | null;
    logs: string[];
}
export interface McpReleaseTool {
    id: string;
    sourceToolId: string;
    code: string;
    name: string;
    description: string | null;
    inputSchemaJson: string;
    outputSchemaJson: string | null;
    script: string;
    sortOrder: number;
}
export interface McpRelease {
    id: string;
    serverId: string;
    version: number;
    serverCode: string;
    serverName: string;
    instructions: string | null;
    digest: string;
    toolCount: number;
    draftRevision: number;
    publishedAt: string;
    tools: McpReleaseTool[];
}
export interface McpInvocation {
    id: string;
    serverId: string | null;
    releaseId: string | null;
    serverCode: string | null;
    releaseVersion: number | null;
    toolCode: string | null;
    invocationType: string;
    protocolVersion: string | null;
    requestId: string | null;
    startedAt: string;
    durationMillis: number;
    status: McpInvocationStatus;
    requestBytes: number;
    responseBytes: number;
    remoteAddress: string | null;
    userAgent: string | null;
    accessTokenId: string | null;
    accessTokenName: string | null;
    tokenRevision: number | null;
    errorSummary: string | null;
}
export interface McpInvocationOverview {
    since: string;
    total: number;
    succeeded: number;
    failed: number;
    successRate: number;
    averageDurationMillis: number;
}
export const mcpStatusLabels: Record<McpServerStatus, string> = { DRAFT: '草稿', ENABLED: '已启用', DISABLED: '已停用' };
