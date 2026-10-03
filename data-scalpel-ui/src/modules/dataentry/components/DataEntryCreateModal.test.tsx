import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import type { SearchRequest } from '../../../shared/search';

const state = vi.hoisted(() => ({ request: {} as SearchRequest }));
vi.mock('../hooks/useDataEntry', () => ({
  useDataEntryCandidateFilters: () => ({ data: { layers: [{ id: 'layer-1', name: '自定义分层' }], storages: [{ id: 'store-1', name: '业务存储' }], hasUnlayered: true } }),
  useDataEntryCandidatePage: (request: SearchRequest) => {
    state.request = request;
    return { data: { totalElements: 21, content: request.search !== 'physicalTableMode:"MANAGED"' ? [] : [{
      modelId: 'model-1', modelName: '待配置模型', modelCode: 'pending_model', modelStatus: 'DRAFT', schemaVersion: 1,
      warehouseLayerId: 'layer-1', warehouseLayerName: '自定义分层', storageDataSourceId: 'store-1', storageDataSourceName: '业务存储',
      physicalTableMode: 'MANAGED', catalogName: null, schemaName: 'public', physicalTableName: 'existing_table',
      knownEligible: false, issues: [{ code: 'TARGET_MODEL_NOT_PUBLISHED', message: '目标模型不是已发布状态' }, { code: 'TARGET_PRIMARY_KEY_MISSING', message: '目标模型没有业务主键字段' }],
    }] } };
  },
}));

import { DataEntryCreateModal } from './DataEntryCreateModal';
afterEach(cleanup);

it('applies combined filters on query and preserves a selected ineligible draft outside the result', async () => {
  const user = userEvent.setup();
  const create = vi.fn().mockResolvedValue(undefined);
  render(<DataEntryCreateModal loading={false} onCreate={create} onClose={vi.fn()} />);
  await user.click(screen.getByRole('radio', { name: '选择模型 待配置模型' }));
  expect(screen.getByText('发布条件待完善')).toBeInTheDocument();
  await user.click(screen.getByRole('combobox', { name: '数据分层' }));
  await user.click(screen.getAllByText('自定义分层').at(-1)!);
  await user.click(screen.getByRole('combobox', { name: '绑定数据源' }));
  await user.click(screen.getAllByText('业务存储').at(-1)!);
  await user.type(screen.getByRole('textbox', { name: '模型名称或编码' }), '模型');
  expect(state.request.search).toBe('physicalTableMode:"MANAGED"');
  await user.click(screen.getByRole('button', { name: '查 询' }));
  await waitFor(() => expect(state.request.search).toContain('warehouseLayerId:"layer-1"'));
  expect(state.request.search).toContain('storageDataSourceId:"store-1"');
  expect(state.request.search).toContain('physicalTableMode:"MANAGED"');
  expect(state.request.search).toContain('name:*"模型"* OR code:*"模型"*');
  expect(state.request.page).toBe(0);
  await user.click(screen.getByRole('button', { name: '创建草稿' }));
  expect(create).toHaveBeenCalledWith('model-1');
});

it('offers no unsupported mode selector and preserves repairable model issues and draft creation', async () => {
  const user = userEvent.setup();
  const create = vi.fn().mockResolvedValue(undefined);
  render(<DataEntryCreateModal loading={false} onCreate={create} onClose={vi.fn()} />);
  expect(screen.queryByRole('combobox', { name: '模型模式' })).not.toBeInTheDocument();
  expect(state.request.search).toBe('physicalTableMode:"MANAGED"');
  expect(screen.getByText('public / existing_table')).toBeInTheDocument();
  expect(screen.getByText('发布条件待完善')).toBeInTheDocument();
  expect(screen.queryByText('发布前需处理')).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '待配置模型的填报检查说明' }));
  expect(await screen.findByText('目标模型不是已发布状态')).toBeInTheDocument();
  expect(screen.getByText('目标模型没有业务主键字段')).toBeInTheDocument();
  await user.click(screen.getByRole('radio', { name: '选择模型 待配置模型' }));
  expect(screen.getByText('将创建草稿，发布前仍需完善模型条件并通过完整检查。')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '创建草稿' }));
  expect(create).toHaveBeenCalledWith('model-1');
});
