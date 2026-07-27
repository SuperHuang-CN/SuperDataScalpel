import type { PlatformDataType } from '../../model';

export type DataSourcePurpose = 'SOURCE' | 'STORAGE' | 'DISTRIBUTION';

export type DataSourceConnectionKind = 'JDBC' | 'KAFKA' | 'S3' | 'HTTP_API';

export type DataSourceType =
  | 'MYSQL'
  | 'POSTGRESQL'
  | 'ORACLE'
  | 'SQL_SERVER'
  | 'CLICKHOUSE'
  | 'DAMENG'
  | 'KINGBASE'
  | 'OPENGAUSS'
  | 'KAFKA'
  | 'S3'
  | 'HTTP_API';

export type HttpApiValueLocation = 'HEADER' | 'QUERY' | 'BODY';
export type HttpApiMethod = 'GET' | 'POST';
export type HttpApiAuthenticationType =
  | 'NONE'
  | 'BASIC'
  | 'BEARER_TOKEN'
  | 'API_KEY'
  | 'OAUTH2_CLIENT_CREDENTIALS'
  | 'TOKEN_ENDPOINT';

export interface HttpApiNamedValue {
  name: string;
  value: string;
}

export interface HttpApiNoneAuthentication {
  type: 'NONE';
}

export interface HttpApiBasicAuthentication {
  type: 'BASIC';
  username: string;
  passwordConfigured: boolean;
}

export interface HttpApiBearerAuthentication {
  type: 'BEARER_TOKEN';
  tokenConfigured: boolean;
}

export interface HttpApiKeyAuthentication {
  type: 'API_KEY';
  location: HttpApiValueLocation;
  name: string;
  valueTemplate: string;
  apiKeyConfigured: boolean;
}

export interface HttpApiOAuth2Authentication {
  type: 'OAUTH2_CLIENT_CREDENTIALS';
  tokenUrl: string;
  clientId: string;
  scopes: string[];
  audience: string | null;
  tokenLocation: HttpApiValueLocation;
  tokenName: string;
  tokenValueTemplate: string;
  clientSecretConfigured: boolean;
}

export interface HttpApiTokenEndpointAuthentication {
  type: 'TOKEN_ENDPOINT';
  tokenUrl: string;
  method: HttpApiMethod;
  headers: HttpApiNamedValue[];
  bodyTemplate: string | null;
  username: string | null;
  tokenPointer: string;
  expiresInPointer: string | null;
  fixedTtlSeconds: number | null;
  tokenLocation: HttpApiValueLocation;
  tokenName: string;
  tokenValueTemplate: string;
  passwordConfigured: boolean;
}

export type HttpApiAuthentication =
  | HttpApiNoneAuthentication
  | HttpApiBasicAuthentication
  | HttpApiBearerAuthentication
  | HttpApiKeyAuthentication
  | HttpApiOAuth2Authentication
  | HttpApiTokenEndpointAuthentication;

export interface HttpApiConnectionConfiguration {
  baseUrl: string;
  defaultHeaders: HttpApiNamedValue[];
  connectTimeoutMs: number;
  requestTimeoutMs: number;
  minimumRequestIntervalMs: number;
  maxRetries: number;
  authentication: HttpApiAuthentication;
  signingSecretConfigured: boolean;
  signingPrivateKeyConfigured: boolean;
}

export interface JdbcDataSourceConnection {
  kind: 'JDBC';
  host: string;
  port: number;
  databaseName: string;
  schemaName: string | null;
  username: string;
  options: Record<string, string>;
  passwordConfigured: boolean;
}

export interface KafkaDataSourceConnection {
  kind: 'KAFKA';
  bootstrapServers: string;
  securityProtocol: string | null;
  saslMechanism: string | null;
  username: string | null;
  passwordConfigured: boolean;
}

export interface S3DataSourceConnection {
  kind: 'S3';
  endpoint: string;
  region: string | null;
  bucket: string;
  rootPrefix: string | null;
  accessKey: string;
  secretKeyConfigured: boolean;
  pathStyleAccess: boolean;
}

export interface HttpApiDataSourceConnection {
  kind: 'HTTP_API';
  configuration: HttpApiConnectionConfiguration;
}

