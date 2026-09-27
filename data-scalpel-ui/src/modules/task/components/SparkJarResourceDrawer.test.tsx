import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SparkJarResourceDrawer } from './SparkJarResourceDrawer';

const state = vi.hoisted(() => ({
  source: { id: 'pg', name: 'PG 存储', type: 'POSTGRESQL', connectionKind: 'JDBC', enabled: true, purposes: ['SOURCE', 'STORAGE', 'DISTRIBUTION'] },
  tables: vi.fn(),
  sources: vi.fn(),
  modelStatus: 'PUBLISHED',
  sourceItems: [] as { id: string; name: string; type: string; connectionKind: string; enabled: boolean; purposes: string[] }[],
}));
vi.mock('../../model', () => ({
  buildDataModelSearch: () => '',
  useDataModel: (id: string | undefined) => ({ data: id ? { model: { storageDataSourceId: 'pg', status: state.modelStatus } } : undefined, isError: false, isSuccess: Boolean(id) }),
  useDataModels: () => ({ data: { content: [{ id: 'model', code: 'assets', name: '资产' }] } }),
}));
vi.mock('../../datasource', () => ({
  buildDataSourceSearch: (filters: unknown) => JSON.stringify(filters),
  dataSourcePurposeLabels: { SOURCE: '数据源', STORAGE: '数据存储', DISTRIBUTION: '数据分发' },
  jdbcTableIdentifierDisplayName: (table: { table: string }) => table.table,
  jdbcTableIdentifierKey: (table: { table: string }) => table.table,
  useDataSource: (id: string | undefined) => ({ data: id ? state.source : undefined, isSuccess: Boolean(id) }),
  useDataSources: (request: unknown) => {
    state.sources(request);
    return { data: { content: state.sourceItems } };
  },
  useDataSourceTables: (...args: unknown[]) => {
    state.tables(...args);
    return { data: { tables: [{ identifier: { catalog: null, schema: 'public', table: 'assets' } }] } };
  },
}));
vi.mock('../canvas/components/CanvasKafkaSelectors', () => ({ CanvasKafkaTopicSelect: () => null }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
beforeEach(() => {
  state.source.purposes = ['SOURCE', 'STORAGE', 'DISTRIBUTION'];
  state.sourceItems = [state.source];
  state.modelStatus = 'PUBLISHED';
});

describe('SparkJarResourceDrawer', () => {
  it('requests only published-model connections and filters input/output before pagination', async () => {
    render(<SparkJarResourceDrawer bindingNames={[]} streaming={false} onClose={vi.fn()} onConfirm={vi.fn()} />);
    expect(screen.getByText('模型所属数据源')).toBeVisible();
    expect(state.sources).toHaveBeenLastCalledWith(expect.objectContaining({
      hasPublishedModels: true, search: JSON.stringify({ keyword: '', enabled: true, purposesAny: ['SOURCE', 'STORAGE'] }),
    }));
    await userEvent.click(screen.getByText('输出'));
    expect(state.sources).toHaveBeenLastCalledWith(expect.objectContaining({
      hasPublishedModels: true, search: JSON.stringify({ keyword: '', enabled: true, purposesAny: ['STORAGE'] }),
    }));
  });

  it('does not offer non-JDBC sources as model connections, but keeps external JDBC input', async () => {
    state.source.purposes = ['SOURCE'];
    state.sourceItems.push({ ...state.source, id: 'http', name: 'HTTP 输入', type: 'HTTP_API', connectionKind: 'HTTP_API' });
    render(<SparkJarResourceDrawer bindingNames={[]} streaming={false} onClose={vi.fn()} onConfirm={vi.fn()} />);
    await userEvent.click(screen.getAllByRole('combobox')[1]);
    await waitFor(() => expect(screen.getAllByText('PG 存储 · POSTGRESQL').at(-1)).toBeVisible());
    expect(screen.queryByText('HTTP 输入 · HTTP_API')).not.toBeInTheDocument();
  });

  it('keeps an existing input visible but blocks output when the source has no STORAGE purpose', async () => {
    state.source.purposes = ['SOURCE'];
    const confirm = vi.fn();
    render(<SparkJarResourceDrawer initial={{ bindingName: 'input', resourceId: 'model', resourceType: 'MODEL', accessMode: 'READ', topicName: null }}
      bindingNames={['input']} streaming={false} onClose={vi.fn()} onConfirm={confirm} />);
    await userEvent.click(screen.getByText('输出'));
    expect(await screen.findByText('当前数据源不支持此用途，请重新选择')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: '确认绑定' }));
    expect(confirm).not.toHaveBeenCalled();
  });

  it('rejects a formerly selected model that is no longer published', async () => {
    state.modelStatus = 'DISABLED';
    state.sourceItems = [];
    const confirm = vi.fn();
    render(<SparkJarResourceDrawer initial={{ bindingName: 'input', resourceId: 'model', resourceType: 'MODEL', accessMode: 'READ', topicName: null }}
      bindingNames={['input']} streaming={false} onClose={vi.fn()} onConfirm={confirm} />);
    expect(await screen.findByText('已选模型未发布或不属于当前连接，请重新选择')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: '确认绑定' }));
    expect(confirm).not.toHaveBeenCalled();
  });
  it('shows missing resource errors visibly instead of submitting an empty binding', async () => {
    const confirm = vi.fn();
    render(<SparkJarResourceDrawer initial={{ bindingName: '', resourceId: '', resourceType: 'JDBC_DATA_SOURCE', accessMode: 'READ', topicName: null }}
      bindingNames={[]} streaming={false} onClose={vi.fn()} onConfirm={confirm} />);
    await userEvent.click(screen.getByRole('button', { name: '确认绑定' }));
    expect(await screen.findByText('请选择当前用途下可用的数据源')).toBeVisible();
    expect(confirm).not.toHaveBeenCalled();
  });

  it('keeps the selected JDBC table when changing input to input/output; schema is not an extra choice', async () => {
    const confirm = vi.fn();
    render(<SparkJarResourceDrawer initial={{ bindingName: 'source_assets', resourceId: 'pg', resourceType: 'JDBC_DATA_SOURCE', accessMode: 'READ', topicName: null }}
      initialTable={{ catalog: null, schema: 'public', table: 'assets' }} bindingNames={['source_assets']}
      streaming={false} onClose={vi.fn()} onConfirm={confirm} />);
    expect(screen.queryByLabelText('Schema')).not.toBeInTheDocument();
    await userEvent.click(screen.getByText('输入及输出'));
    await userEvent.click(screen.getByRole('button', { name: '确认绑定' }));
    await waitFor(() => expect(confirm).toHaveBeenCalledWith({
      binding: { bindingName: 'source_assets', resourceId: 'pg', resourceType: 'JDBC_DATA_SOURCE', accessMode: 'READ_WRITE', topicName: null },
      table: { catalog: null, schema: 'public', table: 'assets' },
    }));
    expect(state.tables).toHaveBeenLastCalledWith('pg', { keyword: undefined, includeViews: false, limit: 100 }, true);
  });

  it('cancels edits without changing the caller binding and rejects duplicate code names', async () => {
    const confirm = vi.fn(); const close = vi.fn();
    const binding = { bindingName: 'source_assets', resourceId: 'pg', resourceType: 'JDBC_DATA_SOURCE' as const, accessMode: 'WRITE' as const, topicName: null };
    render(<SparkJarResourceDrawer initial={binding} bindingNames={['source_assets', 'target_assets']}
      streaming={false} onClose={close} onConfirm={confirm} />);
    const name = screen.getByPlaceholderText('选中资源后自动生成，可修改');
    await userEvent.clear(name);
    await userEvent.type(name, 'target_assets');
    await userEvent.click(screen.getByRole('button', { name: '确认绑定' }));
    expect(await screen.findByText('代码引用名已存在')).toBeVisible();
    expect(confirm).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    expect(close).toHaveBeenCalledOnce();
    expect(binding.bindingName).toBe('source_assets');
  });
});
