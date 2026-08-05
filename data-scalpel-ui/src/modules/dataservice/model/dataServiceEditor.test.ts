import { describe, expect, it } from 'vitest';
import type { ScriptRequestExample } from '@superhuang/super-api-studio-script-workbench';
import {
  buildDataServiceCreateRequest,
  buildSqlServiceTestRequest,
  dataServiceEditorMode,
  dataServiceFormFingerprint,
  scriptRequestExamplesValidationMessage,
  type DataServiceFormValues,
} from './dataServiceEditor';
import type { DataServiceDetail, DataServiceDeploymentStatus, DataServiceStatus } from './dataService';

const sqlValues: DataServiceFormValues = {
  code: 'Customer_Query',
  name: '客户查询',
  type: 'SQL_QUERY',
  accessMode: 'SUBSCRIPTION_REQUIRED',
  dataSourceId: 'source-1',
  modelIds: ['model-1', 'model-2'],
  engineId: 'engine-1',
  routePath: '/open-api/v1/customers',
  sqlText: 'select * from customer where department_id = :departmentId',
  parameters: [{ name: 'departmentId', type: 'LONG', required: true }],
};

describe('data-service editor model', () => {
  it('builds SQL create and test requests with ordered model associations', () => {
    expect(buildDataServiceCreateRequest(sqlValues)).toMatchObject({
      code: 'customer_query',
      type: 'SQL_QUERY',
      accessMode: 'SUBSCRIPTION_REQUIRED',
      standardDefinition: null,
      scriptDefinition: null,
      sqlDefinition: {
        dataSourceId: 'source-1',
        modelIds: ['model-1', 'model-2'],
      },
    });
    expect(buildSqlServiceTestRequest(sqlValues, { departmentId: '1001' })).toMatchObject({
      modelIds: ['model-1', 'model-2'],
      arguments: { departmentId: '1001' },
    });
  });

  it('does not include transient SQL test state in the dirty fingerprint', () => {
    expect(dataServiceFormFingerprint(sqlValues)).toBe(dataServiceFormFingerprint({ ...sqlValues }));
  });

  it('normalizes script Examples into the service definition', () => {
    const values: DataServiceFormValues = {
      code: 'Customer_Script',
      name: '客户脚本',
      type: 'SCRIPT_API',
      accessMode: 'PUBLIC',
      dataSourceId: 'source-1',
      engineId: 'engine-1',
      routePath: '/open-api/v1/customer-script',
      script: 'return request.body',
      examples: [{
        id: 'example-1',
        name: '按 ID 查询',
        bodyText: '{"id": 1}',
        query: [
          { id: 'query-1', key: 'verbose', value: 'true' },
          { id: 'query-empty', key: '  ', value: 'ignored' },
        ],
        headers: [{ id: 'header-1', key: ' X-Trace-Id ', value: 'trace-1' }],
      }],
    };

    expect(buildDataServiceCreateRequest(values)).toMatchObject({
      code: 'customer_script',
      standardDefinition: null,
      sqlDefinition: null,
      scriptDefinition: {
        dataSourceId: 'source-1',
        examples: [{
          id: 'example-1',
          name: '按 ID 查询',
          query: [{ id: 'query-1', key: 'verbose', value: 'true' }],
          headers: [{ id: 'header-1', key: 'X-Trace-Id', value: 'trace-1' }],
        }],
      },
    });
  });

  it('rejects invalid script Example names, bodies and duplicate parameter keys', () => {
    const valid: ScriptRequestExample[] = [{
      id: 'example-1',
      name: '默认示例',
      bodyText: '{}',
      query: [],
      headers: [],
    }];
    expect(scriptRequestExamplesValidationMessage(valid)).toBeUndefined();
    expect(scriptRequestExamplesValidationMessage([
      ...valid,
      { ...valid[0], id: 'example-2' },
    ])).toContain('名称不能重复');
    expect(scriptRequestExamplesValidationMessage([
      { ...valid[0], bodyText: '{' },
    ])).toContain('Body 不是合法 JSON');
    expect(scriptRequestExamplesValidationMessage([{
      ...valid[0],
      headers: [
        { id: 'header-1', key: 'X-Trace-Id', value: 'one' },
        { id: 'header-2', key: 'x-trace-id', value: 'two' },
      ],
    }])).toContain('Header 参数名重复');
  });

  it('derives editable, read-only and deployment-locked page modes from server state', () => {
    const detail = (status: DataServiceStatus, deploymentStatus: DataServiceDeploymentStatus | null): DataServiceDetail => ({
      id: 'service-1', code: 'customer_query', name: '客户查询', directoryId: null,
      type: 'SQL_QUERY', engineId: 'engine-1', routePath: '/open-api/v1/customers',
      accessMode: 'PUBLIC',
      status, revision: 1, deploymentStatus, deploymentError: null, deployedAt: null,
      gatewayBindings: [],
      description: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
      standardDefinition: null,
      scriptDefinition: null,
      sqlDefinition: { dataSourceId: 'source-1', modelIds: ['model-1'], sqlText: 'select 1', parameters: [], version: 1 },
    });

    expect(dataServiceEditorMode(undefined)).toBe('CREATE');
    expect(dataServiceEditorMode(detail('DRAFT', null))).toBe('EDITABLE');
    expect(dataServiceEditorMode(detail('DISABLED', 'REMOVED'))).toBe('EDITABLE');
    expect(dataServiceEditorMode(detail('ENABLED', 'DEPLOYED'))).toBe('READ_ONLY');
    expect(dataServiceEditorMode(detail('DRAFT', 'FAILED'))).toBe('DEPLOYMENT_LOCKED');
    expect(dataServiceEditorMode(detail('ENABLED', 'PENDING'))).toBe('DEPLOYMENT_LOCKED');
  });
});