export type DataSourceConnection =
  | JdbcDataSourceConnection
  | KafkaDataSourceConnection
  | S3DataSourceConnection
  | HttpApiDataSourceConnection;

export interface JdbcDataSourceConnectionInput {
  kind: 'JDBC';
  host: string;
  port: number;
  databaseName: string;
  schemaName?: string;
  username: string;
  password?: string;
  options?: Record<string, string>;
}

export interface KafkaDataSourceConnectionInput {
  kind: 'KAFKA';
  bootstrapServers: string;
  securityProtocol?: 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL';
  saslMechanism?: string;
  username?: string;
  password?: string;
}

export interface S3DataSourceConnectionInput {
  kind: 'S3';
  endpoint: string;
  region?: string;
  bucket: string;
  rootPrefix?: string;
  accessKey: string;
  secretKey?: string;
  pathStyleAccess?: boolean;
}

export type HttpApiAuthenticationInput =
  | { type: 'NONE' }
  | { type: 'BASIC'; username: string; password?: string }
  | { type: 'BEARER_TOKEN'; token?: string }
  | {
    type: 'API_KEY';
    location: HttpApiValueLocation;
    name: string;
    valueTemplate?: string;
    apiKey?: string;
  }
  | {
    type: 'OAUTH2_CLIENT_CREDENTIALS';
    tokenUrl: string;
    clientId: string;
    scopes?: string[];
    audience?: string;
    tokenLocation?: HttpApiValueLocation;
    tokenName?: string;
    tokenValueTemplate?: string;
    clientSecret?: string;
  }
  | {
    type: 'TOKEN_ENDPOINT';
    tokenUrl: string;
    method: HttpApiMethod;
    headers?: HttpApiNamedValue[];
    bodyTemplate?: string;
    username?: string;
    tokenPointer: string;
    expiresInPointer?: string;
    fixedTtlSeconds?: number;
    tokenLocation?: HttpApiValueLocation;
    tokenName?: string;
    tokenValueTemplate?: string;
    password?: string;
  };

export interface HttpApiDataSourceConnectionInput {
  kind: 'HTTP_API';
  baseUrl: string;
  defaultHeaders?: HttpApiNamedValue[];
  connectTimeoutMs?: number;
  requestTimeoutMs?: number;
  minimumRequestIntervalMs?: number;
  maxRetries?: number;
  authentication: HttpApiAuthenticationInput;
  signingSecret?: string;
  signingPrivateKey?: string;
}

export type DataSourceConnectionInput =
  | JdbcDataSourceConnectionInput
  | KafkaDataSourceConnectionInput
  | S3DataSourceConnectionInput
  | HttpApiDataSourceConnectionInput;

export interface DataSource {
  id: string;
  code: string;
  name: string;
  directoryId: string | null;
  purposes: DataSourcePurpose[];
  type: DataSourceType;
  connectionKind: DataSourceConnectionKind;
  enabled: boolean;
  description: string | null;
  connection: DataSourceConnection;
  createdAt: string;
  updatedAt: string;
}

export interface KafkaTopic {
  name: string;
}

export interface CreateDataSourceRequest {
  code: string;
  name: string;
  directoryId?: string;
  purposes: DataSourcePurpose[];
  type: DataSourceType;
  enabled: boolean;
  description?: string;
  connection: DataSourceConnectionInput;
}

export interface UpdateDataSourceRequest {
  name: string;
  directoryId?: string;
  purposes: DataSourcePurpose[];
  type: DataSourceType;
  enabled: boolean;
  description?: string;
  connection: DataSourceConnectionInput;
}

export interface TestDataSourceConnectionRequest {
  type: DataSourceType;
  connection: DataSourceConnectionInput;
}

export interface ConnectionTestResult {
  success: boolean;
  code: string;
  message: string;
  elapsedMs: number;
  databaseProduct: string | null;
  databaseVersion: string | null;
  driverName: string | null;
  diagnostic: ConnectionTestDiagnostic | null;
}

export interface ConnectionTestDiagnostic {
  exceptionType: string;
  rawMessage: string | null;
  sqlState: string | null;
  vendorCode: number | null;
  httpStatus: number | null;
  responsePreview: string | null;
  causes: ConnectionTestCause[];
}

export interface HttpApiRequestTemplate {
  method: HttpApiMethod;
  path: string;
  queryParameters: HttpApiNamedValue[];
  headers: HttpApiNamedValue[];
  bodyTemplate: string | null;
}

