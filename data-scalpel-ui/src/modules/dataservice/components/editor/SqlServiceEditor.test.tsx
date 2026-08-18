import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Form } from 'antd';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataServiceRelatedModelView } from '../../hooks/useDataServiceRelatedModels';
import type { SqlServiceTestResponse } from '../../model/dataService';
import type { DataServiceFormValues } from '../../model/dataServiceEditor';

const mocks = vi.hoisted(() => ({ useDataModel: vi.fn(), useDataModels: vi.fn() }));

vi.mock('../../../../shared/components/MonacoSqlEditor', () => ({
  MonacoSqlEditor: ({ value, onChange }: { value?: string; onChange: (value: string) => void }) => (
    <textarea aria-label="SQL 模板" value={value ?? ''} onChange={(event) => onChange(event.target.value)} />
  ),
}));

vi.mock('../../../directory', () => ({
  DirectoryTreePanel: () => <div data-testid="model-directory" />,
  findDirectoryDescendantIds: () => [],
  useDirectoryTree: () => ({ data: [], isFetching: false }),
}));

vi.mock('../../../datasource', () => ({
  useDataSources: () => ({
    data: { content: [
      { id: 'source-1', code: 'source_one', name: '数据源一' },
      { id: 'source-2', code: 'source_two', name: '数据源二' },
    ] },
    isFetching: false,
  }),
}));

