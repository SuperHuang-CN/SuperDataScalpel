import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FileDataset, FileDatasetParseStatus, FileDatasetTable } from '../model/fileDataset';

const hooks = vi.hoisted(() => ({
  schema: vi.fn(),
  preview: vi.fn(),
  sources: vi.fn(),
  append: vi.fn(),
  replaceData: vi.fn(),
  replaceSource: vi.fn(),
  deleteSource: vi.fn(),
  download: vi.fn(),
  update: vi.fn(),
}));

vi.mock('../hooks/useFileDatasets', () => ({
  useFileDatasetSchema: (...args: unknown[]) => hooks.schema(...args),
  useFileDatasetPreview: (...args: unknown[]) => hooks.preview(...args),
  useFileDatasetTableSources: (...args: unknown[]) => hooks.sources(...args),
  useAppendFileDatasetTable: () => ({ isPending: false, mutateAsync: hooks.append }),
  useReplaceFileDatasetTableData: () => ({ isPending: false, mutateAsync: hooks.replaceData }),
  useReplaceFileDatasetTableSource: () => ({ isPending: false, mutateAsync: hooks.replaceSource }),
  useDeleteFileDatasetTableSource: () => ({ isPending: false, mutateAsync: hooks.deleteSource }),
  useDownloadFileDatasetFile: () => ({ isPending: false, mutateAsync: hooks.download }),
  useUpdateFileDatasetTable: () => ({ isPending: false, mutateAsync: hooks.update }),
}));

import { FileDatasetTableResultPanel } from './FileDatasetTableResultPanel';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const dataset: FileDataset = {
  id: 'dataset-1',
  directoryId: null,
  name: '订单数据',
  type: 'CSV',
  parsingOptions: {
    kind: 'CSV', charset: 'UTF-8', fieldDelimiter: ',', recordDelimiter: 'AUTO', firstRowHeader: true,
  },
  fileCount: 1,
  tableCount: 1,
  readyTableCount: 0,
  parsingOptionsLocked: true,
  description: null,
  createdAt: '2026-07-19T00:00:00Z',
  updatedAt: '2026-07-19T00:00:00Z',
};

const table = (parseStatus: FileDatasetParseStatus): FileDatasetTable => ({
  id: 'table-1',
  fileDatasetId: dataset.id,
  code: 'orders',
  name: '订单表',
  parseStatus,
  sourceCount: parseStatus === 'READY' || parseStatus === 'SCHEMA_READY' ? 1 : 0,
  totalRowCount: parseStatus === 'READY' || parseStatus === 'SCHEMA_READY' ? 12 : 0,
  currentLoadJobId: parseStatus === 'QUEUED' || parseStatus === 'PARSING' ? 'job-1' : null,
  sampledRecordCount: parseStatus === 'READY' ? 12 : 0,
  truncated: false,
  previewSupported: parseStatus !== 'SCHEMA_READY',
  sourceMetadata: {},
  createdAt: '2026-07-19T00:00:00Z',
  updatedAt: '2026-07-19T00:00:00Z',
});

