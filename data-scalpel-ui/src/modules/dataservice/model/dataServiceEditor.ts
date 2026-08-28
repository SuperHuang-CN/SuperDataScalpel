import type { PlatformDataType } from '../../model';
import type {
  ScriptRequestExample,
  ScriptRequestParameter,
} from '@superhuang/super-api-studio-script-workbench';
import type {
  CreateDataServiceRequest,
  DataServiceDetail,
  DataServiceType,
  PlatformTypeDefinition,
  SqlServiceParameterDefinition,
  SqlServiceTestRequest,
  UpdateDataServiceDefinitionRequest,
  UpdateDataServiceRequest,
} from './dataService';

export type DataServiceEditorMode = 'CREATE' | 'EDITABLE' | 'READ_ONLY' | 'DEPLOYMENT_LOCKED';

export const dataServiceEditorMode = (detail: DataServiceDetail | undefined): DataServiceEditorMode => {
  if (!detail) return 'CREATE';
  if (detail.deploymentStatus && detail.deploymentStatus !== 'REMOVED') {
    if (detail.status === 'ENABLED' && detail.deploymentStatus === 'DEPLOYED') return 'READ_ONLY';
    return 'DEPLOYMENT_LOCKED';
  }
  return detail.status === 'ENABLED' ? 'READ_ONLY' : 'EDITABLE';
};

export interface SqlParameterFormValue {
  name?: string;
  type?: PlatformDataType;
  length?: number;
  precision?: number;
  scale?: number;
  required?: boolean;
  description?: string;
}

export interface DataServiceFormValues {
  code?: string;
  name?: string;
  directoryId?: string;
  type: DataServiceType;
  modelId?: string;
  dataSourceId?: string;
  modelIds?: string[];
  engineId?: string;
  routePath?: string;
  sqlText?: string;
  script?: string;
  examples?: ScriptRequestExample[];
  parameters?: SqlParameterFormValue[];
  description?: string;
}

export const defaultScriptRequestExamples = (): ScriptRequestExample[] => ([{
  id: 'default',
  name: '默认示例',
  bodyText: '{\n  \n}',
  query: [],
  headers: [{ id: 'default-content-type', key: 'Content-Type', value: 'application/json' }],
}]);

const duplicateParameterKey = (
  parameters: ScriptRequestParameter[],
  caseInsensitive: boolean,
): string | undefined => {
  const keys = new Set<string>();
  for (const parameter of parameters) {
    const key = parameter.key.trim();
    if (!key) continue;
    const comparisonKey = caseInsensitive ? key.toLowerCase() : key;
    if (keys.has(comparisonKey)) return key;
    keys.add(comparisonKey);
  }
  return undefined;
};

export const scriptRequestExamplesValidationMessage = (
  examples: ScriptRequestExample[] | undefined,
): string | undefined => {
  if (!examples?.length) return '脚本服务至少需要一个 Example';
  const ids = new Set<string>();
  const names = new Set<string>();
  for (const example of examples) {
    const id = example.id.trim();
    const name = example.name.trim();
    if (!id) return 'Example ID 不能为空';
    if (ids.has(id)) return `Example ID 不能重复：${id}`;
    ids.add(id);
    if (!name) return 'Example 名称不能为空';
    if (names.has(name)) return `Example 名称不能重复：${name}`;
    names.add(name);
    try {
      JSON.parse(example.bodyText.trim() || '{}');
    } catch {
      return `Example“${name}”的 Body 不是合法 JSON`;
    }
    const duplicateQuery = duplicateParameterKey(example.query, false);
    if (duplicateQuery) return `Example“${name}”的 Query 参数名重复：${duplicateQuery}`;
    const duplicateHeader = duplicateParameterKey(example.headers, true);
    if (duplicateHeader) return `Example“${name}”的 Header 参数名重复：${duplicateHeader}`;
  }
  return undefined;
};

const normalizedExampleParameters = (parameters: ScriptRequestParameter[]): ScriptRequestParameter[] => (
  parameters
    .filter((parameter) => parameter.key.trim())
    .map((parameter) => ({
      id: parameter.id.trim(),
      key: parameter.key.trim(),
      value: parameter.value,
    }))
);

const normalizedScriptRequestExamples = (examples: ScriptRequestExample[]): ScriptRequestExample[] => (
  examples.map((example) => ({
    id: example.id.trim(),
    name: example.name.trim(),
    bodyText: example.bodyText.trim() || '{}',
    query: normalizedExampleParameters(example.query),
    headers: normalizedExampleParameters(example.headers),
  }))
);

export const parameterNamePattern = /^[A-Za-z][A-Za-z0-9_]{0,63}$/;
export const parameterTypes: PlatformDataType[] = [
  'BOOLEAN', 'BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'STRING',
  'DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ',
];

export const typeLabels: Record<PlatformDataType, string> = {
  BOOLEAN: 'BOOLEAN', BYTE: 'BYTE', SHORT: 'SHORT', INTEGER: 'INTEGER', LONG: 'LONG',
  FLOAT: 'FLOAT', DOUBLE: 'DOUBLE', DECIMAL: 'DECIMAL', STRING: 'STRING', BINARY: 'BINARY',
  DATE: 'DATE', TIMESTAMP: 'TIMESTAMP', TIMESTAMP_NTZ: 'TIMESTAMP_NTZ',
  GEOMETRY: 'GEOMETRY',
};

const normalizedOptionalText = (value: string | undefined): string | undefined => value?.trim() || undefined;

export const toTypeDefinition = (parameter: SqlParameterFormValue): PlatformTypeDefinition => ({
  type: parameter.type ?? 'STRING',
  length: parameter.type === 'STRING' ? parameter.length ?? null : null,
  precision: parameter.type === 'DECIMAL' ? parameter.precision ?? null : null,
  scale: parameter.type === 'DECIMAL' ? parameter.scale ?? null : null,
});

