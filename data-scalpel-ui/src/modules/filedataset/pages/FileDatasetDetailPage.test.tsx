import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import type { FileDataset, FileDatasetTable } from '../model/fileDataset';

const hooks = vi.hoisted(() => ({
  detail: vi.fn(),
  tables: vi.fn(),
  remove: vi.fn(),
}));

vi.mock('../hooks/useFileDatasets', () => ({
  useFileDataset: (...args: unknown[]) => hooks.detail(...args),
  useFileDatasetTables: (...args: unknown[]) => hooks.tables(...args),
  useDeleteFileDataset: () => ({ isPending: false, mutateAsync: hooks.remove }),
}));

vi.mock('../../directory', () => ({
  useDirectoryTree: () => ({ data: [], isFetching: false }),
}));

vi.mock('../../system', () => ({
  useCurrentUser: () => ({
    data: { permissions: ['filedataset.view', 'filedataset.update', 'filedataset.delete'] },
  }),
}));

vi.mock('../components/FileDatasetDrawer', () => ({
  FileDatasetDrawer: () => null,
}));

vi.mock('../components/FileDatasetOverviewPanel', () => ({
  FileDatasetOverviewPanel: () => <div>概览面板</div>,
}));

vi.mock('../components/FileDatasetFilesPanel', () => ({
  FileDatasetFilesPanel: () => <div>文件面板</div>,
}));

vi.mock('../components/FileDatasetTablesPanel', () => ({
  FileDatasetTablesPanel: ({ selectedTableId }: { selectedTableId?: string }) => (
    <div>数据表面板：{selectedTableId ?? 'none'}</div>
  ),
}));

import { FileDatasetDetailPage } from './FileDatasetDetailPage';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const dataset: FileDataset = {
  id: 'dataset-1',
  directoryId: null,
  name: '年度统计',
  type: 'EXCEL',
  parsingOptions: { kind: 'SPREADSHEET', headerRowIndex: 0, dataStartRowIndex: 1 },
  fileCount: 1,
  tableCount: 2,
  readyTableCount: 2,
  parsingOptionsLocked: true,
  description: null,
  createdAt: '2026-07-20T00:00:00Z',
  updatedAt: '2026-07-20T00:00:00Z',
};

const table = (id: string, name: string): FileDatasetTable => ({
  id,
  fileDatasetId: dataset.id,
  code: id,
  name,
  parseStatus: 'READY',
  sourceCount: 1,
  totalRowCount: 3,
  currentLoadJobId: null,
  sampledRecordCount: 3,
  truncated: false,
  previewSupported: true,
  sourceMetadata: {},
  createdAt: '2026-07-20T00:00:00Z',
  updatedAt: '2026-07-20T00:00:00Z',
});

const LocationProbe = () => {
  const location = useLocation();
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
};

const renderPage = (entry: string) => render(
  <MemoryRouter initialEntries={[entry]}>
    <Routes>
      <Route path="/file-dataset/:id" element={<><FileDatasetDetailPage /><LocationProbe /></>} />
    </Routes>
  </MemoryRouter>,
);

describe('FileDatasetDetailPage', () => {
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
    hooks.detail.mockReset().mockReturnValue({
      data: dataset, isPending: false, isFetching: false, error: null, refetch: vi.fn(),
    });
    hooks.tables.mockReset().mockReturnValue({
      data: {
        content: [table('table-1', 'Sheet A'), table('table-2', 'Sheet B')],
        page: 0, size: 500, totalElements: 2, totalPages: 1, first: true, last: true,
      },
      isPending: false, isFetching: false, isError: false, isRefetchError: false, refetch: vi.fn(),
    });
    hooks.remove.mockReset();
  });

  afterEach(() => cleanup());

  it('restores the active tab from the URL and navigates to the logical tables tab', async () => {
    const user = userEvent.setup();
    renderPage('/file-dataset/dataset-1?tab=files');

    expect(await screen.findByText('文件面板')).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: '数据表 2' }));

    expect(await screen.findByText('数据表面板：table-1')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/file-dataset/dataset-1?tab=tables&tableId=table-1');
  });

  it('replaces a stale table deep link with the first available logical table', async () => {
    renderPage('/file-dataset/dataset-1?tab=tables&tableId=missing');

    expect(await screen.findByText('数据表面板：table-1')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('location'))
      .toHaveTextContent('/file-dataset/dataset-1?tab=tables&tableId=table-1'));
  });
});
