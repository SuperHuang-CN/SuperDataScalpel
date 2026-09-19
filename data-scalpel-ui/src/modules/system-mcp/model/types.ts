export interface Page<T> { content: T[]; totalElements: number; totalPages: number; page: number; size: number }
export interface Configuration { enabled: boolean; endpoint: string; catalogStatus: string; catalogMessage?: string; totalApis: number; availableApis: number; enabledApis: number }
export interface Api { id: string; operationId: string; method: string; path: string; module: string; summary: string; description: string; effect: string; status: string; unavailableReason?: string; enabled: boolean; fingerprint?: string; contract?: unknown }
export interface ApiModule { module: string; totalApis: number; availableApis: number; enabledApis: number }
export interface Token { managed: boolean; id: string; name: string; userId: string; username: string; enabled: boolean; revision: number; expiresAt?: string; lastUsedAt?: string; createdAt: string }
export interface Issued { token: Token; secret: string }
export interface Audit { id: string; eventType: string; username: string; tokenId?: string; operationId?: string; toolName?: string; status: string; errorCode?: string; durationMs: number; changesJson?: string; createdAt: string }
export interface UserOption { id: string; username: string; displayName: string }
