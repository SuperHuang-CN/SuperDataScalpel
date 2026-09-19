import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { StandardDataServiceModelCandidate } from '../../model/dataService';

const candidate = vi.hoisted(() => ({
  id: 'model-1',
  code: 'user_model',
  name: '用户模型',
  status: 'PUBLISHED',
  directoryId: null,
  directoryName: null,
  warehouseLayerId: null,
  warehouseLayerCode: null,
  warehouseLayerName: null,
  storageDataSourceId: 'source-1',
  storageDataSourceCode: 'main_db',
  storageDataSourceName: '主数据源',
  catalogName: null,
  schemaName: 'public',
  physicalTableName: 'users',
  schemaVersion: 3,
  fieldCount: 8,
  selectable: true,
  unavailableReason: null,
  updatedAt: '2026-08-05T10:00:00Z',
} satisfies StandardDataServiceModelCandidate));
const candidatesHook = vi.hoisted(() => vi.fn());

vi.mock('../../../directory', () => ({
  DirectoryTreePanel: ({ showResourceCounts }: { showResourceCounts?: boolean }) => (
    <div data-testid="model-directory" data-show-counts={String(showResourceCounts)} />
  ),
  findDirectoryDescendantIds: () => [],
  useDirectoryTree: () => ({ data: [], isFetching: false }),
}));

vi.mock('../../../model', () => ({
  buildDataModelSearch: () => undefined,
  dataModelStatusLabels: { PUBLISHED: '已发布' },
  useDataModel: (id: string | undefined) => ({
    data: id ? {
      model: {
        id: 'model-1',
        code: 'user_model',
        name: '用户模型',
        status: 'PUBLISHED',
        schemaVersion: 3,
        storageDataSourceId: 'source-1',
      },
      fields: [{ id: 'field-1', primaryKey: true }, { id: 'field-2', primaryKey: false }],
    } : undefined,
    isFetching: false,
    isError: false,
  }),
  useModelWarehouseLayers: () => ({ data: { content: [] }, isFetching: false }),
}));

vi.mock('../../../serviceengine', () => ({
  useServiceEngineDataSourceRegistrations: () => ({
    data: { content: [{ dataSourceId: 'source-1', status: 'READY' }] },
    isFetching: false,
  }),
}));

vi.mock('../../hooks/useDataServices', () => ({
  useStandardDataServiceModelCandidates: candidatesHook,
}));

import { StandardModelPicker } from './StandardModelPicker';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('StandardModelPicker', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    candidatesHook.mockReset();
    candidatesHook.mockReturnValue({
      data: { content: [candidate], totalElements: 1 },
      isFetching: false,
      isError: false,
      refetch: vi.fn(),
    });
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  it('renders candidates immediately and selects a model directly from the table', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    render(
      <MemoryRouter>
        <StandardModelPicker
          serviceId="service-1"
          engineId="engine-1"
          readOnly={false}
          canViewModels
          canViewEngines
          onChange={onChange}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('用户模型')).toBeInTheDocument();
    expect(screen.getByTestId('model-directory')).toHaveAttribute('data-show-counts', 'false');
    expect(screen.queryByRole('button', { name: '选择模型' })).not.toBeInTheDocument();
    await user.click(screen.getByText('用户模型'));
    expect(onChange).toHaveBeenCalledWith('model-1');
  });

  it('keeps the selected model in the fixed summary even when selection is outside table state', () => {
    render(
      <MemoryRouter>
        <StandardModelPicker
          serviceId="service-1"
          engineId="engine-1"
          value="model-1"
          readOnly={false}
          canViewModels
          canViewEngines
          onChange={vi.fn()}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('Schema v3 · 2 个字段 · 1 个主键')).toBeInTheDocument();
    expect(screen.getByText('与当前 Engine 兼容')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /查看模型/ })).toBeInTheDocument();
  });

  it('shows only the current model summary when the definition is read-only', () => {
    render(
      <MemoryRouter>
        <StandardModelPicker
          serviceId="service-1"
          engineId="engine-1"
          value="model-1"
          readOnly
          canViewModels
          canViewEngines
          onChange={vi.fn()}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('当前服务定义为只读状态，仅展示已保存的发布模型。')).toBeInTheDocument();
    expect(screen.getByText('Schema v3 · 2 个字段 · 1 个主键')).toBeInTheDocument();
    expect(screen.queryByText('可选模型')).not.toBeInTheDocument();
    expect(candidatesHook).toHaveBeenLastCalledWith(
      'service-1',
      expect.any(Object),
      false,
      false,
    );
  });
});
