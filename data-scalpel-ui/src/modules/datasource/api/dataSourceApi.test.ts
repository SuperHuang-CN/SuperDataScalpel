import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchDataSources } from './dataSourceApi';
import { requestJson } from '../../../shared/api/http';

vi.mock('../../../shared/api/http', () => ({ requestJson: vi.fn().mockResolvedValue({ content: [] }) }));
beforeEach(() => vi.clearAllMocks());

describe('data source model candidate query', () => {
  it('sends the model existence filter alongside normal search and pagination', async () => {
    await fetchDataSources({ hasPublishedModels: true, page: 1, size: 20, search: 'storageEnabled:"true"' });
    const url = new URL(String(vi.mocked(requestJson).mock.calls[0][0]), 'https://local.test');
    expect(url.searchParams.get('hasPublishedModels')).toBe('true');
    expect(url.searchParams.get('search')).toBe('storageEnabled:"true"');
    expect(url.searchParams.get('page')).toBe('1');
  });
  it('does not change ordinary data source queries', async () => {
    await fetchDataSources({ page: 0, size: 20 });
    expect(vi.mocked(requestJson).mock.calls[0][0]).not.toContain('hasPublishedModels');
  });
});
