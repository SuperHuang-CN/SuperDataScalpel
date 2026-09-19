import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FileDataset, FileDatasetFile, FileDatasetType } from '../model/fileDataset';

const hooks = vi.hoisted(() => ({
  files: vi.fn(),
  upload: vi.fn(),
  remove: vi.fn(),
  download: vi.fn(),
}));

vi.mock('../hooks/useFileDatasets', () => ({
  useFileDatasetFiles: (...args: unknown[]) => hooks.files(...args),
  useUploadFileDatasetFiles: () => ({ isPending: false, mutateAsync: hooks.upload }),
  useDeleteFileDatasetFile: () => ({ isPending: false, mutateAsync: hooks.remove }),
  useDownloadFileDatasetFile: () => ({ isPending: false, mutateAsync: hooks.download }),
}));

vi.mock('./ReplaceFileDatasetContentDrawer', () => ({
  ReplaceFileDatasetContentDrawer: () => null,
}));

import { FileDatasetFilesPanel } from './FileDatasetFilesPanel';

const dataset = (type: FileDatasetType): FileDataset => ({
  id: `dataset-${type.toLowerCase()}`,
  directoryId: null,
  name: `${type} 数据集`,
  type,
  parsingOptions: type === 'SHP'
    ? { kind: 'SHP', dbfFallbackCharset: 'GB18030' }
    : { kind: 'CSV', charset: 'UTF-8', fieldDelimiter: ',', recordDelimiter: 'AUTO', firstRowHeader: true },
  fileCount: 1,
  tableCount: 1,
  readyTableCount: 1,
  parsingOptionsLocked: true,
  description: null,
  createdAt: '2026-09-19T00:00:00Z',
  updatedAt: '2026-09-19T00:00:00Z',
});

const file = (type: FileDatasetType): FileDatasetFile => ({
  id: `file-${type.toLowerCase()}`,
  fileDatasetId: `dataset-${type.toLowerCase()}`,
  originalFileName: type === 'SHP' ? 'roads.zip' : 'roads.csv',
  format: type === 'SHP' ? 'SHP' : 'CSV',
  compression: type === 'SHP' ? 'ZIP' : 'NONE',
  contentType: type === 'SHP' ? 'application/zip' : 'text/csv',
  sizeBytes: 1024,
  status: 'READY',
  storageKind: type === 'SHP' ? 'SHAPEFILE_COMPONENT_SET' : 'SINGLE_OBJECT',
  materializedSizeBytes: type === 'SHP' ? 2048 : null,
  materializedEntryCount: type === 'SHP' ? 5 : null,
  currentPreparationJobId: null,
  createdAt: '2026-09-19T00:00:00Z',
  updatedAt: '2026-09-19T00:00:00Z',
});

describe('FileDatasetFilesPanel', () => {
  beforeEach(() => {
    hooks.upload.mockReset();
    hooks.remove.mockReset().mockResolvedValue(undefined);
    hooks.download.mockReset();
  });

  afterEach(() => cleanup());

  it.each(['CSV', 'SHP'] as const)('offers file deletion for %s datasets', async (type) => {
    const sourceFile = file(type);
    hooks.files.mockReset().mockReturnValue({
      data: { content: [sourceFile], totalElements: 1, totalPages: 1, page: 0, size: 20 },
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    });
    const user = userEvent.setup();
    render(
      <FileDatasetFilesPanel dataset={dataset(type)} canUpdate onRefreshTables={vi.fn()} />,
    );

    await user.click(await screen.findByLabelText(`${sourceFile.originalFileName}更多操作`));
    expect(await screen.findByText('删除文件')).toBeInTheDocument();
  });

  it('explains cascading table deletion and submits the selected file', async () => {
    const sourceFile = file('CSV');
    hooks.files.mockReset().mockReturnValue({
      data: { content: [sourceFile], totalElements: 1, totalPages: 1, page: 0, size: 20 },
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    });
    const onRefreshTables = vi.fn();
    const user = userEvent.setup();
    render(
      <FileDatasetFilesPanel
        dataset={dataset('CSV')}
        canUpdate
        onRefreshTables={onRefreshTables}
      />,
    );

    await user.click(await screen.findByLabelText(`${sourceFile.originalFileName}更多操作`));
    await user.click(await screen.findByText('删除文件'));
    expect(await screen.findByText(/失去最后来源的数据表也会被永久删除/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /删\s*除/ }));

    await waitFor(() => expect(hooks.remove).toHaveBeenCalledWith({
      datasetId: sourceFile.fileDatasetId,
      fileId: sourceFile.id,
    }));
    expect(onRefreshTables).toHaveBeenCalled();
  });
});
