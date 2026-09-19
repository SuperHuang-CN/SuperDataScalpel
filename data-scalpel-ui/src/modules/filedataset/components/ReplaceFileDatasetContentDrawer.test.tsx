import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { FileDataset, FileDatasetFile } from '../model/fileDataset';

const hookState = vi.hoisted(() => ({
  replace: vi.fn(),
}));

vi.mock('../hooks/useFileDatasets', () => ({
  useReplaceFileDatasetFile: () => ({
    mutateAsync: hookState.replace,
    isPending: false,
  }),
}));

import { ReplaceFileDatasetContentDrawer } from './ReplaceFileDatasetContentDrawer';

const fileDataset: FileDataset = {
  id: 'dataset-id',
  directoryId: null,
  name: '政务交换 CSV',
  type: 'CSV',
  parsingOptions: {
    kind: 'CSV',
    charset: 'UTF-8',
    fieldDelimiter: ',',
    recordDelimiter: 'AUTO',
    firstRowHeader: true,
  },
  fileCount: 1,
  tableCount: 2,
  readyTableCount: 2,
  parsingOptionsLocked: true,
  description: null,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-02T08:30:00Z',
};

const sourceFile: FileDatasetFile = {
  id: 'file-id',
  fileDatasetId: fileDataset.id,
  originalFileName: 'government_exchange_2026.csv',
  format: 'CSV',
  compression: 'NONE',
  contentType: 'text/csv',
  sizeBytes: 2048,
  status: 'READY',
  storageKind: 'SINGLE_OBJECT',
  materializedSizeBytes: null,
  materializedEntryCount: null,
  currentPreparationJobId: null,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-02T08:30:00Z',
};

describe('ReplaceFileDatasetContentDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('keeps the destructive effect visible and confirms the exact replacement file', async () => {
    hookState.replace.mockResolvedValue(undefined);
    const onClose = vi.fn();
    const user = userEvent.setup();
    const { container } = render(
      <ReplaceFileDatasetContentDrawer
        fileDataset={fileDataset}
        file={sourceFile}
        open
        onClose={onClose}
      />,
    );

    expect(screen.getByText('当前文件')).toBeInTheDocument();
    expect(screen.getByText(sourceFile.originalFileName)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('替换后不可恢复');
    expect(screen.getByRole('alert')).toHaveTextContent('旧表与 Schema 会被永久删除');
    expect(screen.getByRole('button', { name: '确认替换' })).toBeDisabled();

    const uploadInput = container.ownerDocument.querySelector<HTMLInputElement>('input[type="file"]');
    expect(uploadInput).not.toBeNull();
    const replacementFile = new File(['id,name\n1,政务数据'], 'government_exchange_2026_v2.csv', { type: 'text/csv' });
    await user.upload(uploadInput!, replacementFile);

    expect(screen.getByText(`已选择 ${replacementFile.name}`)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '确认替换' }));

    await waitFor(() => expect(hookState.replace).toHaveBeenCalledWith({
      datasetId: fileDataset.id,
      fileId: sourceFile.id,
      file: replacementFile,
    }));
    expect(onClose).toHaveBeenCalled();
  });
});
