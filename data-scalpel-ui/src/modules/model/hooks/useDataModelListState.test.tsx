import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataModel } from '../model/dataModel';
import { useDataModelListState } from './useDataModelListState';
import { useDataModels } from './useDataModels';

const routeMocks = vi.hoisted(() => ({
  params: new URLSearchParams('keyword=已应用'),
  setSearchParams: vi.fn(),
}));

const directoryMocks = vi.hoisted(() => ({
  tree: [{
    id: 'directory-root',
    scope: 'MODEL',
    parentId: null,
    name: '根目录',
    sortOrder: 0,
    description: null,
    directResourceCount: 0,
    resourceCount: 0,
    children: [{
      id: 'directory-child',
      scope: 'MODEL',
      parentId: 'directory-root',
      name: '子目录',
      sortOrder: 0,
      description: null,
      directResourceCount: 0,
      resourceCount: 0,
      children: [],
    }],
  }],
}));

const queryMocks = vi.hoisted(() => ({
  content: [] as DataModel[],
  refetch: vi.fn(),
}));

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useSearchParams: () => [routeMocks.params, routeMocks.setSearchParams] as const,
  };
});

vi.mock('../../directory', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../directory')>();
  return {
    ...actual,
    useDirectoryTree: vi.fn(() => ({
      data: directoryMocks.tree,
      isFetching: false,
    })),
  };
});

vi.mock('./useDataModels', () => ({
  useDataModels: vi.fn(() => ({
    data: { content: queryMocks.content, totalElements: queryMocks.content.length },
    isFetching: false,
    refetch: queryMocks.refetch,
  })),
}));

const model = (id: string, status: DataModel['status'] = 'DRAFT'): DataModel => ({
  id,
  code: `model_${id}`,
  name: `模型 ${id}`,
  directoryId: null,
  storageDataSourceId: 'storage-1',
  storageDataSourceName: '存储库',
  catalogName: 'demo',
  schemaName: 'public',
  physicalTableName: `model_${id}`,
  physicalTableMode: 'MANAGED',
  clickHouseOrderByColumns: [],
  status,
  schemaVersion: 1,
  description: null,
  createdAt: '2026-09-16T00:00:00Z',
  updatedAt: '2026-09-16T00:00:00Z',
});

describe('useDataModelListState', () => {
  beforeEach(() => {
    routeMocks.params = new URLSearchParams('keyword=已应用');
    routeMocks.setSearchParams.mockReset();
    queryMocks.content = [];
    queryMocks.refetch.mockReset();
    vi.mocked(useDataModels).mockClear();
  });

  afterEach(cleanup);

  it('switches directories with the applied filters instead of unsubmitted form values', () => {
    const { result } = renderHook(() => useDataModelListState(true));

    act(() => {
      result.current.filterForm.setFieldValue('keyword', '尚未提交');
      result.current.selectDirectory('directory-root');
    });

    const request = vi.mocked(useDataModels).mock.calls.at(-1)?.[0];
    expect(request).toMatchObject({ page: 0, size: 20, sort: '-updatedAt,code' });
    expect(request?.search).toContain('name:*"已应用"*');
    expect(request?.search).toContain('directoryId:"directory-root"');
    expect(request?.search).toContain('directoryId:"directory-child"');
    expect(request?.search).not.toContain('尚未提交');
  });

  it('retains selected model snapshots across pages and removes deselected rows', () => {
    const first = model('first');
    const second = model('second', 'PUBLISHED');
    queryMocks.content = [first];
    const { result } = renderHook(() => useDataModelListState(false));

    act(() => result.current.updateSelection([first.id], [first], { type: 'multiple' }));
    expect(result.current.selectedModels).toEqual([first]);

    act(() => {
      queryMocks.content = [second];
      result.current.changePage(1, 20);
    });
    act(() => result.current.updateSelection(
      [first.id, second.id],
      [second],
      { type: 'multiple' },
    ));

    expect(result.current.selectedModelIds).toEqual([first.id, second.id]);
    expect(result.current.selectedModels).toEqual([first, second]);
    expect(result.current.publishableSelectedModels).toEqual([first]);
    expect(result.current.publishedSelectedModels).toEqual([second]);

    act(() => result.current.updateSelection([second.id], [second], { type: 'multiple' }));
    expect(result.current.selectedModels).toEqual([second]);
  });
});
