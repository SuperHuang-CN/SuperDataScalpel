import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FileDatasetParseJob } from '../model/fileDatasetParseJob';

const mocks = vi.hoisted(() => ({ jobs: vi.fn(), invalidate: vi.fn() }));
vi.mock('../hooks/useFileDatasets', () => ({ useFileDatasetParseJobs: (...args: unknown[]) => mocks.jobs(...args) }));
vi.mock('@tanstack/react-query', () => ({ useQueryClient: () => ({ invalidateQueries: mocks.invalidate }) }));
import { FileDatasetParseHistoryPanel } from './FileDatasetParseHistoryPanel';

const job = {
  id: 'job-1', fileDatasetId: 'dataset-1', sourceFileId: 'deleted-file', sourceFileName: 'broken.zip',
  type: 'FILE_PREPARATION', tableName: null, status: 'FAILED', attemptCount: 3, maxAttempts: 3,
  errorMessage: '归档中缺少 .gdb 目录', queuedAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:10Z',
} as FileDatasetParseJob;
beforeEach(() => {
  mocks.invalidate.mockReset();
  mocks.jobs.mockReset().mockReturnValue({ data: { content: [job], totalElements: 1 }, refetch: vi.fn() });
});
afterEach(cleanup);

describe('dataset parse history', () => {
  it('queries only the current dataset and shows errors without requiring the original file', async () => {
    render(<FileDatasetParseHistoryPanel datasetId="dataset-1" compact />);
    expect(mocks.jobs).toHaveBeenCalledWith(expect.objectContaining({ search: 'fileDatasetId:"dataset-1"', size: 3 }), true);
    expect(screen.getByText('broken.zip')).toBeInTheDocument();
    expect(screen.getByText('归档中缺少 .gdb 目录')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /展开|Expand/i }));
    expect(await screen.findByText('失败原因')).toBeInTheDocument();
    expect(screen.getByText('job-1')).toBeInTheDocument();
  });

  it('refreshes files, tables and resource counts after a job changes state', async () => {
    mocks.jobs.mockReturnValue({ data: { content: [{ ...job, status: 'RUNNING' }], totalElements: 1 }, refetch: vi.fn() });
    const { rerender } = render(<FileDatasetParseHistoryPanel datasetId="dataset-1" compact />);
    mocks.jobs.mockReturnValue({ data: { content: [job], totalElements: 1 }, refetch: vi.fn() });
    rerender(<FileDatasetParseHistoryPanel datasetId="dataset-1" compact />);
    await waitFor(() => expect(mocks.invalidate).toHaveBeenCalledWith({ queryKey: ['file-datasets', 'dataset-1'] }));
  });
});