export type HttpApiSignatureType = 'NONE' | 'MD5' | 'HMAC_SHA256' | 'HMAC_SHA512' | 'RSA_SHA256';
export type HttpApiSignatureEncoding = 'HEX_LOWERCASE' | 'HEX_UPPERCASE' | 'BASE64' | 'BASE64_URL';

export interface HttpApiSigningConfiguration {
  type: HttpApiSignatureType;
  canonicalTemplate: string | null;
  timestamp: { name: string; location: HttpApiValueLocation; unit: 'SECONDS' | 'MILLISECONDS' } | null;
  nonce: { name: string; location: HttpApiValueLocation } | null;
  output: {
    name: string;
    location: HttpApiValueLocation;
    encoding: HttpApiSignatureEncoding;
    valueTemplate: string | null;
  } | null;
}

export type HttpApiPaginationConfiguration =
  | { type: 'NONE' }
  | {
    type: 'PAGE_NUMBER';
    location: HttpApiValueLocation;
    pageParameter: string;
    pageSizeParameter: string;
    initialPage: number;
    pageSize: number;
    hasMorePointer: string | null;
    totalPagesPointer: string | null;
  }
  | {
    type: 'OFFSET_LIMIT';
    location: HttpApiValueLocation;
    offsetParameter: string;
    limitParameter: string;
    initialOffset: number;
    limit: number;
    hasMorePointer: string | null;
    totalPointer: string | null;
  }
  | {
    type: 'CURSOR';
    location: HttpApiValueLocation;
    cursorParameter: string;
    initialCursor: string | null;
    nextCursorPointer: string;
    hasMorePointer: string | null;
  }
  | { type: 'NEXT_URL'; nextUrlPointer: string; sameOriginOnly: boolean };

export interface HttpApiAsyncJobConfiguration {
  statusRequest: HttpApiRequestTemplate;
  jobIdPointer: string;
  statusPointer: string;
  runningStatuses: string[];
  successStatuses: string[];
  failureStatuses: string[];
  pollingIntervalMs: number;
  pollingTimeoutMs: number;
  resultRequest: HttpApiRequestTemplate;
}

export interface PlatformTypeDefinition {
  type: PlatformDataType;
  length: number | null;
  precision: number | null;
  scale: number | null;
}

export interface HttpApiOutputField {
  name: string;
  jsonPointer: string;
  type: PlatformTypeDefinition;
  nullable: boolean;
  comment: string | null;
}

export interface HttpApiExecutionLimits {
  maxPages: number;
  maxRows: number;
  maxResponseBytes: number;
  maxDurationSeconds: number;
}

export interface ApiResource {
  id: string;
  dataSourceId: string;
  code: string;
  name: string;
  connectorType: string;
  enabled: boolean;
  request: HttpApiRequestTemplate;
  signing: HttpApiSigningConfiguration;
  invocationType: 'SINGLE_REQUEST' | 'PAGINATED_REQUEST' | 'ASYNC_JOB';
  pagination: HttpApiPaginationConfiguration;
  asyncJob: HttpApiAsyncJobConfiguration | null;
  recordsPointer: string;
  outputFields: HttpApiOutputField[];
  limits: HttpApiExecutionLimits;
  createdAt: string;
  updatedAt: string;
}

export interface ApiResourceWriteRequest {
  name: string;
  connectorType?: string;
  enabled: boolean;
  request: HttpApiRequestTemplate;
  signing: HttpApiSigningConfiguration;
  invocationType: ApiResource['invocationType'];
  pagination: HttpApiPaginationConfiguration;
  asyncJob: HttpApiAsyncJobConfiguration | null;
  recordsPointer: string;
  outputFields: HttpApiOutputField[];
  limits: HttpApiExecutionLimits;
}

export interface CreateApiResourceRequest extends ApiResourceWriteRequest {
  code: string;
}

export type UpdateApiResourceRequest = ApiResourceWriteRequest;

export interface HttpApiRuntimeParameter {
  name: string;
  value: string;
}

