import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataModel, DataModelField, DataModelPreview } from '../model/dataModel';

const mutateAsync = vi.fn();
let previewData: DataModelPreview | undefined;

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

vi.mock('../hooks/useDataModels', () => ({
  useDataModelPreview: () => ({
    data: previewData,
    error: null,
    isFetching: false,
    isPending: false,
    refetch: vi.fn(),
  }),
  useDataModelDataQuery: () => ({
    error: null,
    isPending: false,
    mutateAsync,
  }),
}));

import { DataModelPreviewPanel } from './DataModelPreviewPanel';
import { DataModelDataQueryPanel } from './DataModelDataQueryPanel';

const model: DataModel = {
  id: 'model-id',
  code: 'user',
  name: '用户表',
  directoryId: null,
  storageDataSourceId: 'storage-id',
  storageDataSourceName: '开发存储',
  catalogName: 'datascalpel',
  schemaName: 'public',
  physicalTableName: 'user',
  physicalTableMode: 'MANAGED',
  clickHouseOrderByColumns: [],
  status: 'PUBLISHED',
  schemaVersion: 1,
  description: null,
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
};

const fields: DataModelField[] = [{
  id: 'field-id',
  modelId: model.id,
  code: 'id',
  name: 'Id',
  fieldType: 'LONG',
  length: null,
  precision: null,
  scale: null,
  nullable: false,
  primaryKey: true,
  sortOrder: 10,
  description: null,
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
}];

