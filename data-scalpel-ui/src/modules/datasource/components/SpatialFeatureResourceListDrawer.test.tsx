import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { DataSource, SpatialCatalogEntry, SpatialFeatureResource } from '../model/dataSource';

const hookState = vi.hoisted(() => ({
  create: vi.fn(),
  update: vi.fn(),
  refresh: vi.fn(),
  remove: vi.fn(),
  refetchResources: vi.fn(),
  refetchCatalog: vi.fn(),
}));

const resource: SpatialFeatureResource = {
  id: 'spatial-resource-id',
  dataSourceId: 'spatial-source-id',
  code: 'province_boundary',
  name: '省级行政区划',
  protocol: 'WFS',
  remoteIdentifier: 'geo:province_boundary',
  enabled: true,
  serviceTitle: '政务空间服务',
  geometryFieldName: 'shape',
  epsgCode: 4490,
  objectIdFieldName: 'objectid',
  wfsVersion: '2.0.0',
  outputFormat: 'application/json',
  axisOrder: 'XY',
  columns: [{
    name: 'objectid',
    fieldType: 'LONG',
    length: null,
    precision: null,
    scale: null,
    nullable: false,
    defaultValue: null,
    autoIncrement: false,
    generated: false,
    comment: '主键',
    geometry: null,
  }],
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-02T08:30:00Z',
};

const catalogEntries: SpatialCatalogEntry[] = [{
  protocol: 'WFS',
  remoteIdentifier: 'geo:city_boundary',
  name: 'city_boundary',
  title: '市级行政区划',
  kind: 'FeatureType',
  selectable: true,
  epsgCode: 4490,
}];

vi.mock('../hooks/useDataSources', () => ({
  useSpatialFeatureResources: () => ({
    data: [resource],
    error: null,
    isFetching: false,
    refetch: hookState.refetchResources,
  }),
  useSpatialCatalog: () => ({
    data: catalogEntries,
    error: null,
    isFetching: false,
    refetch: hookState.refetchCatalog,
  }),
  useCreateSpatialFeatureResource: () => ({
    mutateAsync: hookState.create,
    isPending: false,
  }),
  useUpdateSpatialFeatureResource: () => ({
    mutateAsync: hookState.update,
    isPending: false,
  }),
  useRefreshSpatialFeatureResourceSchema: () => ({
    mutateAsync: hookState.refresh,
    isPending: false,
    variables: undefined,
  }),
  useDeleteSpatialFeatureResource: () => ({
    mutateAsync: hookState.remove,
    isPending: false,
  }),
  useSpatialFeaturePreview: () => ({
    data: { columns: resource.columns, rows: [{ objectid: 1 }], limit: 20, truncated: false },
    isFetching: false,
  }),
}));

import { SpatialFeatureResourceListDrawer } from './SpatialFeatureResourceListDrawer';

const dataSource: DataSource = {
  id: 'spatial-source-id',
  code: 'government_wfs',
  name: '政务空间服务',
  directoryId: null,
  purposes: ['SOURCE'],
  type: 'WFS',
  connectionKind: 'HTTP_API',
  enabled: true,
  description: null,
  connection: {
    kind: 'HTTP_API',
    configuration: {
      baseUrl: 'https://example.test/wfs',
      defaultHeaders: [],
      connectTimeoutMs: 5000,
      requestTimeoutMs: 30000,
      minimumRequestIntervalMs: 0,
      maxRetries: 0,
      authentication: { type: 'NONE' },
      signingSecretConfigured: false,
      signingPrivateKeyConfigured: false,
    },
  },
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
};

const renderDrawer = () => render(
  <SpatialFeatureResourceListDrawer
    dataSource={dataSource}
    open
    canCreate
    canUpdate
    canDelete
    canReadMetadata
    onClose={vi.fn()}
  />,
);

describe('SpatialFeatureResourceListDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('keeps the resource list compact and exposes every command through the row menu', async () => {
    const user = userEvent.setup();
    renderDrawer();

    expect(screen.getByText('已登记空间资源')).toBeInTheDocument();
    expect(screen.getByText('省级行政区划')).toBeInTheDocument();
    expect(screen.getByText('geo:province_boundary')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '管理空间要素资源 省级行政区划' }));

    expect(screen.getByRole('menuitem', { name: /预览属性$/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /修改$/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /刷新 Schema$/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /删除$/ })).toBeInTheDocument();
  });

  it('uses a discovery workspace for registration and submits the selected catalog entry', async () => {
    hookState.create.mockResolvedValue(resource);
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /发现并登记$/ }));

    expect(screen.getByText('服务目录')).toBeInTheDocument();
    expect(screen.getByText('登记配置')).toBeInTheDocument();
    expect(screen.getByText('尚未选择远程资源')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /选\s*择/ }));

    expect(screen.getByText('已选择远程资源')).toBeInTheDocument();
    expect(screen.getByLabelText('资源编码')).toHaveValue('geo_city_boundary');
    expect(screen.getByLabelText('资源名称')).toHaveValue('市级行政区划');
    expect(screen.getByLabelText('远程图层 / FeatureType 标识')).toHaveValue('geo:city_boundary');

    await user.click(screen.getByRole('button', { name: '登记资源' }));

    await waitFor(() => expect(hookState.create).toHaveBeenCalledWith({
      code: 'geo_city_boundary',
      name: '市级行政区划',
      outputEpsgCode: 4490,
      remoteIdentifier: 'geo:city_boundary',
    }));
  });

  it('restores the lightweight edit state and keeps enablement in the section header', async () => {
    hookState.update.mockResolvedValue(resource);
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: '管理空间要素资源 省级行政区划' }));
    await user.click(screen.getByRole('menuitem', { name: /修改$/ }));

    expect(screen.getByText('资源身份')).toBeInTheDocument();
    expect(screen.getByLabelText('资源名称')).toHaveValue('省级行政区划');
    expect(screen.getByLabelText('空间要素资源启用状态')).toBeChecked();
    expect(screen.getByText('保存后启用')).toBeInTheDocument();

    await user.clear(screen.getByLabelText('资源名称'));
    await user.type(screen.getByLabelText('资源名称'), '省级行政区划（验收）');
    await user.click(screen.getByRole('button', { name: '保存修改' }));

    await waitFor(() => expect(hookState.update).toHaveBeenCalledWith({
      resourceId: resource.id,
      request: { name: '省级行政区划（验收）', enabled: true },
    }));
  });
});
