import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LineageGraphNode } from '../model/dataModel';

const mocks = vi.hoisted(() => ({ user: vi.fn(), model: vi.fn(), task: vi.fn(), service: vi.fn(), source: vi.fn(), file: vi.fn() }));
vi.mock('../../system', () => ({ useCurrentUser: mocks.user }));
vi.mock('../hooks/useDataModels', () => ({ useDataModel: mocks.model }));
vi.mock('../../task', () => ({ useTask: mocks.task, taskTypeLabels: { SPARK_CANVAS: 'Spark 画布' }, taskStatusLabels: { PUBLISHED: '已发布' }, taskPageHref: (path: string) => path }));
vi.mock('../../dataservice', () => ({ useDataService: mocks.service, dataServiceTypeLabels: { STANDARD_TABLE: '标准单表' }, dataServiceStatusLabels: { ENABLED: '已启用' }, dataServiceDeploymentStatusLabels: { FAILED: '部署失败' } }));
vi.mock('../../datasource', () => ({ useDataSource: mocks.source, DataSourceTypeIcon: () => null, dataSourceTypeLabels: { POSTGRESQL: 'PostgreSQL' } }));
vi.mock('../../filedataset', () => ({ useFileDatasetCanvasMetadata: mocks.file, fileDatasetTypeLabels: { CSV: 'CSV' }, fileDatasetParseStatusLabels: { READY: '已就绪' } }));
import { LineageNodeResourceDetails } from './LineageNodeResourceDetails';

const node = (overrides: Partial<LineageGraphNode> = {}): LineageGraphNode => ({
  id: 'model', kind: 'MODEL', label: '抄表', subtitle: 'meter_readings', side: 'CURRENT', depth: 0,
  modelId: 'model', modelFieldId: null, taskId: null, dataSourceId: null, taskStatus: null,
  definitionVersion: null, writeMode: null, stale: false, externalResourceType: null,
  resourceId: null, dataServiceId: null, dataServiceType: null, dataServiceStatus: null,
  routePath: null, fieldOwner: null, focusRoot: false, focusFieldKeys: [], ...overrides,
});
const success = (data: unknown) => ({ data, isPending: false, isError: false, refetch: vi.fn() });
const renderDetails = (selected = node()) => render(<MemoryRouter><LineageNodeResourceDetails node={selected} /></MemoryRouter>);

