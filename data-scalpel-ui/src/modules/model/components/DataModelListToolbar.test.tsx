import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  MAX_BATCH_PUBLISH_COUNT,
  MAX_STATISTICS_REFRESH_COUNT,
  STATISTICS_REFRESH_CONCURRENCY,
  type DataModelListActions,
} from '../hooks/useDataModelListActions';
import type { DataModelListState } from '../hooks/useDataModelListState';
import { DataModelListToolbar } from './DataModelListToolbar';

const listState = (selectionCount: number): DataModelListState => ({
  selectedModelIds: Array.from({ length: selectionCount }, (_, index) => `model-${index}`),
  publishableSelectedModels: Array.from({ length: selectionCount }, (_, index) => ({ id: `model-${index}` })),
  selectedIncludesExternal: false,
  modelsQuery: { data: { totalElements: 72 }, refetch: vi.fn() },
} as unknown as DataModelListState);

const actions = (): DataModelListActions => ({
  batchPublishPending: false,
  batchRefreshingStatistics: false,
  refreshingModelIds: new Set<string>(),
  exportPending: false,
  confirmBatchPublish: vi.fn(),
  refreshSelectedStatistics: vi.fn(),
  exportModels: vi.fn(),
} as unknown as DataModelListActions);

describe('DataModelListToolbar', () => {
  afterEach(cleanup);

  it('enforces the statistics refresh limit while keeping an allowed batch publish available', () => {
    render(<DataModelListToolbar
      list={listState(MAX_STATISTICS_REFRESH_COUNT + 1)}
      actions={actions()}
      canPublish
      canCreate={false}
      createMenuItems={[]}
    />);

    expect(screen.getByRole('button', { name: /刷新统计/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /批量发布/ })).toBeEnabled();
    expect(STATISTICS_REFRESH_CONCURRENCY).toBe(3);
  });

  it('enforces the batch publish limit at fifty selected models', () => {
    render(<DataModelListToolbar
      list={listState(MAX_BATCH_PUBLISH_COUNT + 1)}
      actions={actions()}
      canPublish
      canCreate={false}
      createMenuItems={[]}
    />);

    expect(screen.getByRole('button', { name: /批量发布/ })).toBeDisabled();
  });
});
