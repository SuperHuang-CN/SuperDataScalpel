import { describe, expect, it } from 'vitest';
import {
  buildDataServiceCreateRequest,
  buildSqlServiceTestRequest,
  dataServiceEditorMode,
  dataServiceFormFingerprint,
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

  it('derives editable, read-only and deployment-locked page modes from server state', () => {
    const detail = (status: DataServiceStatus, deploymentStatus: DataServiceDeploymentStatus | null): DataServiceDetail => ({
      id: 'service-1', code: 'customer_query', name: '客户查询', directoryId: null,
      type: 'SQL_QUERY', engineId: 'engine-1', routePath: '/open-api/v1/customers',
      accessMode: 'PUBLIC',
      status, revision: 1, deploymentStatus, deploymentError: null, deployedAt: null,
      gatewayBindings: [],
      description: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
      standardDefinition: null,
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