describe('DataModelPreviewPanel', () => {
  afterEach(cleanup);

  beforeEach(() => {
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
    previewData = undefined;
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({
      columns: [], rows: [], pageNo: 1, pageSize: 20, hasNext: false, totalCount: null, stableOrder: true,
    });
  });

  it('loads the first condition-query page immediately with default settings', async () => {
    const user = userEvent.setup();
    render(<DataModelPreviewPanel model={model} fields={fields} />);

    await user.click(screen.getByText('条件查询'));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledWith({
      id: 'model-id',
      request: {
        pageNo: 1,
        pageSize: 20,
        conditionType: 'AND',
        columns: [],
        filters: [],
        orders: [],
        returnCount: false,
      },
    }));
  });

  it('submits an equality filter value entered in the condition row', async () => {
    const user = userEvent.setup();
    render(<DataModelPreviewPanel model={model} fields={fields} />);

    await user.click(screen.getByText('条件查询'));
    await user.click(screen.getByRole('button', { name: /编辑条件/ }));
    await user.click(screen.getByRole('button', { name: /添加条件/ }));

    const selects = within(document.querySelector('.model-data-query-editor') as HTMLElement).getAllByRole('combobox');
    await user.click(selects[2]);
    await user.click(await screen.findByText('Id (id)'));
    await user.type(screen.getByPlaceholderText('输入条件值'), '1');
    await user.click(screen.getByRole('button', { name: /应用并查询/ }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledWith({
      id: 'model-id',
      request: expect.objectContaining({
        filters: [{ field: 'id', operator: 'EQ', value: '1' }],
      }),
    }));
    await new Promise((resolve) => window.setTimeout(resolve, 20));
  }, 20_000);

  it('does not offer Geometry as a projection, filter or sort field', async () => {
    const user = userEvent.setup();
    const geometryField: DataModelField = {
      ...fields[0],
      id: 'shape-field-id',
      code: 'shape',
      name: '空间位置',
      fieldType: 'GEOMETRY',
      geometry: {
        kind: 'POINT',
        crs: { authority: 'EPSG', code: 4326 },
        dimension: 'XY',
      },
      nullable: true,
      primaryKey: false,
      sortOrder: 20,
    };
    render(<DataModelPreviewPanel model={model} fields={[...fields, geometryField]} />);

    await user.click(screen.getByText('条件查询'));
    await user.click(screen.getByRole('button', { name: /编辑条件/ }));
    await user.click(screen.getByRole('button', { name: /添加条件/ }));
    const selects = within(document.querySelector('.model-data-query-editor') as HTMLElement).getAllByRole('combobox');
    await user.click(selects[2]);

    expect(await screen.findByText('Id (id)')).toBeInTheDocument();
    expect(screen.queryByText('空间位置 (shape)')).not.toBeInTheDocument();
  });

  it('allows a manually expanded width above the automatic maximum and shares it between preview modes', async () => {
    previewData = {
      catalogName: null,
      schemaName: null,
      tableName: 'user',
      columns: [{ code: 'id', name: 'Id', fieldType: 'LONG' }],
      rows: [{ id: 1 }],
      limit: 50,
      truncated: false,
    };
    mutateAsync.mockResolvedValue({
      columns: previewData.columns,
      rows: previewData.rows,
      pageNo: 1,
      pageSize: 20,
      hasNext: false,
      totalCount: null,
      stableOrder: true,
    });
    const user = userEvent.setup();
    render(<DataModelPreviewPanel model={model} fields={fields} />);

    expect(document.querySelector('.model-preview-resizable-table')).toBeInTheDocument();
    const quickHandle = screen.getByRole('separator', { name: '调整字段 Id 的列宽' });
    const automaticWidth = Number(quickHandle.getAttribute('aria-valuenow'));
    fireEvent.pointerDown(quickHandle, { button: 0, pointerId: 1, clientX: 100 });
    fireEvent.pointerMove(quickHandle, { pointerId: 1, clientX: 500 });
    fireEvent.pointerUp(quickHandle, { pointerId: 1, clientX: 500 });
    const manualWidth = Number(quickHandle.getAttribute('aria-valuenow'));
    expect(manualWidth).toBeGreaterThan(320);

    await user.click(screen.getByText('条件查询'));
    const queryHandle = await screen.findByRole('separator', { name: '调整字段 Id 的列宽' });
    expect(Number(queryHandle.getAttribute('aria-valuenow'))).toBe(manualWidth);

    fireEvent.doubleClick(queryHandle);
    expect(Number(queryHandle.getAttribute('aria-valuenow'))).toBe(automaticWidth);
    fireEvent.keyDown(queryHandle, { key: 'ArrowLeft', shiftKey: true });
    expect(Number(queryHandle.getAttribute('aria-valuenow'))).toBe(80);
    fireEvent.keyDown(queryHandle, { key: 'ArrowRight', shiftKey: true });
    expect(Number(queryHandle.getAttribute('aria-valuenow'))).toBe(112);
  });

  it('does not recalculate automatic widths when condition-query pagination moves past page one', async () => {
    mutateAsync
      .mockResolvedValueOnce({
        columns: [{ code: 'id', name: 'Id', fieldType: 'LONG' }],
        rows: [{ id: 1 }],
        pageNo: 1,
        pageSize: 20,
        hasNext: true,
        totalCount: null,
        stableOrder: true,
      })
      .mockResolvedValueOnce({
        columns: [{ code: 'id', name: 'Id', fieldType: 'LONG' }],
        rows: [{ id: 'x'.repeat(200) }],
        pageNo: 2,
        pageSize: 20,
        hasNext: false,
        totalCount: null,
        stableOrder: true,
      });
    const user = userEvent.setup();
    render(<DataModelPreviewPanel model={model} fields={fields} />);

    await user.click(screen.getByText('条件查询'));
    const firstPageHandle = await screen.findByRole('separator', { name: '调整字段 Id 的列宽' });
    const firstPageWidth = Number(firstPageHandle.getAttribute('aria-valuenow'));

    await user.click(screen.getByRole('button', { name: '下一页' }));
    await waitFor(() => expect(screen.getByText('第 2 页')).toBeInTheDocument());
    expect(Number(screen.getByRole('separator', { name: '调整字段 Id 的列宽' }).getAttribute('aria-valuenow')))
      .toBe(firstPageWidth);
  });

  it('keeps the shared data-query table fixed-width and non-resizable when sizing is not enabled', async () => {
    const query = vi.fn().mockResolvedValue({
      columns: [{ code: 'id', name: 'Id', fieldType: 'LONG' }],
      rows: [{ id: 1 }],
      pageNo: 1,
      pageSize: 20,
      hasNext: false,
      totalCount: null,
      stableOrder: true,
    });
    render(<DataModelDataQueryPanel fields={fields} query={query} />);

    await waitFor(() => expect(query).toHaveBeenCalled());

    expect(screen.queryByRole('separator', { name: '调整字段 Id 的列宽' })).not.toBeInTheDocument();
    expect(document.querySelector('.model-preview-resizable-table')).not.toBeInTheDocument();
  });
});
