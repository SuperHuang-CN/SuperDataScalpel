import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataModel, DataModelField } from '../model/dataModel';

const refetch = vi.fn();

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const baseModel: DataModel = {
  id: 'model-id',
  code: 'spatial_asset',
  name: '空间资产',
  directoryId: null,
  storageDataSourceId: 'storage-id',
  storageDataSourceName: 'PostGIS',
  catalogName: 'warehouse',
  schemaName: 'public',
  physicalTableName: 'spatial_asset',
  physicalTableMode: 'MANAGED',
  clickHouseOrderByColumns: [],
  status: 'DRAFT',
  schemaVersion: 1,
  description: null,
  createdAt: '2026-07-24T00:00:00Z',
  updatedAt: '2026-07-24T00:00:00Z',
};

const geometryFields: DataModelField[] = [{
  id: 'shape-id',
  modelId: baseModel.id,
  code: 'shape',
  name: '空间位置',
  fieldType: 'GEOMETRY',
  length: null,
  precision: null,
  scale: null,
  geometry: {
    kind: 'POINT',
    crs: { authority: 'EPSG', code: 4326 },
    dimension: 'XY',
  },
  nullable: true,
  primaryKey: false,
  sortOrder: 10,
  description: null,
  createdAt: '2026-07-24T00:00:00Z',
  updatedAt: '2026-07-24T00:00:00Z',
}];

const scalarFields: DataModelField[] = [{
  id: 'order-id',
  modelId: baseModel.id,
  code: 'order_id',
  name: '订单ID',
  fieldType: 'LONG',
  length: null,
  precision: null,
  scale: null,
  nullable: false,
  primaryKey: true,
  sortOrder: 10,
  description: null,
  createdAt: '2026-07-24T00:00:00Z',
  updatedAt: '2026-07-24T00:00:00Z',
}];

let detailModel = baseModel;
let detailFields = geometryFields;
let dataSourceType: 'POSTGRESQL' | 'CLICKHOUSE' = 'POSTGRESQL';

vi.mock('../hooks/useDataModels', () => ({
  useDataModel: () => ({
    data: { model: detailModel, fields: detailFields },
    error: null,
    isFetching: false,
    refetch,
  }),
  usePhysicalTableInspection: () => ({
    data: { state: 'MATCHED' },
    error: null,
    isPending: false,
    refetch,
  }),
  usePlatformTypeCapabilities: () => ({
    data: [],
    isFetching: false,
  }),
  useUpdateDataModelFields: () => ({
    isPending: false,
    mutateAsync: vi.fn(),
  }),
  useCreatePhysicalTableChangePlan: () => ({
    isPending: false,
    mutateAsync: vi.fn(),
  }),
}));

vi.mock('../../system', () => ({
  useCurrentUser: () => ({
    data: { permissions: [] },
  }),
}));

vi.mock('../../datasource', () => ({
  useDataSource: () => ({
    data: { id: 'storage-id', type: dataSourceType },
  }),
}));

vi.mock('../../standard', () => ({
  isStandardDictionaryTypeFamilyCompatible: () => true,
  standardDictionaryValueTypeLabels: {},
  useStandardDictionaries: () => ({
    data: { content: [] },
    isFetching: false,
  }),
}));

vi.mock('./DataModelPhysicalChangeDrawer', () => ({
  DataModelPhysicalChangeDrawer: () => null,
}));

import { DataModelFieldsPanel } from './DataModelFieldsPanel';

