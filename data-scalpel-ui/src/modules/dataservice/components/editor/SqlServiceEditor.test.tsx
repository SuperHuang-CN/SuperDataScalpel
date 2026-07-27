import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Form } from 'antd';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { SqlServiceTestResponse } from '../../model/dataService';
import type { DataServiceFormValues } from '../../model/dataServiceEditor';

const mocks = vi.hoisted(() => ({ useDataModels: vi.fn() }));

vi.mock('../../../../shared/components/MonacoSqlEditor', () => ({
  MonacoSqlEditor: ({ value, onChange }: { value?: string; onChange: (value: string) => void }) => (
    <textarea aria-label="SQL 模板" value={value ?? ''} onChange={(event) => onChange(event.target.value)} />
  ),
}));

vi.mock('../../../directory', () => ({
  directoryTreeSelectData: () => [],
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
  dataModelStatusLabels: { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' },
  useDataModels: (...argumentsValue: unknown[]) => mocks.useDataModels(...argumentsValue),
}));

vi.mock('../../../serviceengine', () => ({
  useServiceEngineDataSourceRegistrations: () => ({
    data: { content: [{ engineId: 'engine-1', engineName: 'Engine A', engineCode: 'engine-a', status: 'READY' }] },
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
      catalogName: 'app', schemaName: 'public', physicalTableName: 'customer', storageDataSourceId: 'source-1',
    },
    {
      id: 'model-2', code: 'department', name: '部门模型', status: 'DISABLED',
      catalogName: 'app', schemaName: 'public', physicalTableName: 'department', storageDataSourceId: 'source-1',
    },
  ],
  'source-2': [{
    id: 'model-3', code: 'employee', name: '员工模型', status: 'PUBLISHED',
    catalogName: 'app', schemaName: 'hr', physicalTableName: 'employee', storageDataSourceId: 'source-2',
  }],
};

const validResult: SqlServiceTestResponse = {
  valid: true, problems: [], resultFields: [], preview: null, elapsedMs: 5,
};

const Harness = () => {
  const [form] = Form.useForm<DataServiceFormValues>();
  const values = Form.useWatch([], form);
  const [testResult, setTestResult] = useState<SqlServiceTestResponse | null>(null);
  const [testValues, setTestValues] = useState<Record<string, string>>({});
  return (
    <Form<DataServiceFormValues>
      form={form}
      initialValues={{ type: 'SQL_QUERY', routePath: '/open-api/v1/customers', modelIds: [], parameters: [], sqlText: '' }}
    >
      <SqlServiceEditor
        form={form}
        creating
        readOnly={false}
        canTest
        canViewDirectories={false}
        canViewModels
        canViewDataSources
        canViewEngines
        testValues={testValues}
        testResult={testResult}
        onTestValuesChange={setTestValues}
        onResetTest={() => setTestResult(null)}
      />
      <button type="button" onClick={() => setTestResult(validResult)}>显示测试结果</button>
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
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false, media: query, onchange: null,
        addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
      })),
    });
    mocks.useDataModels.mockReset();
    mocks.useDataModels.mockImplementation((request: { search?: string }) => {
      const source = request.search?.match(/storageDataSourceId:"([^"]+)"/)?.[1];
      return {
        data: { content: source ? modelsBySource[source as keyof typeof modelsBySource] ?? [] : [] },
        isFetching: false,
      };
    });
  });

  it('filters models by data source and clears models, Engine and test result when the source changes', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await selectOption(user, 'PostgreSQL 数据源', '数据源一（source_one）');
    await waitFor(() => expect(mocks.useDataModels).toHaveBeenCalledWith({
      search: 'storageDataSourceId:"source-1"', page: 0, size: 500, sort: 'code',
    }, true));
    await selectOption(user, '关联模型', '客户模型（customer） · 草稿');
    await selectOption(user, '关联模型', '部门模型（department） · 已停用');
    await user.keyboard('{Escape}');
    expect(screen.getByText('app.public.customer')).toBeInTheDocument();
    expect(screen.getByText('app.public.department')).toBeInTheDocument();
    await selectOption(user, 'Service Engine', 'Engine A（engine-a） · 已就绪');
    await user.click(screen.getByRole('button', { name: '显示测试结果' }));
    expect(screen.getByText('SQL 测试通过，耗时 5 ms')).toBeInTheDocument();

    await selectOption(user, 'PostgreSQL 数据源', '数据源二（source_two）');

    await waitFor(() => expect(mocks.useDataModels).toHaveBeenCalledWith({
      search: 'storageDataSourceId:"source-2"', page: 0, size: 500, sort: 'code',
    }, true));
    expect(screen.queryByText('app.public.customer')).not.toBeInTheDocument();
    expect(screen.queryByText('SQL 测试通过，耗时 5 ms')).not.toBeInTheDocument();
    expect(screen.getByTestId('form-values')).toHaveTextContent('"modelIds":[]');
    expect(screen.getByTestId('form-values')).not.toHaveTextContent('engine-1');
  }, 20_000);
});