describe('FileDatasetTableResultPanel', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false, media: query, onchange: null,
        addListener: vi.fn(), removeListener: vi.fn(),
        addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
      })),
    });
    hooks.schema.mockReset().mockReturnValue({
      data: undefined, isError: false, isFetching: false, refetch: vi.fn(),
    });
    hooks.preview.mockReset().mockReturnValue({
      data: undefined, isError: false, isFetching: false, refetch: vi.fn(),
    });
    hooks.sources.mockReset().mockReturnValue({
      data: [], isError: false, isFetching: false, refetch: vi.fn(),
    });
    hooks.append.mockReset();
    hooks.replaceData.mockReset();
    hooks.replaceSource.mockReset();
    hooks.deleteSource.mockReset();
    hooks.download.mockReset();
    hooks.update.mockReset();
  });

  afterEach(() => cleanup());

  it('shows the initial queued state and keeps result queries idle', async () => {
    render(<FileDatasetTableResultPanel dataset={dataset} table={table('QUEUED')} canUpdate />);

    expect(await screen.findByText('初始来源正在后台解析')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /开始解析/ })).not.toBeInTheDocument();
    expect(hooks.schema).toHaveBeenCalledWith(dataset.id, 'table-1', false);
    expect(hooks.preview).toHaveBeenCalledWith(dataset.id, 'table-1', false);
    expect(hooks.sources).toHaveBeenCalledWith(dataset.id, 'table-1', true);
  });

  it('shows only effective sources without failure or retry state', async () => {
    const user = userEvent.setup();
    const readyTable = table('READY');
    hooks.sources.mockReturnValue({
      data: [{
        id: 'source-2',
        tableId: readyTable.id,
        sourceFileId: 'file-2',
        sourceName: 'orders-2026.csv',
        sourceKey: 'orders-2026.csv',
        sourceOrder: 0,
        rowCount: 12,
        schemaFingerprint: 'fingerprint',
        activatedAt: '2026-07-20T00:00:00Z',
        createdAt: '2026-07-20T00:00:00Z',
        updatedAt: '2026-07-20T00:00:00Z',
      }],
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    });
    render(<FileDatasetTableResultPanel dataset={dataset} table={readyTable} canUpdate />);

    await user.click(await screen.findByText('数据来源 1'));
    expect(await screen.findByText('orders-2026.csv')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '重试来源orders-2026.csv' })).not.toBeInTheDocument();
  });

  it('loads table-owned schema and preview only after the table is ready', async () => {
    const fields = [{
      name: 'id',
      sortOrder: 0,
      fieldType: 'LONG' as const,
      length: null,
      precision: null,
      scale: null,
      nullable: false,
    }];
    hooks.schema.mockReturnValue({
      data: { tableId: 'table-1', fields }, isError: false, isFetching: false, refetch: vi.fn(),
    });
    hooks.preview.mockReturnValue({
      data: { fields, rows: [[1]], limit: 50, truncated: false },
      isError: false, isFetching: false, refetch: vi.fn(),
    });
    render(<FileDatasetTableResultPanel dataset={dataset} table={table('READY')} canUpdate />);

    expect(await screen.findByText('Schema 1')).toBeInTheDocument();
    expect(screen.getAllByText('12 条')).toHaveLength(2);
    expect(hooks.schema).toHaveBeenCalledWith(dataset.id, 'table-1', true);
    expect(hooks.preview).toHaveBeenCalledWith(dataset.id, 'table-1', true);
    expect(screen.getByRole('button', { name: /追加数据/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /全量覆盖/ })).toBeEnabled();
  });

  it('loads schema without requesting preview for a schema-only GDB layer', async () => {
    const fields = [
      { name: 'OBJECTID', sortOrder: 0, fieldType: 'LONG' as const, length: null, precision: null, scale: null, nullable: false },
      { name: 'Shape', sortOrder: 1, fieldType: 'STRING' as const, length: null, precision: null, scale: null, nullable: true },
    ];
    hooks.schema.mockReturnValue({
      data: { tableId: 'table-1', fields }, isError: false, isFetching: false, refetch: vi.fn(),
    });
    render(<FileDatasetTableResultPanel dataset={{ ...dataset, type: 'GDB', parsingOptions: { kind: 'GDB' } }} table={table('SCHEMA_READY')} canUpdate />);

    expect(await screen.findByText('当前表仅支持 Schema')).toBeInTheDocument();
    expect(screen.getByText('Schema 2')).toBeInTheDocument();
    expect(screen.queryByText(/数据预览/)).not.toBeInTheDocument();
    expect(hooks.schema).toHaveBeenCalledWith(dataset.id, 'table-1', true);
    expect(hooks.preview).toHaveBeenCalledWith(dataset.id, 'table-1', false);
    expect(screen.queryByRole('button', { name: '追加数据' })).not.toBeInTheDocument();
  });

  it('shows the persisted SHP preview safety reason while keeping schema available', async () => {
    const fields = [
      { name: 'name', sortOrder: 0, fieldType: 'STRING' as const, length: null, precision: null, scale: null, nullable: true },
      { name: '_geometry', sortOrder: 1, fieldType: 'STRING' as const, length: null, precision: null, scale: null, nullable: true },
    ];
    hooks.schema.mockReturnValue({
      data: { tableId: 'table-1', fields }, isError: false, isFetching: false, refetch: vi.fn(),
    });
    const schemaOnlyTable = {
      ...table('SCHEMA_READY'),
      sourceMetadata: {
        previewUnavailableReason: '空间几何超过预览安全上限，仅保留 Schema',
        shapeType: 'POLYGON_Z',
        dbfCharset: 'GB18030',
        recordCount: 18,
        geometryField: '_geometry',
        extent: { xMin: 100, yMin: 20, xMax: 120, yMax: 40 },
        spatialReference: { wkt: 'GEOGCS["fixture"]' },
      },
    };

    render(<FileDatasetTableResultPanel
      dataset={{
        ...dataset,
        type: 'SHP',
        parsingOptions: { kind: 'SHP', dbfFallbackCharset: 'GB18030' },
      }}
      table={schemaOnlyTable}
      canUpdate
    />);

    expect(await screen.findByText('当前表仅支持 Schema')).toBeInTheDocument();
    expect(screen.getByText('空间几何超过预览安全上限，仅保留 Schema')).toBeInTheDocument();
    expect(screen.getByText('Schema 2')).toBeInTheDocument();
    expect(screen.queryByText(/数据预览/)).not.toBeInTheDocument();
    expect(hooks.preview).toHaveBeenCalledWith(dataset.id, 'table-1', false);
  });
});
