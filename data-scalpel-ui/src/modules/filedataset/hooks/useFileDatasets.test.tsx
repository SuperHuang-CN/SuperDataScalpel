import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as fileDatasetApi from '../api/fileDatasetApi';
import { useFileDatasetFiles } from './useFileDatasets';

describe('useFileDatasetFiles', () => {
  afterEach(() => vi.restoreAllMocks());

  it('sorts physical files only by fields supported by FileDatasetFile', async () => {
    const fetchFiles = vi.spyOn(fileDatasetApi, 'fetchFileDatasetFiles').mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 200,
    });
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    renderHook(() => useFileDatasetFiles('dataset-1', true), { wrapper });

    await waitFor(() => expect(fetchFiles).toHaveBeenCalledWith('dataset-1', {
      page: 0,
      size: 200,
      sort: 'createdAt,originalFileName',
    }));
    queryClient.clear();
  });
});