export const toParameterDefinition = (parameter: SqlParameterFormValue): SqlServiceParameterDefinition => ({
  name: parameter.name?.trim() ?? '',
  typeDefinition: toTypeDefinition(parameter),
  required: parameter.required ?? false,
  description: normalizedOptionalText(parameter.description) ?? null,
});

export const toParameterFormValue = (parameter: SqlServiceParameterDefinition): SqlParameterFormValue => ({
  name: parameter.name,
  type: parameter.typeDefinition.type,
  length: parameter.typeDefinition.length ?? undefined,
  precision: parameter.typeDefinition.precision ?? undefined,
  scale: parameter.typeDefinition.scale ?? undefined,
  required: parameter.required,
  description: parameter.description ?? undefined,
});

export const initialDataServiceFormValues = (
  type: DataServiceType,
  directoryId?: string,
): DataServiceFormValues => ({
  directoryId,
  type,
  modelIds: [],
  sqlText: '',
  script: 'return [message: "Hello DataScalpel"]',
  examples: defaultScriptRequestExamples(),
  parameters: [],
});

export const detailToDataServiceFormValues = (detail: DataServiceDetail): DataServiceFormValues => ({
  code: detail.code,
  name: detail.name,
  directoryId: detail.directoryId ?? undefined,
  type: detail.type,
  modelId: detail.standardDefinition?.modelId,
  dataSourceId: detail.sqlDefinition?.dataSourceId ?? detail.scriptDefinition?.dataSourceId,
  modelIds: detail.sqlDefinition?.modelIds ?? [],
  engineId: detail.engineId,
  routePath: detail.engineRoutePath,
  sqlText: detail.sqlDefinition?.sqlText ?? '',
  script: detail.scriptDefinition?.script ?? 'return [message: "Hello DataScalpel"]',
  examples: detail.scriptDefinition?.examples?.length
    ? detail.scriptDefinition.examples
    : defaultScriptRequestExamples(),
  parameters: detail.sqlDefinition?.parameters.map(toParameterFormValue) ?? [],
  description: detail.description ?? undefined,
});

export const buildDataServiceUpdateRequest = (values: DataServiceFormValues): UpdateDataServiceRequest => {
  const common = {
    name: values.name?.trim() ?? '',
    directoryId: values.directoryId,
    engineId: values.engineId ?? '',
    type: values.type,
    description: normalizedOptionalText(values.description),
  };
  return {
    ...common,
    standardDefinition: null,
    sqlDefinition: null,
    scriptDefinition: null,
  };
};

export const buildDataServiceDefinitionRequest = (
  values: DataServiceFormValues,
): UpdateDataServiceDefinitionRequest => {
  if (values.type === 'STANDARD_TABLE') {
    return {
      standardDefinition: { modelId: values.modelId ?? '' },
      sqlDefinition: null,
      scriptDefinition: null,
    };
  }
  if (values.type === 'SQL_QUERY') {
    return {
      standardDefinition: null,
      sqlDefinition: {
        dataSourceId: values.dataSourceId ?? '',
        modelIds: values.modelIds ?? [],
        sqlText: values.sqlText?.trim() ?? '',
        parameters: (values.parameters ?? []).map(toParameterDefinition),
      },
      scriptDefinition: null,
    };
  }
  return {
    standardDefinition: null,
    sqlDefinition: null,
    scriptDefinition: {
      dataSourceId: values.dataSourceId ?? '',
      script: values.script ?? '',
      examples: normalizedScriptRequestExamples(values.examples ?? defaultScriptRequestExamples()),
    },
  };
};

export const buildDataServiceCreateRequest = (values: DataServiceFormValues): CreateDataServiceRequest => ({
  ...buildDataServiceUpdateRequest(values),
  code: values.code?.trim().toLowerCase() ?? '',
});

const testArgument = (value: string | undefined, type: PlatformDataType): unknown => {
  if (value === undefined || value === '') return null;
  if (type === 'BOOLEAN') return value === 'true';
  return value;
};

export const buildSqlServiceTestRequest = (
  values: DataServiceFormValues,
  testValues: Record<string, string>,
): SqlServiceTestRequest => {
  const parameters = (values.parameters ?? []).map(toParameterDefinition);
  return {
    dataSourceId: values.dataSourceId ?? '',
    modelIds: values.modelIds ?? [],
    sqlText: values.sqlText?.trim() ?? '',
    parameters,
    arguments: Object.fromEntries(parameters.map((parameter) => [
      parameter.name,
      testArgument(testValues[parameter.name], parameter.typeDefinition.type),
    ])),
    previewSize: 20,
  };
};

export const dataServiceFormFingerprint = (values: DataServiceFormValues): string => JSON.stringify({
  code: values.code?.trim().toLowerCase() ?? '',
  basic: buildDataServiceUpdateRequest(values),
  definition: buildDataServiceDefinitionRequest(values),
});

export const dataServiceBasicFingerprint = (values: DataServiceFormValues): string => JSON.stringify({
  code: values.code?.trim().toLowerCase() ?? '',
  basic: buildDataServiceUpdateRequest(values),
});

export const dataServiceDefinitionFingerprint = (values: DataServiceFormValues): string => JSON.stringify(
  buildDataServiceDefinitionRequest(values),
);

export const typeDescription = (type: PlatformTypeDefinition): string => {
  if (type.type === 'STRING' && type.length) return `STRING(${type.length})`;
  if (type.type === 'DECIMAL') return `DECIMAL(${type.precision},${type.scale})`;
  return type.type;
};