describe('DataModelFieldsPanel field editing boundaries', () => {
  beforeEach(() => {
    detailModel = baseModel;
    detailFields = geometryFields;
    dataSourceType = 'POSTGRESQL';
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('allows constraint changes but keeps the spatial field definition locked', async () => {
    const user = userEvent.setup();
    render(<DataModelFieldsPanel model={detailModel} canUpdate />);

    expect(screen.getByText(/可修改可空性和非空间字段主键约束/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /新增字段/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /生成变更计划/ })).toBeDisabled();
    expect(screen.queryByRole('button', { name: /删除字段空间位置/ })).not.toBeInTheDocument();
    expect(screen.getByText(/Point · EPSG:4326 · XY/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '修改字段空间位置' }));
    expect(screen.getByLabelText('字段编码')).toBeDisabled();
    expect(screen.getByLabelText('字段类型')).toBeDisabled();
    expect(screen.getByRole('switch', { name: '允许为空' })).toBeEnabled();
    expect(screen.getByRole('switch', { name: '主键' })).toBeDisabled();

    await user.click(screen.getByRole('switch', { name: '允许为空' }));
    await user.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /生成变更计划/ })).toBeEnabled();
    });
  });

  it('treats a ClickHouse platform primary key as directly saved metadata', async () => {
    const user = userEvent.setup();
    dataSourceType = 'CLICKHOUSE';
    detailFields = [...scalarFields, ...geometryFields];
    render(<DataModelFieldsPanel model={detailModel} canUpdate />);

    await user.click(screen.getByRole('button', { name: '修改字段订单ID' }));
    expect(screen.getByText(/不生成 ClickHouse 约束/)).toBeInTheDocument();
    await user.click(screen.getByRole('switch', { name: '平台主键' }));
    await user.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /保存字段/ })).toBeEnabled();
    });
    expect(screen.queryByRole('button', { name: /生成变更计划/ })).not.toBeInTheDocument();
  });

  it('keeps nullable and primary key locked for an external table', async () => {
    const user = userEvent.setup();
    detailModel = { ...baseModel, physicalTableMode: 'EXTERNAL' };
    detailFields = scalarFields;
    render(<DataModelFieldsPanel model={detailModel} canUpdate />);

    await user.click(screen.getByRole('button', { name: '修改字段订单ID' }));

    expect(screen.getByRole('switch', { name: '允许为空' })).toBeDisabled();
    expect(screen.getByRole('switch', { name: '主键' })).toBeDisabled();
  });

  it('allows a disabled managed model to enter field editing', () => {
    detailModel = { ...baseModel, status: 'DISABLED' };
    detailFields = scalarFields;

    render(<DataModelFieldsPanel model={detailModel} canUpdate />);

    expect(screen.getByText(/模型已停用，可以修改字段/)).toBeInTheDocument();
    expect(screen.queryByText(/模型已发布，字段结构只读/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /新增字段/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /生成变更计划/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: '修改字段订单ID' })).toBeInTheDocument();
  });

  it('routes disabled metadata edits to direct save', async () => {
    const user = userEvent.setup();
    detailModel = { ...baseModel, status: 'DISABLED' };
    detailFields = scalarFields;
    render(<DataModelFieldsPanel model={detailModel} canUpdate />);

    await user.click(screen.getByRole('button', { name: '修改字段订单ID' }));
    const nameInput = screen.getByLabelText('字段名称');
    await user.clear(nameInput);
    await user.type(nameInput, '订单主键');
    await user.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /保存字段/ })).toBeEnabled();
    });
    expect(screen.queryByRole('button', { name: /生成变更计划/ })).not.toBeInTheDocument();
  });

  it('routes disabled structural edits to a physical change plan', async () => {
    const user = userEvent.setup();
    detailModel = { ...baseModel, status: 'DISABLED' };
    detailFields = scalarFields;
    render(<DataModelFieldsPanel model={detailModel} canUpdate />);

    await user.click(screen.getByRole('button', { name: '修改字段订单ID' }));
    const codeInput = screen.getByLabelText('字段编码');
    await user.clear(codeInput);
    await user.type(codeInput, 'business_order_id');
    await user.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /生成变更计划/ })).toBeEnabled();
    });
    expect(screen.queryByRole('button', { name: /保存字段/ })).not.toBeInTheDocument();
  });

  it('shows only the selected page of fields when using the top pagination controls', async () => {
    const user = userEvent.setup();
    detailFields = Array.from({ length: 21 }, (_, index) => ({
      ...scalarFields[0],
      id: `field-${index + 1}`,
      code: `field_${index + 1}`,
      name: `字段 ${index + 1}`,
      sortOrder: (index + 1) * 10,
    }));

    render(<DataModelFieldsPanel model={detailModel} canUpdate={false} />);

    expect(screen.getByText('字段 1')).toBeInTheDocument();
    expect(screen.queryByText('字段 21')).not.toBeInTheDocument();

    const nextPage = document.querySelector<HTMLButtonElement>('.detail-table-pagination .ant-pagination-next button');
    expect(nextPage).not.toBeNull();
    await user.click(nextPage!);

    expect(await screen.findByText('字段 21')).toBeInTheDocument();
    expect(screen.queryByText('字段 1')).not.toBeInTheDocument();
  });
});
