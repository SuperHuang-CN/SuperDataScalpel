import type { PlatformDataType } from '../../model';
import type {
  CreateDataServiceRequest,
  DataServiceDetail,
  DataServiceAccessMode,
  DataServiceType,
  PlatformTypeDefinition,
  SqlServiceParameterDefinition,
  SqlServiceTestRequest,
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
  accessMode: DataServiceAccessMode;
  modelId?: string;
  dataSourceId?: string;
  modelIds?: string[];
  engineId?: string;
  routePath?: string;
  sqlText?: string;
  parameters?: SqlParameterFormValue[];
  description?: string;
}

export const routePathPattern = /^\/open-api\/v1\/[a-z0-9][a-z0-9/_-]*$/;
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

export const initialDataServiceFormValues = (type: DataServiceType): DataServiceFormValues => ({
  type,
  accessMode: 'PUBLIC',
  routePath: '/open-api/v1/',
  modelIds: [],
  sqlText: '',
  parameters: [],
});

export const detailToDataServiceFormValues = (detail: DataServiceDetail): DataServiceFormValues => ({
  code: detail.code,
  name: detail.name,
  directoryId: detail.directoryId ?? undefined,
  type: detail.type,
  accessMode: detail.accessMode,
  modelId: detail.standardDefinition?.modelId,
  dataSourceId: detail.sqlDefinition?.dataSourceId,
  modelIds: detail.sqlDefinition?.modelIds ?? [],
  engineId: detail.engineId,
  routePath: detail.routePath,
  sqlText: detail.sqlDefinition?.sqlText ?? '',
  parameters: detail.sqlDefinition?.parameters.map(toParameterFormValue) ?? [],
  description: detail.description ?? undefined,
});

export const buildDataServiceUpdateRequest = (values: DataServiceFormValues): UpdateDataServiceRequest => {
  const common = {
    name: values.name?.trim() ?? '',
    directoryId: values.directoryId,
    engineId: values.engineId ?? '',
    routePath: values.routePath?.trim().toLowerCase() ?? '',
    accessMode: values.accessMode,
    type: values.type,
    description: normalizedOptionalText(values.description),
  };
  if (values.type === 'STANDARD_TABLE') {
    return {
      ...common,
      standardDefinition: { modelId: values.modelId ?? '' },
      sqlDefinition: null,
    };
  }
  return {
    ...common,
    standardDefinition: null,
    sqlDefinition: {
      dataSourceId: values.dataSourceId ?? '',
      modelIds: values.modelIds ?? [],
      sqlText: values.sqlText?.trim() ?? '',
      parameters: (values.parameters ?? []).map(toParameterDefinition),
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
  definition: buildDataServiceUpdateRequest(values),
});

export const typeDescription = (type: PlatformTypeDefinition): string => {
  if (type.type === 'STRING' && type.length) return `STRING(${type.length})`;
  if (type.type === 'DECIMAL') return `DECIMAL(${type.precision},${type.scale})`;
  return type.type;
};
