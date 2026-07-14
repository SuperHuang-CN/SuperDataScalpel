export type DataSourcePurpose = 'SOURCE' | 'STORAGE' | 'DISTRIBUTION';

export type DatabaseType =
  | 'MYSQL'
  | 'POSTGRESQL'
  | 'ORACLE'
  | 'SQL_SERVER'
  | 'CLICKHOUSE'
  | 'DAMENG'
  | 'KINGBASE'
  | 'OPENGAUSS';

export interface DataSourceConnection {
  host: string;
  port: number;
  databaseName: string;
  schemaName: string | null;
  username: string;
  options: Record<string, string>;
  passwordConfigured: boolean;
}

export interface DataSourceConnectionInput {
  host: string;
  port: number;
  databaseName: string;
  schemaName?: string;
  username: string;
  password?: string;
  options?: Record<string, string>;
}

export interface DataSource {
  id: string;
  code: string;
  name: string;
  directoryId: string | null;
  purposes: DataSourcePurpose[];
  databaseType: DatabaseType;
  enabled: boolean;
  description: string | null;
  connection: DataSourceConnection;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDataSourceRequest {
  code: string;
  name: string;
  directoryId?: string;
  purposes: DataSourcePurpose[];
  databaseType: DatabaseType;
  enabled: boolean;
  description?: string;
  connection: DataSourceConnectionInput;
}

export interface UpdateDataSourceRequest {
  name: string;
  directoryId?: string;
  purposes: DataSourcePurpose[];
  databaseType: DatabaseType;
  enabled: boolean;
  description?: string;
  connection: DataSourceConnectionInput;
}

export interface TestDataSourceConnectionRequest {
  databaseType: DatabaseType;
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
}

export type DatabaseCapability =
  | 'TEST_CONNECTION'
  | 'LIST_NAMESPACES'
  | 'LIST_TABLES'
  | 'READ_TABLE_METADATA'
  | 'PREVIEW_DATA';

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

export interface DatabaseTypeDefinition {
  id: DatabaseType;
  displayName: string;
  defaultPort: number;
  databaseNameLabel: string;
  schemaNameLabel: string;
  defaultSchema: string | null;
  namespaceMode: 'CATALOG' | 'SCHEMA' | 'CATALOG_AND_SCHEMA';
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
  logicalType: string;
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
  databaseType?: DatabaseType;
  enabled?: boolean;
}

export const databaseTypeLabels: Record<DatabaseType, string> = {
  MYSQL: 'MySQL',
  POSTGRESQL: 'PostgreSQL',
  ORACLE: 'Oracle',
  SQL_SERVER: 'SQL Server',
  CLICKHOUSE: 'ClickHouse',
  DAMENG: '达梦',
  KINGBASE: '人大金仓',
  OPENGAUSS: 'openGauss',
};

export const dataSourcePurposeLabels: Record<DataSourcePurpose, string> = {
  SOURCE: '数据源',
  STORAGE: '数据存储',
  DISTRIBUTION: '数据分发',
};
