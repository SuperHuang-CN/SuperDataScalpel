import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataModel, DataModelField } from '../model/dataModel';

const mutateAsync = vi.fn();

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

vi.mock('../hooks/useDataModels', () => ({
  useDataModelPreview: () => ({
    data: undefined,
    error: null,
    isFetching: false,
    isPending: false,
    refetch: vi.fn(),
  }),
  useDataModelDataQuery: () => ({
    error: null,
    isPending: false,
    mutateAsync,
  }),
}));

import { DataModelPreviewPanel } from './DataModelPreviewPanel';

const model: DataModel = {
  id: 'model-id',
  code: 'user',
  name: '用户表',
  directoryId: null,
  storageDataSourceId: 'storage-id',
  storageDataSourceName: '开发存储',
  catalogName: 'datascalpel',
  schemaName: 'public',
  physicalTableName: 'user',
  physicalTableMode: 'MANAGED',
  clickHouseOrderByColumns: [],
  status: 'PUBLISHED',
  schemaVersion: 1,
  description: null,
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
};

const fields: DataModelField[] = [{
  id: 'field-id',
  modelId: model.id,
  code: 'id',
  name: 'Id',
  fieldType: 'LONG',
  length: null,
  precision: null,
  scale: null,
  nullable: false,
  primaryKey: true,
  sortOrder: 10,
  description: null,
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
}];

describe('DataModelPreviewPanel', () => {
  beforeEach(() => {
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
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({
      columns: [], rows: [], pageNo: 1, pageSize: 50, hasNext: false, totalCount: null, stableOrder: true,
    });
  });

  it('submits an equality filter value entered in the condition row', async () => {
    const user = userEvent.setup();
    render(<DataModelPreviewPanel model={model} fields={fields} />);

    await user.click(screen.getByText('条件查询'));
    await user.click(screen.getByRole('button', { name: /添加条件/ }));

    const selects = screen.getAllByRole('combobox');
    await user.click(selects[2]);
    await user.click(await screen.findByText('Id (id)'));
    await user.type(screen.getByPlaceholderText('输入条件值'), '1');
    await user.click(screen.getByRole('button', { name: /查询/ }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledWith({
      id: 'model-id',
      request: expect.objectContaining({
        filters: [{ field: 'id', operator: 'EQ', value: '1' }],
      }),
    }));
  });
});
