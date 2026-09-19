import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { buildDataModelSearch, useDataModels } from '../../../model';
import { CanvasModelSelect } from './CanvasModelSelect';

const fixtures = vi.hoisted(() => {
  const published = {
    id: '1067be6d-0a7f-43f7-a48d-13737cff6850',
    code: 'order_model',
    name: '订单模型',
    directoryId: null,
    storageDataSourceId: '3ea16400-7c0a-4d23-9039-12c9393a4823',
    storageDataSourceName: '模型仓库',
    catalogName: 'warehouse',
    schemaName: 'public',
    physicalTableName: 'dwd_order',
    physicalTableMode: 'MANAGED',
    clickHouseOrderByColumns: [],
    status: 'PUBLISHED',
    schemaVersion: 2,
    description: null,
    createdAt: '2026-07-17T00:00:00Z',
    updatedAt: '2026-07-17T00:00:00Z',
  };
  const disabled = {
    ...published,
    id: '2fe70771-06df-4a23-9df6-eb086a9c623a',
    code: 'disabled_model',
    name: '已停用模型',
    status: 'DISABLED',
  };
  const external = {
    ...published,
    id: '8876bfe2-b58a-4d84-8b6f-d9eb29bf744c',
    code: 'external_model',
    name: '外部表模型',
    physicalTableMode: 'EXTERNAL',
  };
  return { published, disabled, external };
});

vi.mock('../../../model', () => ({
  buildDataModelSearch: vi.fn(({
    keyword,
    status,
    physicalTableModes,
  }: {
    keyword: string;
    status: string;
    physicalTableModes?: string[];
  }) => (
    `keyword:${keyword};status:${status};modes:${physicalTableModes?.join(',') ?? ''}`
  )),
  dataModelStatusLabels: { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' },
  physicalTableModeLabels: { MANAGED: '新建物理表', EXTERNAL: '绑定已有表' },
  useDataModel: vi.fn((id: string | undefined) => ({
    data: id === fixtures.disabled.id ? { model: fixtures.disabled, fields: [] }
      : id === fixtures.external.id ? { model: fixtures.external, fields: [] }
      : id === fixtures.published.id ? { model: fixtures.published, fields: [] }
        : undefined,
    isError: Boolean(id)
      && id !== fixtures.disabled.id
      && id !== fixtures.external.id
      && id !== fixtures.published.id,
    isFetching: false,
  })),
  useDataModels: vi.fn(() => ({
    data: { content: [fixtures.published], totalElements: 1 },
    isFetching: false,
  })),
}));

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('CanvasModelSelect', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('retains a selected disabled model while new choices remain published-only', () => {
    render(
      <CanvasModelSelect
        value={fixtures.disabled.id}
        placeholder="选择模型"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByRole('combobox').closest('.ant-select-content'))
      .toHaveAttribute('title', '已停用模型（disabled_model）');
    fireEvent.mouseDown(screen.getByRole('combobox'));
    expect(screen.getByText('已停用')).toBeInTheDocument();
    expect(screen.getByText('订单模型')).toBeInTheDocument();
    expect(screen.getByText('order_model · 模型仓库 · dwd_order')).toBeInTheDocument();
    expect(screen.queryByText(/public/)).not.toBeInTheDocument();
  });

  it('debounces server-side search and keeps the PUBLISHED filter', () => {
    vi.useFakeTimers();
    render(<CanvasModelSelect placeholder="选择模型" onChange={vi.fn()} />);

    const input = screen.getByRole('combobox');
    fireEvent.mouseDown(input);
    fireEvent.change(input, { target: { value: '订单' } });
    act(() => vi.advanceTimersByTime(300));

    expect(buildDataModelSearch).toHaveBeenLastCalledWith({ keyword: '订单', status: 'PUBLISHED' });
    expect(vi.mocked(useDataModels).mock.calls.at(-1)?.[0]).toEqual({
      search: 'keyword:订单;status:PUBLISHED;modes:',
      page: 0,
      size: 50,
      sort: 'code',
    });
  });

  it('requests only allowed physical table modes and retains an invalid selected model', () => {
    render(
      <CanvasModelSelect
        value={fixtures.external.id}
        placeholder="选择模型"
        physicalTableModes={['MANAGED']}
        onChange={vi.fn()}
      />,
    );

    expect(buildDataModelSearch).toHaveBeenLastCalledWith({
      keyword: '',
      status: 'PUBLISHED',
      physicalTableModes: ['MANAGED'],
    });
    expect(screen.getByRole('combobox').closest('.ant-select')).toHaveClass('ant-select-status-error');
    fireEvent.mouseDown(screen.getByRole('combobox'));
    expect(screen.getByText('外部表模型')).toBeInTheDocument();
    expect(screen.getByText('订单模型')).toBeInTheDocument();
  });
});
