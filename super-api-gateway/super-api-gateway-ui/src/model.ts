export type AccessMode = 'PUBLIC' | 'SUBSCRIPTION_REQUIRED';
export type GatewayHttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Service {
  id: string;
  code: string;
  name: string;
  upstreamUri: string;
  accessMode: AccessMode;
  connectTimeoutMs: number;
  responseTimeoutMs: number;
  enabled: boolean;
  description?: string;
  source: string;
  externalId?: string;
  revision: number;
  routeCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Route {
  id: string;
  serviceId: string;
  code: string;
  name: string;
  pathPattern: string;
  methods: GatewayHttpMethod[];
  order: number;
  stripPrefixSegments: number;
  enabled: boolean;
  source: string;
  externalId?: string;
  revision: number;
}

export interface Consumer {
  id: string;
  code: string;
  name: string;
  enabled: boolean;
  description?: string;
  source: string;
  externalId?: string;
  revision: number;
}

export interface ApiKey {
  id: string;
  consumerId: string;
  name: string;
  prefix: string;
  lastFour: string;
  status: 'ACTIVE' | 'REVOKED';
  rotatedAt: string;
  source: string;
  externalId?: string;
  secret?: string;
}

export interface Subscription {
  id: string;
  consumerId: string;
  serviceId: string;
  status: 'ACTIVE' | 'REVOKED';
  source: string;
  externalId?: string;
}

export interface GatewayInstance {
  id: string;
  state: string;
  loadedRevision: number;
  startedAt: string;
  lastSeenAt: string;
  lastError?: string;
  applicationVersion: string;
}

export interface RuntimeSummary {
  targetRevision: number;
  services: number;
  routes: number;
  consumers: number;
  apiKeys: number;
  subscriptions: number;
  instances: GatewayInstance[];
}