vi.mock('../../../model', () => ({
  buildDataModelSearch: ({ storageDataSourceId }: { storageDataSourceId?: string }) => (
    storageDataSourceId ? `storageDataSourceId:"${storageDataSourceId}"` : undefined
  ),
  dataModelStatusLabels: { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' },
  useDataModel: (...argumentsValue: unknown[]) => mocks.useDataModel(...argumentsValue),
  useDataModels: (...argumentsValue: unknown[]) => mocks.useDataModels(...argumentsValue),
  useModelWarehouseLayers: () => ({ data: { content: [] }, isFetching: false }),
}));

vi.mock('../../../serviceengine', () => ({
  useServiceEngineDataSourceRegistrations: () => ({
    data: { content: [
      { engineId: 'engine-1', dataSourceId: 'source-1', status: 'READY' },
      { engineId: 'engine-1', dataSourceId: 'source-2', status: 'READY' },
    ] },
    isFetching: false,
  }),
}));

import { SqlServiceEditor } from './SqlServiceEditor';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const modelsBySource = {
  'source-1': [
    {
      id: 'model-1', code: 'customer', name: '客户模型', status: 'DRAFT',
      directoryId: null, warehouseLayer: null, storageDataSourceName: '数据源一', schemaVersion: 1,
      catalogName: 'app', schemaName: 'public', physicalTableName: 'customer', storageDataSourceId: 'source-1',
      updatedAt: '2026-08-05T10:00:00Z',
    },
    {
      id: 'model-2', code: 'department', name: '部门模型', status: 'DISABLED',
      directoryId: null, warehouseLayer: null, storageDataSourceName: '数据源一', schemaVersion: 2,
      catalogName: 'app', schemaName: 'public', physicalTableName: 'department', storageDataSourceId: 'source-1',
      updatedAt: '2026-08-05T10:00:00Z',
    },
  ],
  'source-2': [{
    id: 'model-3', code: 'employee', name: '员工模型', status: 'PUBLISHED',
    directoryId: null, warehouseLayer: null, storageDataSourceName: '数据源二', schemaVersion: 1,
    catalogName: 'app', schemaName: 'hr', physicalTableName: 'employee', storageDataSourceId: 'source-2',
    updatedAt: '2026-08-05T10:00:00Z',
  }],
};

const validResult: SqlServiceTestResponse = {
  valid: true, problems: [], resultFields: [], preview: null, elapsedMs: 5,
};

interface HarnessProps {
  initialDataSourceId?: string;
  initialModelIds?: string[];
  relatedModels?: DataServiceRelatedModelView[];
}

const Harness = ({
  initialDataSourceId,
  initialModelIds = [],
  relatedModels = [],
}: HarnessProps = {}) => {
  const [form] = Form.useForm<DataServiceFormValues>();
  const values = Form.useWatch([], form);
  const [testResult, setTestResult] = useState<SqlServiceTestResponse | null>(null);
  const [testValues, setTestValues] = useState<Record<string, string>>({});
  const [resultCollapsed, setResultCollapsed] = useState(true);
  return (
    <Form<DataServiceFormValues>
      form={form}
      initialValues={{
        type: 'SQL_QUERY',
        engineId: 'engine-1',
        routePath: '/open-api/v1/customers',
        dataSourceId: initialDataSourceId,
        modelIds: initialModelIds,
        parameters: [],
        sqlText: '',
      }}
    >
      <Form.Item name="engineId" hidden><input /></Form.Item>
      <SqlServiceEditor
        form={form}
        engineId="engine-1"
        readOnly={false}
        canTest
        canViewModels
        canViewDataSources
        canViewEngines
        testValues={testValues}
        testResult={testResult}
        relatedModels={relatedModels}
        resultCollapsed={resultCollapsed}
        onTestValuesChange={setTestValues}
        onResetTest={() => setTestResult(null)}
        onResultCollapsedChange={setResultCollapsed}
      />
      <button type="button" onClick={() => { setTestResult(validResult); setResultCollapsed(false); }}>显示测试结果</button>
      <output data-testid="form-values">{JSON.stringify(values)}</output>
    </Form>
  );
};

const selectOption = async (user: ReturnType<typeof userEvent.setup>, label: string, option: string) => {
  await user.click(screen.getByLabelText(label));
  const candidates = await screen.findAllByText(option);
  const dropdownOption = candidates.find((candidate) => {
    const dropdown = candidate.closest<HTMLElement>('.ant-select-dropdown');
    return dropdown && window.getComputedStyle(dropdown).pointerEvents !== 'none';
  });
  expect(dropdownOption).toBeDefined();
  fireEvent.click(dropdownOption as HTMLElement);
};

describe('SqlServiceEditor', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    window.localStorage.clear();
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false, media: query, onchange: null,
        addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
      })),
    });
    mocks.useDataModel.mockReset();
    mocks.useDataModel.mockImplementation((id: string, enabled: boolean) => ({
      data: enabled ? {
        model: { id, schemaVersion: 1 },
        fields: [{
          id: `${id}-field-1`, modelId: id, code: 'user_id', name: '用户ID', fieldType: 'LONG',
          length: null, precision: null, scale: null, nullable: false, primaryKey: true,
          sortOrder: 0, description: '用户主键', createdAt: '2026-08-05T10:00:00Z', updatedAt: '2026-08-05T10:00:00Z',
        }],
      } : undefined,
      isFetching: false,
      isError: false,
      refetch: vi.fn(),
    }));
    mocks.useDataModels.mockReset();
    mocks.useDataModels.mockImplementation((request: { search?: string }) => {
      const source = request.search?.match(/storageDataSourceId:"([^"]+)"/)?.[1];
      return {
        data: {
          content: source ? modelsBySource[source as keyof typeof modelsBySource] ?? [] : [],
          totalElements: source ? modelsBySource[source as keyof typeof modelsBySource]?.length ?? 0 : 0,
        },
        isFetching: false,
        isError: false,
        refetch: vi.fn(),
      };
    });
  });

  it('selects models in the paged Drawer and confirms before clearing them on data source change', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await selectOption(user, 'PostgreSQL 数据源', '数据源一（source_one）');
    const chooseModels = screen.getByRole('button', { name: /选择模型/ });
    await waitFor(() => expect(chooseModels).toBeEnabled());
    await user.click(chooseModels);
    await waitFor(() => expect(mocks.useDataModels).toHaveBeenCalledWith({
      search: 'storageDataSourceId:"source-1"', page: 0, size: 20, sort: '-updatedAt,code',
    }, true));
    let checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[1]);
    await user.click(screen.getByRole('button', { name: /取\s*消/ }));
    expect(screen.getByTestId('form-values')).toHaveTextContent('"modelIds":[]');
    expect(screen.queryByText('customer')).not.toBeInTheDocument();

    await user.click(chooseModels);
    checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[1]);
    await user.click(checkboxes[2]);
    await user.click(screen.getByRole('button', { name: /确\s*定/ }));
    expect(screen.getByText('customer')).toBeInTheDocument();
    expect(screen.getByText('department')).toBeInTheDocument();
    expect(screen.queryByText('app.public.customer')).not.toBeInTheDocument();
    expect(screen.queryByText('app.public.department')).not.toBeInTheDocument();
    expect(screen.getByText('已关联 2 个模型')).toBeInTheDocument();
    expect(screen.queryByText(/另有 \d+ 个模型/)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '展开 客户模型 字段结构' }));
    expect(mocks.useDataModel).toHaveBeenCalledWith('model-1', true);
    expect(screen.getByText('用户ID')).toBeInTheDocument();
    expect(screen.getByText('主键 · 非空')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '显示测试结果' }));
    expect(screen.getByText('SQL 测试通过，耗时 5 ms')).toBeInTheDocument();

    await selectOption(user, 'PostgreSQL 数据源', '数据源二（source_two）');
    expect(screen.getByText('切换数据源将清空当前关联的 2 个模型和 SQL 测试结果。')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /取\s*消/ }));
    expect(screen.getByTestId('form-values')).toHaveTextContent('"dataSourceId":"source-1"');
    expect(screen.getByText('已关联 2 个模型')).toBeInTheDocument();

    await selectOption(user, 'PostgreSQL 数据源', '数据源二（source_two）');
    await user.click(screen.getByRole('button', { name: '切换并清空' }));
    await waitFor(() => expect(screen.getByTestId('form-values')).toHaveTextContent('"dataSourceId":"source-2"'));
    expect(screen.queryByText('customer')).not.toBeInTheDocument();
    expect(screen.queryByText('SQL 测试通过，耗时 5 ms')).not.toBeInTheDocument();
    expect(screen.getByTestId('form-values')).toHaveTextContent('"modelIds":[]');
    expect(screen.getByTestId('form-values')).toHaveTextContent('"engineId":"engine-1"');
  }, 20_000);

  it('starts with the result pane collapsed and allows direct expand and collapse', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    expect(screen.getByRole('button', { name: '展开测试结果' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '展开测试结果' }));
    expect(screen.getByRole('button', { name: '收起测试结果' })).toBeInTheDocument();
    expect(screen.getByText('尚未执行 SQL 测试')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '收起测试结果' }));
    expect(screen.getByRole('button', { name: '展开测试结果' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '显示测试结果' }));
    expect(screen.getByRole('button', { name: '收起测试结果' })).toBeInTheDocument();
    expect(screen.getByText('SQL 测试通过，耗时 5 ms')).toBeInTheDocument();
  });

  it('allows a resolved draft model to expand even when its association needs correction', async () => {
    const user = userEvent.setup();
    const draftModel: DataServiceRelatedModelView = {
      modelId: 'model-draft', role: 'REFERENCE', order: 1, resolved: true,
      code: 'draft_customer', name: '草稿客户模型', status: 'DRAFT',
      directoryId: null, directoryName: null,
      warehouseLayerId: null, warehouseLayerCode: null, warehouseLayerName: null,
      storageDataSourceId: 'source-2', storageDataSourceCode: 'source_two', storageDataSourceName: '数据源二',
      catalogName: 'app', schemaName: 'draft', physicalTableName: 'customer', schemaVersion: 1,
      updatedAt: '2026-08-05T10:00:00Z', loading: false, error: false,
    };

    render(<Harness initialDataSourceId="source-1" initialModelIds={['model-draft']} relatedModels={[draftModel]} />);

    const expand = screen.getByRole('button', { name: '展开 草稿客户模型 字段结构' });
    expect(expand).toBeEnabled();
    await user.click(expand);
    expect(mocks.useDataModel).toHaveBeenCalledWith('model-draft', true);
    expect(screen.getByText('用户ID')).toBeInTheDocument();
  });

  it('restores the shared expanded height and resets it by double-clicking the divider', async () => {
    window.localStorage.setItem('data-scalpel.sql-result-pane-height', '420');
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole('button', { name: '展开测试结果' }));
    const resultPanel = document.querySelectorAll<HTMLElement>('.ant-splitter-panel')[1];
    expect(resultPanel).toHaveStyle({ flexBasis: '420px' });

    fireEvent.doubleClick(screen.getByRole('separator'));
    await waitFor(() => expect(resultPanel).toHaveStyle({ flexBasis: '300px' }));
    expect(window.localStorage.getItem('data-scalpel.sql-result-pane-height')).toBe('300');
  });
});