export interface ApiResourceTestResult {
  success: boolean;
  code: string;
  message: string;
  elapsedMs: number;
  httpStatus: number | null;
  contentType: string | null;
  recordCount: number;
  columns: string[];
  rows: unknown[][];
  diagnostic: {
    exceptionType: string;
    rawMessage: string | null;
    httpStatus: number | null;
    responsePreview: string | null;
    causes: ConnectionTestCause[];
  } | null;
}

export interface ConnectionTestCause {
  exceptionType: string;
  message: string | null;
}

export type DatabaseCapability =
  | 'TEST_CONNECTION'
  | 'LIST_NAMESPACES'
  | 'LIST_TABLES'
  | 'READ_TABLE_METADATA'
  | 'PREVIEW_DATA'
  | 'SQL_SERVICE_QUERY'
  | 'CREATE_TABLE';

export interface ConnectionOptionChoice {
  value: string;
  label: string;
}

export interface ConnectionOptionDefinition {
  key: string;
  label: string;
  type: 'TEXT' | 'BOOLEAN' | 'SELECT';
  defaultValue: string | null;
  choices: ConnectionOptionChoice[];
}

export interface DataSourceTypeDefinition {
  id: DataSourceType;
  displayName: string;
  connectionKind: DataSourceConnectionKind;
  supportedPurposes: DataSourcePurpose[];
  connectionTestAvailable: boolean;
  metadataAvailable: boolean;
  defaultPort: number | null;
  databaseNameLabel: string | null;
  schemaNameLabel: string | null;
  defaultSchema: string | null;
  namespaceMode: 'CATALOG' | 'SCHEMA' | 'CATALOG_AND_SCHEMA' | null;
  capabilities: DatabaseCapability[];
  connectionOptions: ConnectionOptionDefinition[];
  driverAvailable: boolean;
}

export interface DataSourceNamespace {
  catalog: string | null;
  schema: string | null;
  displayName: string;
  defaultNamespace: boolean;
}

export interface TableIdentifier {
  catalog: string | null;
  schema: string | null;
  table: string;
}

export interface DataSourceTable {
  identifier: TableIdentifier;
  type: string;
  comment: string | null;
}

export interface TableListResult {
  tables: DataSourceTable[];
  truncated: boolean;
}

export interface ColumnMetadata {
  name: string;
  ordinal: number;
  jdbcType: number;
  nativeType: string;
  logicalType: PlatformDataType;
  length: number | null;
  precision: number | null;
  scale: number | null;
  nullable: boolean;
  defaultValue: string | null;
  autoIncrement: boolean;
  generated: boolean;
  comment: string | null;
}

export interface PrimaryKeyMetadata {
  name: string | null;
  columns: string[];
}

export interface IndexMetadata {
  name: string;
  unique: boolean;
  columns: string[];
}

export interface TableMetadata {
  table: DataSourceTable;
  columns: ColumnMetadata[];
  primaryKey: PrimaryKeyMetadata | null;
  indexes: IndexMetadata[];
}

export interface PreviewColumn {
  name: string;
  nativeType: string;
  logicalType: string;
}

export interface TablePreview {
  table: TableIdentifier;
  columns: PreviewColumn[];
  rows: unknown[][];
  limit: number;
  truncated: boolean;
}

export interface TableQuery {
  catalog?: string;
  schema?: string;
  keyword?: string;
  includeViews?: boolean;
}

export type DataSourcePurposeFilter = DataSourcePurpose | 'BOTH';

export interface DataSourceFilters {
  keyword?: string;
  directoryIds?: string[];
  uncategorized?: boolean;
  purpose?: DataSourcePurposeFilter;
  type?: DataSourceType;
  enabled?: boolean;
}

export const dataSourceTypeLabels: Record<DataSourceType, string> = {
  MYSQL: 'MySQL',
  POSTGRESQL: 'PostgreSQL',
  ORACLE: 'Oracle',
  SQL_SERVER: 'SQL Server',
  CLICKHOUSE: 'ClickHouse',
  DAMENG: '达梦',
  KINGBASE: '人大金仓',
  OPENGAUSS: 'openGauss',
  KAFKA: 'Kafka',
  S3: 'S3 兼容对象存储',
  HTTP_API: 'HTTP API',
};

export const dataSourcePurposeLabels: Record<DataSourcePurpose, string> = {
  SOURCE: '数据源',
  STORAGE: '数据存储',
  DISTRIBUTION: '数据分发',
};