describe('lineage business properties', () => {
  afterEach(cleanup);
  beforeEach(() => {
    vi.resetAllMocks();
    mocks.user.mockReturnValue(success({ permissions: ['model.view', 'task.view', 'service.view', 'datasource.view', 'filedataset.view'] }));
    mocks.model.mockReturnValue(success({ model: { id: 'model', name: '抄表', code: 'meter_readings', status: 'PUBLISHED', schemaVersion: 2, physicalTableMode: 'MANAGED', storageDataSourceName: 'pg_storage', catalogName: 'warehouse', schemaName: 'public', physicalTableName: 'meter_readings', description: '每月抄表记录' }, fields: [{ id: 'field', code: 'meter_id', name: '水表编号', primaryKey: true, nullable: false, fieldType: 'LONG', description: null }] }));
  });
  it('shows model storage, qualified location and real key fields, with a detail link', () => {
    renderDetails();
    expect(screen.getByLabelText('当前资源属性')).toHaveTextContent('warehouse / public');
    expect(screen.getByText('pg_storage')).toBeInTheDocument();
    expect(screen.getByText('meter_id')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看模型详情 →' })).toHaveAttribute('href', '/model/model');
    expect(mocks.task).not.toHaveBeenCalled();
    expect(mocks.source).not.toHaveBeenCalled();
  });
  it('does not fetch protected resource details without permission', () => {
    mocks.user.mockReturnValue(success({ permissions: [] }));
    renderDetails();
    expect(screen.getByText(/暂无查看该资源详情的权限/)).toBeInTheDocument();
    expect(mocks.model).not.toHaveBeenCalled();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
  it('resolves a selected field by stable ID and does not substitute a deleted field', () => {
    const view = renderDetails(node({ kind: 'FIELD', modelFieldId: 'field' }));
    expect(screen.getByText('业务主键 · 不允许空值')).toBeInTheDocument();
    expect(screen.getByText('水表编号')).toBeInTheDocument();
    view.rerender(<MemoryRouter><LineageNodeResourceDetails node={node({ kind: 'FIELD', modelFieldId: 'deleted' })} /></MemoryRouter>);
    expect(screen.getByText(/当前模型中已找不到该字段/)).toBeInTheDocument();
    expect(screen.queryByText('水表编号')).not.toBeInTheDocument();
  });
  it('keeps request failure visible and retries without inventing property values', () => {
    const refetch = vi.fn();
    mocks.model.mockReturnValue({ isPending: false, isError: true, refetch });
    renderDetails();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(refetch).toHaveBeenCalledOnce();
    expect(screen.queryByText('pg_storage')).not.toBeInTheDocument();
  });
  it('shows task execution and output information', () => {
    mocks.task.mockReturnValue(success({ id: 'task', type: 'SPARK_CANVAS', status: 'PUBLISHED', computeEngineName: 'Spark 集群', definitionConfigured: true, definitionVersion: 3, outputModelName: '月度汇总', description: '按月份归集读数' }));
    renderDetails(node({ kind: 'TASK', modelId: null, taskId: 'task' }));
    expect(screen.getByText('Spark 集群')).toBeInTheDocument();
    expect(screen.getByText('月度汇总')).toBeInTheDocument();
    expect(mocks.model).not.toHaveBeenCalled();
  });
  it('distinguishes enabled service state from deployment failure', () => {
    mocks.service.mockReturnValue(success({ id: 'service', type: 'STANDARD_TABLE', status: 'ENABLED', code: 'readings', contextPath: '/readings', deploymentStatus: 'FAILED', revision: 2, definitionVersion: 3, description: null }));
    renderDetails(node({ kind: 'DATA_SERVICE', modelId: null, dataServiceId: 'service' }));
    expect(screen.getByText('已启用')).toBeInTheDocument();
    expect(screen.getByText('部署失败')).toBeInTheDocument();
    expect(screen.getByText('/readings')).toBeInTheDocument();
  });
  it('loads a file table by resource ID and links its dataset', () => {
    mocks.file.mockReturnValue(success({ tables: [{ fileDatasetTableId: 'table', fileDatasetId: 'dataset', fileDatasetName: '本月读数', datasetType: 'CSV', code: 'readings', name: '读数表', parseStatus: 'READY', fields: [] }] }));
    renderDetails(node({ kind: 'EXTERNAL_RESOURCE', modelId: null, externalResourceType: 'FILE_DATASET_TABLE', resourceId: 'table' }));
    expect(mocks.file).toHaveBeenCalledWith(['table']);
    expect(screen.getByRole('link')).toHaveAttribute('href', '/file-dataset/dataset');
  });
  it('shows connection identity without rendering credentials or implying a successful connection test', () => {
    mocks.source.mockReturnValue(success({ id: 'source', name: '仓储库', code: 'storage', type: 'POSTGRESQL', enabled: true, connection: { kind: 'JDBC', databaseName: 'warehouse', schemaName: 'public', username: 'private_user', options: { token: 'private_token' } } }));
    renderDetails(node({ kind: 'JDBC_TABLE', modelId: null, dataSourceId: 'source' }));
    expect(screen.getByText('PostgreSQL')).toBeInTheDocument();
    expect(screen.getByText('连接默认库')).toBeInTheDocument();
    expect(screen.queryByText('private_user')).not.toBeInTheDocument();
    expect(screen.queryByText('private_token')).not.toBeInTheDocument();
    expect(screen.queryByText('连接正常')).not.toBeInTheDocument();
  });

});
