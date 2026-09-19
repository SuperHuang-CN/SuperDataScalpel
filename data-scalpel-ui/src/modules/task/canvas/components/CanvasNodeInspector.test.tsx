import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createRef } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  buildDataSourceSearch,
  useDataSourceTables,
  useTableMetadata,
  type TableIdentifier,
} from '../../../datasource';
import { exampleCanvasDefinition } from '../defaultCanvas';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type CanvasTableSchema,
  type CanvasValidationResult,
} from '../canvasTypes';
import { CanvasNodeInspector, type CanvasNodeInspectorHandle } from './CanvasNodeInspector';

const metadataFixtures = vi.hoisted(() => {
  const sourceId = 'c5c021bd-35d1-43ae-bbdb-ff90ff824ba0';
  const archiveSourceId = 'f4badf47-8c25-4811-aa47-83c72677eca0';
  const distributionId = '04d11960-1ee1-4282-8963-6fb52a21ab0c';
  const storageOnlyId = 'd26bf10a-4e71-47dc-929d-7638df967431';
  const columns = {
    orders: [
      ['order_id', 'LONG', false],
      ['customer_id', 'LONG', false],
      ['amount', 'DECIMAL', false],
      ['ordered_at', 'TIMESTAMP', false],
    ],
    customers: [
      ['customer_key', 'LONG', false],
      ['customer_name', 'STRING', false],
      ['customer_level', 'STRING', true],
    ],
    payments: [
      ['payment_id', 'LONG', false],
      ['order_id', 'LONG', false],
      ['paid_amount', 'DECIMAL', false],
    ],
    dwd_order_customer: [
      ['order_id', 'LONG', false],
      ['customer_id', 'LONG', false],
      ['amount', 'DECIMAL', false],
      ['ordered_at', 'TIMESTAMP', false],
      ['customer_key', 'LONG', false],
      ['customer_name', 'STRING', false],
      ['customer_level', 'STRING', true],
    ],
    invoices: [
      ['invoice_id', 'LONG', false],
    ],
  } as const;
  const dataSources = [
    {
      id: archiveSourceId,
      code: 'archive_pg',
      name: '归档 PostgreSQL',
      purposes: ['SOURCE'],
      type: 'POSTGRESQL',
      connectionKind: 'JDBC',
      enabled: true,
      connection: { kind: 'JDBC', databaseName: 'archive', schemaName: 'public' },
    },
    {
      id: sourceId,
      code: 'business_pg',
      name: '业务 PostgreSQL',
      purposes: ['SOURCE'],
      type: 'POSTGRESQL',
      connectionKind: 'JDBC',
      enabled: true,
      connection: { kind: 'JDBC', databaseName: 'demo', schemaName: 'public' },
    },
    {
      id: distributionId,
      code: 'distribution_pg',
      name: '分发 PostgreSQL',
      purposes: ['STORAGE', 'DISTRIBUTION'],
      type: 'POSTGRESQL',
      connectionKind: 'JDBC',
      enabled: true,
      connection: { kind: 'JDBC', databaseName: 'demo', schemaName: 'dw' },
    },
    {
      id: storageOnlyId,
      code: 'storage_pg',
      name: '仅存储 PostgreSQL',
      purposes: ['STORAGE'],
      type: 'POSTGRESQL',
      connectionKind: 'JDBC',
      enabled: true,
      connection: { kind: 'JDBC', databaseName: 'demo', schemaName: 'dw' },
    },
  ];
  return { sourceId, archiveSourceId, distributionId, storageOnlyId, columns, dataSources, truncated: false };
});

const modelFixtures = vi.hoisted(() => {
  const managedId = '41475dce-c159-490f-916e-903ff6f9b9de';
  const externalId = '51ba1947-68e3-4a16-b307-648977762e57';
  const disabledId = '5895ad30-ee22-4ba5-bb24-d0ea322c9d35';
  const field = (
    modelId: string,
    code: string,
    name: string,
    sortOrder: number,
  ) => ({
    id: `${modelId.slice(0, 28)}${String(sortOrder).padStart(8, '0')}`,
    modelId,
    code,
    name,
    fieldType: code === 'amount' ? 'DECIMAL' : code === 'order_id' ? 'LONG' : 'STRING',
    length: code === 'order_id' || code === 'amount' ? null : 120,
    precision: code === 'amount' ? 18 : null,
    scale: code === 'amount' ? 2 : null,
    nullable: code !== 'order_id',
    primaryKey: code === 'order_id',
    sortOrder,
    description: null,
    createdAt: '2026-07-17T00:00:00Z',
    updatedAt: '2026-07-17T00:00:00Z',
  });
  const detail = (
    id: string,
    code: string,
    name: string,
    physicalTableMode: 'MANAGED' | 'EXTERNAL',
    status: 'PUBLISHED' | 'DISABLED',
  ) => ({
    model: {
      id,
      code,
      name,
      directoryId: null,
      storageDataSourceId: metadataFixtures.distributionId,
      storageDataSourceName: '分发 PostgreSQL',
      catalogName: 'demo',
      schemaName: 'dw',
      physicalTableName: code,
      physicalTableMode,
      clickHouseOrderByColumns: [],
      status,
      schemaVersion: 3,
      description: null,
      createdAt: '2026-07-17T00:00:00Z',
      updatedAt: '2026-07-17T00:00:00Z',
    },
    fields: [
      field(id, 'order_id', '订单ID', 0),
      field(id, 'customername', '客户名称', 1),
      field(id, 'orderedat', '下单时间', 2),
    ],
  });
  const details = [
    detail(managedId, 'order_customer_model', '订单客户模型', 'MANAGED', 'PUBLISHED'),
    detail(externalId, 'external_order_model', '外部订单模型', 'EXTERNAL', 'PUBLISHED'),
    detail(disabledId, 'disabled_order_model', '已停用订单模型', 'MANAGED', 'DISABLED'),
  ];
  return { managedId, externalId, disabledId, details };
});

const canvasColumn = (
  [name, fieldType, nullable]: readonly [string, string, boolean],
): CanvasColumnSchema => ({
  name,
  fieldType: fieldType as CanvasColumnSchema['fieldType'],
  length: fieldType === 'STRING' ? 120 : null,
  precision: fieldType === 'DECIMAL' ? 18 : null,
  scale: fieldType === 'DECIMAL' ? 2 : null,
  nullable,
  defaultValue: null,
  autoIncrement: false,
  generated: false,
  comment: null,
  geometry: null,
});

const canvasTable = (name: keyof typeof metadataFixtures.columns): CanvasTableSchema => ({
  name,
  origin: {
    kind: 'JDBC',
    dataSourceId: name === 'dwd_order_customer' ? metadataFixtures.distributionId : metadataFixtures.sourceId,
    tableName: name,
    modelId: null,
    modelCode: null,
    modelSchemaVersion: null,
  },
  columns: metadataFixtures.columns[name].map(canvasColumn),
  datasetKind: 'BOUNDED',
  eventTimeColumn: null,
  watermarkDelay: null,
});

const engineValidationFixture = (
  definition: ReturnType<typeof exampleCanvasDefinition>,
): CanvasValidationResult => {
  const orders = canvasTable('orders');
  const customers = canvasTable('customers');
  const joined: CanvasTableSchema = {
    name: 'order_customer',
    origin: null,
    columns: [...orders.columns, ...customers.columns],
    datasetKind: 'BOUNDED',
    eventTimeColumn: null,
    watermarkDelay: null,
  };
  const nodeResults = new Map<string, CanvasNodeValidationResult>();

  definition.nodes.forEach((node) => {
    const result: CanvasNodeValidationResult = {
      nodeId: node.id,
      issues: [],
      inputTables: [],
      outputTables: [],
    };
    if (node.type === CanvasNodeType.JdbcInput) {
      result.outputTables = node.configuration.tables.flatMap(({ tableName }) => {
        if (tableName === 'orders') return [orders];
        if (tableName === 'customers') return [customers];
        return [];
      });
    } else if (node.type === CanvasNodeType.Join) {
      result.inputTables = [orders, customers];
      result.outputTables = [orders, customers, joined];
      if (!result.inputTables.some((table) => table.name === node.configuration.leftTableName)) {
        result.issues.push({
          code: 'TABLE_NOT_FOUND',
          severity: 'ERROR',
          message: `左表 ${node.configuration.leftTableName} 不在上游数据中`,
          nodeId: node.id,
          path: 'configuration.leftTableName',
        });
      }
    } else {
      result.inputTables = [orders, customers, joined];
    }
    nodeResults.set(node.id, result);
  });

  return {
    valid: [...nodeResults.values()].every((result) => result.issues.length === 0),
    canvasIssues: [],
    nodeResults,
  };
};

vi.mock('../../../datasource', async () => {
  const React = await vi.importActual<typeof import('react')>('react');
  const tableComments: Record<string, string> = {
    orders: '订单表',
    customers: '客户表',
    payments: '支付表，可用于演示 Join 字段重名',
    dwd_order_customer: '订单客户明细目标表',
    invoices: '发票表',
  };
  const tablesFor = (id: string | undefined) => {
    const names = id === metadataFixtures.distributionId || id === metadataFixtures.storageOnlyId
      ? ['dwd_order_customer']
      : id === metadataFixtures.archiveSourceId ? ['invoices'] : ['orders', 'customers', 'payments'];
    return names.map((table) => ({
      identifier: {
        catalog: 'demo',
        schema: id === metadataFixtures.distributionId || id === metadataFixtures.storageOnlyId ? 'dw' : 'public',
        table,
      },
      type: 'TABLE',
      comment: tableComments[table],
    }));
  };
  const metadataFor = (id: string | undefined, table: string | undefined) => {
    const values = table ? metadataFixtures.columns[table as keyof typeof metadataFixtures.columns] : undefined;
    if (!table || !values || !tablesFor(id).some((candidate) => candidate.identifier.table === table)) return undefined;
    return {
      table: tablesFor(id).find((candidate) => candidate.identifier.table === table),
      columns: values.map(([name, logicalType, nullable], index) => ({
        name,
        ordinal: index + 1,
        jdbcType: 0,
        nativeType: logicalType,
        logicalType,
        platformTypeDefinition: {
          type: logicalType,
          length: logicalType === 'STRING' ? 120 : null,
          precision: logicalType === 'DECIMAL' ? 18 : null,
          scale: logicalType === 'DECIMAL' ? 2 : null,
          geometry: null,
        },
        length: logicalType === 'STRING' ? 120 : null,
        precision: logicalType === 'DECIMAL' ? 18 : null,
        scale: logicalType === 'DECIMAL' ? 2 : null,
        nullable,
        defaultValue: null,
        autoIncrement: false,
        generated: false,
        comment: null,
      })),
      primaryKey: null,
      uniqueKeys: table === 'dwd_order_customer'
        ? [{ type: 'PRIMARY_KEY', name: 'pk_dwd_order_customer', columns: ['order_id'] }]
        : [],
      indexes: [],
    };
  };
  const useDataSourceTablesMock = vi.fn((id: string | undefined, request?: {
    keyword?: string;
    includeViews?: boolean;
    limit?: number;
  }) => ({
    data: {
      tables: tablesFor(id).filter((table) => !request?.keyword
        || table.identifier.table.includes(request.keyword)),
      truncated: metadataFixtures.truncated,
    },
    isFetching: false,
    isError: false,
    refetch: vi.fn(),
  }));
  const JdbcTablePickerModal = ({
    open,
    dataSourceId,
    value,
    onCancel,
    onConfirm,
  }: {
    open: boolean;
    dataSourceId: string;
    value: readonly TableIdentifier[];
    onCancel: () => void;
    onConfirm: (tables: TableIdentifier[]) => void;
  }) => {
    const [selected, setSelected] = React.useState<TableIdentifier[]>(
      () => value.map((table) => ({ ...table })),
    );
    const [search, setSearch] = React.useState('');
    const [keyword, setKeyword] = React.useState('');
    const timerRef = React.useRef<number | undefined>(undefined);
    React.useEffect(() => () => window.clearTimeout(timerRef.current), []);
    const query = useDataSourceTablesMock(dataSourceId, {
      keyword: keyword || undefined,
      includeViews: false,
      limit: 100,
    });
    if (!open) return null;
    const selectedNames = new Set(selected.map((table) => table.table));
    return (
      <div role="dialog" aria-label="选择 JDBC 物理表">
        <input
          placeholder="输入物理表名搜索"
          value={search}
          onChange={(event) => {
            const next = event.target.value;
            setSearch(next);
            window.clearTimeout(timerRef.current);
            timerRef.current = window.setTimeout(() => setKeyword(next.trim()), 300);
          }}
        />
        {query.data.truncated && <span>匹配结果超过 100 项，请输入关键字缩小范围</span>}
        {query.data.tables.map((table) => (
          <label key={table.identifier.table}>
            <input
              type="checkbox"
              aria-label={`${table.identifier.catalog}.${table.identifier.schema}.${table.identifier.table}`}
              checked={selectedNames.has(table.identifier.table)}
              onChange={(event) => setSelected((current) => event.target.checked
                ? [...current, { ...table.identifier }]
                : current.filter((item) => item.table !== table.identifier.table))}
            />
            {table.identifier.table}
          </label>
        ))}
        <button type="button" onClick={onCancel}>取消</button>
        <button type="button" onClick={() => onConfirm(selected)}>
          确定 · {selected.length} 张表
        </button>
      </div>
    );
  };
  return {
    buildDataSourceSearch: vi.fn(() => undefined),
    useDataSource: vi.fn((id: string | undefined) => ({
      data: metadataFixtures.dataSources.find((dataSource) => dataSource.id === id),
      isError: Boolean(id) && !metadataFixtures.dataSources.some((dataSource) => dataSource.id === id),
      isFetching: false,
      error: null,
      refetch: vi.fn(),
    })),
    useApiResource: vi.fn(() => ({
      data: undefined,
      isError: false,
      isFetching: false,
      error: null,
      refetch: vi.fn(),
    })),
    useApiResources: vi.fn(() => ({
      data: [],
      isFetching: false,
    })),
    useDataSourceTypes: vi.fn(() => ({
      data: [{
        id: 'POSTGRESQL',
        connectionKind: 'JDBC',
        driverAvailable: true,
        capabilities: ['LIST_TABLES', 'READ_TABLE_METADATA'],
      }],
      isFetching: false,
    })),
    useDataSources: vi.fn(() => ({
      data: { content: metadataFixtures.dataSources, totalElements: metadataFixtures.dataSources.length },
      isFetching: false,
    })),
    JdbcTablePickerModal,
    useDataSourceTables: useDataSourceTablesMock,
    useTableMetadata: vi.fn((id: string | undefined, identifier: { table: string } | undefined) => {
      const data = metadataFor(id, identifier?.table);
      return { data, isFetching: false, isError: Boolean(identifier) && !data };
    }),
  };
});

vi.mock('../../../model', () => ({
  buildDataModelSearch: vi.fn(() => undefined),
  dataModelStatusLabels: {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    DISABLED: '已停用',
  },
  physicalTableModeLabels: {
    MANAGED: '新建物理表',
    EXTERNAL: '绑定已有表',
  },
  useDataModel: vi.fn((id: string | undefined) => {
    const data = modelFixtures.details.find((detail) => detail.model.id === id);
    return {
      data,
      isError: Boolean(id) && !data,
      isFetching: false,
      error: data ? null : new Error('模型不存在'),
      refetch: vi.fn(),
    };
  }),
  useDataModels: vi.fn(() => {
    const content = modelFixtures.details
      .filter((detail) => detail.model.status === 'PUBLISHED')
      .map((detail) => detail.model);
    return {
      data: { content, totalElements: content.length },
      isFetching: false,
    };
  }),
}));

const modelInputNode = (modelId: string): Extract<CanvasNodeDefinition, { type: 'MODEL_INPUT' }> => ({
  id: 'd52cf848-e6d9-46e1-813e-0e96eafdb463',
  type: CanvasNodeType.ModelInput,
  name: '模型输入',
  layout: { x: 80, y: 80, width: 240, height: 120 },
  configuration: { models: [{ modelId }] },
});

const modelOutputNode = (
  targetModelId: string,
): Extract<CanvasNodeDefinition, { type: 'MODEL_OUTPUT' }> => ({
  id: '4fb252a7-4987-4178-afc1-9f55b8559779',
  type: CanvasNodeType.ModelOutput,
  name: '模型输出',
  layout: { x: 800, y: 180, width: 240, height: 120 },
  configuration: {
    writes: [{
      writeId: 'd709d3ad-efb1-4b86-bb78-c8172ea0ed37',
      sourceTableName: 'order_customer',
      targetModelId,
      writeMode: 'APPEND',
      columnMappings: [],
    }],
    sourceTableName: 'order_customer',
    targetModelId,
    writeMode: 'APPEND',
    columnMappings: [],
  },
});

const modelOutputValidation = (nodeId: string): CanvasNodeValidationResult => ({
  nodeId,
  issues: [],
  inputTables: [{
    name: 'order_customer',
    origin: null,
    columns: [
      canvasColumn(['order_id', 'LONG', false]),
      canvasColumn(['customer_name', 'STRING', true]),
      canvasColumn(['ordered_at', 'TIMESTAMP', false]),
    ],
    datasetKind: 'BOUNDED',
    eventTimeColumn: null,
    watermarkDelay: null,
  }],
  outputTables: [],
});

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const selectAntOption = async (
  fieldLabel: string,
  optionLabel: string,
) => {
  fireEvent.mouseDown(screen.getByLabelText(fieldLabel));
  const option = (await screen.findAllByText(optionLabel)).find((element) => {
    const dropdown = element.closest<HTMLElement>('.ant-select-dropdown');
    return dropdown && window.getComputedStyle(dropdown).pointerEvents !== 'none';
  });
  expect(option).toBeDefined();
  fireEvent.click(option as HTMLElement);
};

const waitForInspector = async () => {
  await waitFor(() => {
    expect(screen.queryByText('正在加载节点配置…')).not.toBeInTheDocument();
  });
};

const openFirstProcessorOperation = async (sourceTableName = 'orders') => {
  fireEvent.click(screen.getByRole('button', { name: `配置 ${sourceTableName}` }));
  await screen.findByRole('button', { name: '保存此项' });
};

const openFirstJdbcWrite = async () => {
  fireEvent.click(screen.getByRole('button', { name: '设置第 1 条 JDBC 写入' }));
  await screen.findByRole('button', { name: '保存此项' });
};

const openFirstModelWrite = async () => {
  fireEvent.click(screen.getByRole('button', { name: '设置第 1 条模型写入' }));
  await screen.findByRole('button', { name: '保存此项' });
};

describe('CanvasNodeInspector', () => {
  beforeEach(() => {
    metadataFixtures.truncated = false;
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query) => ({
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

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('shows JDBC input metadata and applies only business configuration', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const input = definition.nodes.find(
      (node) => node.type === CanvasNodeType.JdbcInput
        && node.configuration.tables.some((table) => table.tableName === 'orders'),
    );
    const onApply = vi.fn();
    const onDirtyChange = vi.fn();
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    expect(input?.type).toBe(CanvasNodeType.JdbcInput);
    if (!input || input.type !== CanvasNodeType.JdbcInput) return;

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={input}
        validation={validation.nodeResults.get(input.id)}
        onApply={onApply}
        onDirtyChange={onDirtyChange}
      />,
    );
    await waitForInspector();

    fireEvent.click(screen.getByRole('button', { name: '查看 orders 字段' }));
    expect(screen.getByText('order_id')).toBeInTheDocument();
    expect(screen.queryByText('demo.public.orders')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('节点名称')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /管理物理表/ }));
    fireEvent.click(await screen.findByRole('checkbox', { name: /payments/ }));
    fireEvent.click(screen.getByRole('button', { name: '确定 · 2 张表' }));
    expect(onDirtyChange).toHaveBeenLastCalledWith(true);

    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });

    await waitFor(() => {
      expect(onApply).toHaveBeenCalledWith(expect.objectContaining({
        id: input.id,
        type: CanvasNodeType.JdbcInput,
        configuration: {
          dataSourceId: input.configuration.dataSourceId,
          tables: [
            { tableName: 'orders', readOptions: [] },
            { tableName: 'payments', readOptions: [] },
          ],
        },
      }));
      expect(onDirtyChange).toHaveBeenLastCalledWith(false);
    });
  });

  it('applies an incomplete draft while keeping validation errors visible', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const input = definition.nodes.find((node) => node.type === CanvasNodeType.JdbcInput);
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();
    expect(input).toBeDefined();
    if (!input) return;
    input.configuration = { dataSourceId: '', tables: [] };

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={input}
        validation={validation.nodeResults.get(input.id)}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();
    let applied: boolean | undefined;
    await act(async () => {
      applied = await inspectorRef.current?.apply();
    });

    expect(applied).toBe(true);
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({
      id: input.id,
      type: CanvasNodeType.JdbcInput,
      configuration: { dataSourceId: '', tables: [] },
    }));
  });

  it('rejects sensitive HTTP API runtime parameter names before applying the node', async () => {
    const node: Extract<CanvasNodeDefinition, { type: 'HTTP_API_INPUT' }> = {
      id: '97f9ff44-95c1-4bcc-bc4f-afb39e238803',
      type: CanvasNodeType.HttpApiInput,
      name: 'HTTP API 输入',
      layout: { x: 80, y: 80, width: 240, height: 120 },
      configuration: {
        dataSourceId: 'a2961398-cb9c-4f50-bcb1-b3015685a8bf',
        resources: [{ resourceId: '1ff5d574-d031-47f2-805b-bd84e7785f52', outputTableName: 'api_orders', runtimeParameters: [{ name: 'accessToken', value: 'must-not-be-persisted' }] }],
      },
    };
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        validation={{ nodeId: node.id, issues: [], inputTables: [], outputTables: [] }}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(false);
    });

    expect(onApply).not.toHaveBeenCalled();
    expect(await screen.findByText('运行时参数不能包含密码、Token、API Key 或 Secret')).toBeInTheDocument();
  });

  it('keeps and applies an invalid table selection so upstream configuration can be fixed first', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const input = definition.nodes.find(
      (node) => node.type === CanvasNodeType.JdbcInput
        && node.configuration.tables.some((table) => table.tableName === 'orders'),
    );
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();
    if (!input || input.type !== CanvasNodeType.JdbcInput) return;

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={input}
        validation={validation.nodeResults.get(input.id)}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await selectAntOption('来源数据源', '归档 PostgreSQL');
    expect(screen.getByLabelText('来源数据源').closest('.ant-select-content'))
      .toHaveAttribute('title', '归档 PostgreSQL');
    await waitFor(() => {
      expect(vi.mocked(useTableMetadata).mock.calls.at(-1)?.[0]).toBe(metadataFixtures.archiveSourceId);
    });
    expect(screen.getByText('orders')).toBeInTheDocument();
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledTimes(1);
    expect(await screen.findByText('物理表不存在或元数据读取失败')).toBeInTheDocument();
  });

  it('warns when the real table result is truncated and offers refresh', async () => {
    metadataFixtures.truncated = true;
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const input = definition.nodes.find((node) => node.type === CanvasNodeType.JdbcInput);
    if (!input) return;

    render(
      <CanvasNodeInspector
        node={input}
        validation={validation.nodeResults.get(input.id)}
        onApply={vi.fn()}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    fireEvent.click(screen.getByRole('button', { name: /管理物理表/ }));
    expect(await screen.findByText(/匹配结果超过 100 项/)).toBeInTheDocument();
  });

  it('debounces physical-table input into a remote keyword query', async () => {
    try {
      const definition = exampleCanvasDefinition();
      const validation = engineValidationFixture(definition);
      const input = definition.nodes.find((node) => node.type === CanvasNodeType.JdbcInput);
      if (!input) return;

      render(
        <CanvasNodeInspector
          node={input}
          validation={validation.nodeResults.get(input.id)}
          onApply={vi.fn()}
          onDirtyChange={vi.fn()}
        />,
      );
      await waitForInspector();
      fireEvent.click(screen.getByRole('button', { name: /管理物理表/ }));
      vi.useFakeTimers();

      fireEvent.change(screen.getByPlaceholderText('输入物理表名搜索'), { target: { value: 'pay' } });
      act(() => vi.advanceTimersByTime(300));
      expect(vi.mocked(useDataSourceTables).mock.calls.at(-1)?.[1])
        .toEqual({ keyword: 'pay', includeViews: false, limit: 100 });
    } finally {
      vi.useRealTimers();
    }
  });

  it('uses Task Engine input tables for Join table and field selections', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const join = definition.nodes.find((node) => node.type === CanvasNodeType.Join);
    expect(join).toBeDefined();
    if (!join) return;

    render(<CanvasNodeInspector node={join} validation={validation.nodeResults.get(join.id)} onApply={vi.fn()} onDirtyChange={vi.fn()} />);
    await waitForInspector();

    expect(screen.getAllByText('orders').length).toBeGreaterThan(0);
    expect(screen.getAllByText('customers').length).toBeGreaterThan(0);
    expect(screen.getByText('条件 1')).toBeInTheDocument();
    const addCondition = screen.getByRole('button', { name: /添加 Join 条件/ });
    fireEvent.click(addCondition);
    expect(screen.getByText('条件 2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '删除条件 2' }));
    expect(screen.queryByText('条件 2')).not.toBeInTheDocument();
  });

  it('suggests the full right table name for duplicate Join output fields', async () => {
    const join: Extract<CanvasNodeDefinition, { type: 'JOIN' }> = {
      id: 'ba196a5c-64da-4869-806b-71ae31e2cbed',
      type: CanvasNodeType.Join,
      name: '用户部门关联',
      layout: { x: 320, y: 20, width: 360, height: 216 },
      configuration: {
        leftTableName: 'sys_user',
        rightTableName: 'sys_dept',
        outputTableName: 'dim_user',
        joinType: 'LEFT',
        conditions: [{
          leftColumnName: 'dept_id',
          operator: 'EQUALS',
          rightColumnName: 'id',
        }],
        outputColumns: [],
      },
    };
    const table = (name: string): CanvasTableSchema => ({
      ...canvasTable('orders'),
      name,
      origin: null,
      columns: [canvasColumn(['id', 'LONG', false]), canvasColumn(['name', 'STRING', false])],
    });
    const validation: CanvasNodeValidationResult = {
      nodeId: join.id,
      issues: [],
      inputTables: [table('sys_user'), table('sys_dept')],
      outputTables: [],
    };

    render(
      <CanvasNodeInspector
        node={join}
        validation={validation}
        onApply={vi.fn()}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await waitFor(() => {
      expect(screen.getByDisplayValue('sys_dept_id')).toBeInTheDocument();
      expect(screen.getByDisplayValue('sys_dept_name')).toBeInTheDocument();
    });
    expect(screen.queryByDisplayValue('right_id')).not.toBeInTheDocument();
  });

  it('suggests the full static table name for duplicate Stream Join output fields', async () => {
    const streamJoin: Extract<CanvasNodeDefinition, { type: 'STREAM_JOIN' }> = {
      id: '1f620b7a-ed25-4b17-8910-4399f52fa741',
      type: CanvasNodeType.StreamJoin,
      name: '订单流关联客户维表',
      layout: { x: 320, y: 20, width: 360, height: 216 },
      configuration: {
        leftTableName: 'order_events',
        rightTableName: 'dim_customer',
        outputTableName: 'enriched_events',
        joinType: 'LEFT',
        conditions: [{
          leftColumnName: 'id',
          operator: 'EQUALS',
          rightColumnName: 'id',
        }],
        outputColumns: [],
      },
    };
    const table = (
      name: string,
      datasetKind: CanvasTableSchema['datasetKind'],
    ): CanvasTableSchema => ({
      ...canvasTable('orders'),
      name,
      origin: null,
      columns: [canvasColumn(['id', 'LONG', false]), canvasColumn(['name', 'STRING', false])],
      datasetKind,
      eventTimeColumn: datasetKind === 'UNBOUNDED' ? 'event_time' : null,
      watermarkDelay: datasetKind === 'UNBOUNDED' ? '10 minutes' : null,
    });
    const validation: CanvasNodeValidationResult = {
      nodeId: streamJoin.id,
      issues: [],
      inputTables: [table('order_events', 'UNBOUNDED'), table('dim_customer', 'BOUNDED')],
      outputTables: [],
    };

    render(
      <CanvasNodeInspector
        node={streamJoin}
        validation={validation}
        onApply={vi.fn()}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await waitFor(() => {
      expect(screen.getByDisplayValue('dim_customer_id')).toBeInTheDocument();
      expect(screen.getByDisplayValue('dim_customer_name')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: '排除右侧 Join Key' })).toBeInTheDocument();
  });

  it('configures Rename from Task Engine tables and keeps mappings as one atomic operation', async () => {
    const rename: Extract<CanvasNodeDefinition, { type: 'RENAME' }> = {
      id: 'ee30aa02-b663-4d1c-95ca-3e63246113ae',
      type: CanvasNodeType.Rename,
      name: '订单重命名',
      layout: { x: 320, y: 20, width: 240, height: 120 },
      configuration: {
        operations: [{
          operationId: 'd62a32d1-0a5d-475e-9d9f-aef39f0177b0',
          sourceTableName: 'orders',
          output: { mode: 'CREATE_NEW_TABLE', outputTableName: 'source_orders' },
          columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'source_order_id' }],
        }],
        sourceTableName: 'orders',
        outputTableName: 'source_orders',
        columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'source_order_id' }],
      },
    };
    const validation: CanvasNodeValidationResult = {
      nodeId: rename.id,
      issues: [],
      inputTables: [canvasTable('orders'), canvasTable('customers')],
      outputTables: [],
    };
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={rename}
        validation={validation}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await openFirstProcessorOperation();
    expect(screen.getByPlaceholderText('输出逻辑表名')).toHaveValue('source_orders');
    const renameInput = screen.getByLabelText('重命名 order_id');
    expect(renameInput).toHaveValue('source_order_id');
    fireEvent.change(renameInput, { target: { value: 'order_key' } });
    await waitFor(() => expect(renameInput).toHaveValue('order_key'));
    fireEvent.click(screen.getByRole('button', { name: '保存此项' }));
    await waitFor(() => expect(screen.queryByText('配置处理表 · orders')).not.toBeInTheDocument());

    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: rename.id,
      type: CanvasNodeType.Rename,
      configuration: {
        operations: [{
          operationId: 'd62a32d1-0a5d-475e-9d9f-aef39f0177b0',
          sourceTableName: 'orders',
          output: { mode: 'CREATE_NEW_TABLE', outputTableName: 'source_orders' },
          columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'order_key' }],
        }],
      },
    });
  });

  it('does not invent Join input tables when Task Engine has no current result', async () => {
    const definition = exampleCanvasDefinition();
    const join = definition.nodes.find((node) => node.type === CanvasNodeType.Join);
    if (!join) return;

    render(
      <CanvasNodeInspector
        node={join}
        validation={undefined}
        validationUnavailableMessage="Task Engine 校验请求失败"
        onApply={vi.fn()}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    fireEvent.click(screen.getByLabelText('Task Engine 尚未完成校验'));
    expect(await screen.findByText('Task Engine 校验请求失败')).toBeInTheDocument();
    expect(screen.getByLabelText('左表')).toBeDisabled();
    expect(screen.getByLabelText('右表')).toBeDisabled();
  });

  it('keeps an invalid upstream table selection visible after schemas change', async () => {
    const definition = exampleCanvasDefinition();
    const join = definition.nodes.find((node) => node.type === CanvasNodeType.Join);
    expect(join).toBeDefined();
    if (!join || join.type !== CanvasNodeType.Join) return;
    join.configuration.leftTableName = 'archived_orders';
    const validation = engineValidationFixture(definition);

    render(<CanvasNodeInspector node={join} validation={validation.nodeResults.get(join.id)} onApply={vi.fn()} onDirtyChange={vi.fn()} />);
    await waitForInspector();

    expect(screen.getByText('archived_orders')).toBeInTheDocument();
    expect(screen.queryByText('TABLE_NOT_FOUND')).not.toBeInTheDocument();
    const issueButton = screen.getByRole('button', { name: /查看 \d+ 个配置问题/ });
    fireEvent.click(issueButton);
    expect(issueButton).toHaveAttribute('aria-expanded', 'true');
  });

  it('renders JDBC output mappings as fixed target-driven rows', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const output = definition.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    expect(output).toBeDefined();
    if (!output) return;

    render(<CanvasNodeInspector node={output} validation={validation.nodeResults.get(output.id)} onApply={vi.fn()} onDirtyChange={vi.fn()} />);
    await waitForInspector();

    await openFirstJdbcWrite();
    expect(screen.getByRole('button', { name: /自动匹配空白字段/ })).toBeInTheDocument();
    expect(screen.getByLabelText('目标字段 order_id 的来源字段')).toBeInTheDocument();
    expect(screen.queryByLabelText('字段映射模式')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /添加字段映射/ })).not.toBeInTheDocument();
  });

  it('reports an invalid JDBC output data source but still applies the draft', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const output = definition.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();
    if (!output || output.type !== CanvasNodeType.JdbcOutput) return;
    output.configuration.dataSourceId = metadataFixtures.sourceId;

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={output}
        validation={validation.nodeResults.get(output.id)}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    expect(screen.getByText('业务 PostgreSQL')).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByRole('combobox'));
    expect(vi.mocked(buildDataSourceSearch)).toHaveBeenCalledWith({
      keyword: '',
      purpose: 'DISTRIBUTION',
      enabled: true,
    });
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({
      configuration: {
        dataSourceId: metadataFixtures.sourceId,
        writes: output.configuration.writes,
      },
    }));
  });

  it('does not expose add or delete actions for target-driven mappings', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const output = definition.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    expect(output).toBeDefined();
    if (!output) return;

    render(<CanvasNodeInspector node={output} validation={validation.nodeResults.get(output.id)} onApply={vi.fn()} onDirtyChange={vi.fn()} />);
    await waitForInspector();

    await openFirstJdbcWrite();
    expect(screen.getByText('字段映射')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /添加字段映射/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /删除字段映射/ })).not.toBeInTheDocument();
  });

  it('shows published model metadata and applies only the ModelInput reference', async () => {
    const node = modelInputNode(modelFixtures.managedId);
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();
    const onDirtyChange = vi.fn();

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        validation={{ nodeId: node.id, issues: [], inputTables: [], outputTables: [] }}
        onApply={onApply}
        onDirtyChange={onDirtyChange}
      />,
    );
    await waitForInspector();

    expect(screen.getByText('订单客户模型 · order_customer_model')).toBeInTheDocument();
    expect(screen.getByText('Schema v3')).toBeInTheDocument();
    expect(screen.getByText('分发 PostgreSQL')).toBeInTheDocument();

    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: node.id,
      type: CanvasNodeType.ModelInput,
      configuration: { models: [{ modelId: modelFixtures.managedId }] },
    });
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);
  });

  it('retains and applies a disabled ModelInput selection as an invalid draft', async () => {
    const node = modelInputNode(modelFixtures.disabledId);
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        validation={{
          nodeId: node.id,
          issues: [{
            code: 'MODEL_NOT_PUBLISHED',
            severity: 'ERROR',
            message: '模型当前状态为已停用',
            nodeId: node.id,
            path: 'configuration.models[0].modelId',
          }],
          inputTables: [],
          outputTables: [],
        }}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    expect(screen.getByText('已停用订单模型 · disabled_order_model')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看 1 个配置问题' }));
    expect(await screen.findByText('模型当前状态为已停用')).toBeInTheDocument();
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledTimes(1);
  });

  it('keeps and applies invalid EXTERNAL OVERWRITE as a draft without rewriting it', async () => {
    const node = modelOutputNode(modelFixtures.externalId);
    node.configuration.writeMode = 'OVERWRITE';
    node.configuration.writes![0].writeMode = 'OVERWRITE';
    const validation = modelOutputValidation(node.id);
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        validation={validation}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await openFirstModelWrite();
    expect(screen.getByText('EXTERNAL 模型不允许 OVERWRITE')).toBeInTheDocument();
    expect(screen.getByLabelText('写入模式').closest('.ant-select-content'))
      .toHaveAttribute('title', 'OVERWRITE · EXTERNAL 模型不可用');
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledTimes(1);
  });

  it('offers model-primary-key UPSERT in batch and streaming modes', async () => {
    const batchNode = modelOutputNode(modelFixtures.managedId);
    batchNode.configuration.writeMode = 'UPSERT';
    batchNode.configuration.writes![0].writeMode = 'UPSERT';
    const { unmount } = render(
      <CanvasNodeInspector
        node={batchNode}
        executionMode="BATCH"
        validation={modelOutputValidation(batchNode.id)}
        onApply={vi.fn()}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await openFirstModelWrite();
    expect(screen.getByText('模型主键：')).toBeInTheDocument();
    expect(screen.getAllByText('order_id').length).toBeGreaterThan(0);
    expect(screen.getByLabelText('写入模式').closest('.ant-select-content'))
      .toHaveAttribute('title', 'UPSERT · 按模型主键插入或更新');
    unmount();

    const streamingNode = modelOutputNode(modelFixtures.managedId);
    streamingNode.configuration.writeMode = 'OVERWRITE';
    streamingNode.configuration.writes![0].writeMode = 'OVERWRITE';
    render(
      <CanvasNodeInspector
        node={streamingNode}
        executionMode="STREAMING"
        validation={modelOutputValidation(streamingNode.id)}
        onApply={vi.fn()}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await openFirstModelWrite();
    expect(screen.getByLabelText('写入模式').closest('.ant-select-content'))
      .toHaveAttribute('title', 'OVERWRITE · 实时模式不支持');
  });

  it('auto-matches exact and camel-snake-equivalent model fields', async () => {
    const node = modelOutputNode(modelFixtures.managedId);
    const validation = modelOutputValidation(node.id);
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();
    const onDirtyChange = vi.fn();

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        validation={validation}
        onApply={onApply}
        onDirtyChange={onDirtyChange}
      />,
    );
    await waitForInspector();

    await openFirstModelWrite();
    fireEvent.click(screen.getByRole('button', { name: /自动匹配空白字段/ }));
    fireEvent.click(screen.getByRole('button', { name: '保存此项' }));
    await waitFor(() => {
      expect(onDirtyChange).toHaveBeenLastCalledWith(true);
    });
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: node.id,
      type: CanvasNodeType.ModelOutput,
      configuration: {
        writes: [{
          writeId: 'd709d3ad-efb1-4b86-bb78-c8172ea0ed37',
          sourceTableName: 'order_customer',
          targetModelId: modelFixtures.managedId,
          writeMode: 'APPEND',
          columnMappings: [
            { sourceColumnName: 'order_id', targetColumnName: 'order_id' },
            { sourceColumnName: 'customer_name', targetColumnName: 'customername' },
            { sourceColumnName: 'ordered_at', targetColumnName: 'orderedat' },
          ],
        }],
      },
    });
  });

  it('keeps unavailable upstream tables and field mappings visible', async () => {
    const node = modelOutputNode(modelFixtures.managedId);
    node.configuration.sourceTableName = 'archived_orders';
    node.configuration.columnMappings = [{
      sourceColumnName: 'removed_source',
      targetColumnName: 'removed_target',
    }];
    node.configuration.writes![0].sourceTableName = 'archived_orders';
    node.configuration.writes![0].columnMappings = [{
      sourceColumnName: 'removed_source',
      targetColumnName: 'removed_target',
    }];

    render(
      <CanvasNodeInspector
        node={node}
        validation={modelOutputValidation(node.id)}
        onApply={vi.fn()}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await openFirstModelWrite();
    expect(screen.getByText('archived_orders（上游已不可用）')).toBeInTheDocument();
    expect(screen.getByText('目标字段已不存在')).toBeInTheDocument();
    expect(screen.getByText('removed_target')).toBeInTheDocument();
    expect(screen.getByText('已保存来源字段：removed_source')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '清除' })).toBeInTheDocument();
  });

  it('loads and applies an ordered NULL_HANDLING rule set', async () => {
    const node: Extract<CanvasNodeDefinition, { type: 'NULL_HANDLING' }> = {
      id: 'c73e95ad-afb4-41d0-93db-5111950cdd96',
      type: CanvasNodeType.NullHandling,
      name: '空值处理',
      layout: { x: 320, y: 20, width: 240, height: 120 },
      configuration: {
        operations: [{
          operationId: '6212fa20-ddfb-4ac5-8bd3-af96d7c3979f',
          sourceTableName: 'orders',
          output: { mode: 'CREATE_NEW_TABLE', outputTableName: 'orders_cleaned' },
          rules: [
            {
              kind: 'DROP_ROW',
              columnNames: ['order_id', 'customer_id'],
              matchMode: 'ANY_NULL',
            },
            {
              kind: 'FILL_LITERAL',
              columnName: 'amount',
              value: { dataType: 'DECIMAL', value: '0.00' },
            },
          ],
        }],
        sourceTableName: 'orders',
        outputTableName: 'orders_cleaned',
        rules: [
          {
            kind: 'DROP_ROW',
            columnNames: ['order_id', 'customer_id'],
            matchMode: 'ANY_NULL',
          },
          {
            kind: 'FILL_LITERAL',
            columnName: 'amount',
            value: { dataType: 'DECIMAL', value: '0.00' },
          },
        ],
      },
    };
    const validation: CanvasNodeValidationResult = {
      nodeId: node.id,
      issues: [],
      inputTables: [canvasTable('orders')],
      outputTables: [],
    };
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        validation={validation}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await openFirstProcessorOperation();
    expect(screen.getByText('删除空值行')).toBeInTheDocument();
    expect(screen.getAllByText('固定值填充')).toHaveLength(2);
    fireEvent.click(screen.getByRole('button', { name: '上移规则 2' }));
    await waitFor(() => {
      expect(document.querySelectorAll('.canvas-processor-rule-card')[0])
        .toHaveTextContent('固定值填充');
    });
    fireEvent.click(screen.getByRole('button', { name: '保存此项' }));
    await waitFor(() => expect(screen.queryByText('配置处理表 · orders')).not.toBeInTheDocument());
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: node.id,
      type: CanvasNodeType.NullHandling,
      configuration: {
        operations: [{
          ...node.configuration.operations![0],
          rules: [
            node.configuration.operations![0].rules[1],
            node.configuration.operations![0].rules[0],
          ],
        }],
      },
    });
  });

  it('loads and applies VALUE_MAPPING without exposing literals in node metadata', async () => {
    const node: Extract<CanvasNodeDefinition, { type: 'VALUE_MAPPING' }> = {
      id: '3e677169-a39f-49c2-8ba1-cd180a927aaf',
      type: CanvasNodeType.ValueMapping,
      name: '值映射',
      layout: { x: 320, y: 20, width: 240, height: 120 },
      configuration: {
        operations: [{
          operationId: 'd06ea816-ab53-48b0-8fc6-f6156ba9b6ee',
          sourceTableName: 'orders',
          output: { mode: 'CREATE_NEW_TABLE', outputTableName: 'orders_mapped' },
          rules: [{
            columnName: 'customer_id',
            entries: [{
              sourceValue: { dataType: 'LONG', value: '1' },
              targetValue: { dataType: 'LONG', value: '1001' },
            }],
            unmatchedStrategy: 'KEEP',
            unmatchedValue: null,
          }],
        }],
        sourceTableName: 'orders',
        outputTableName: 'orders_mapped',
        rules: [{
          columnName: 'customer_id',
          entries: [{
            sourceValue: { dataType: 'LONG', value: '1' },
            targetValue: { dataType: 'LONG', value: '1001' },
          }],
          unmatchedStrategy: 'KEEP',
          unmatchedValue: null,
        }],
      },
    };
    const validation: CanvasNodeValidationResult = {
      nodeId: node.id,
      issues: [],
      inputTables: [canvasTable('orders')],
      outputTables: [],
    };
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        validation={validation}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await openFirstProcessorOperation();
    expect(screen.getByText('customer_id')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('输入原值')).toHaveValue('1');
    expect(screen.getByPlaceholderText('输入目标值')).toHaveValue('1001');
    expect(screen.getByText('未匹配非 NULL 值')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '保存此项' }));
    await waitFor(() => expect(screen.queryByText('配置处理表 · orders')).not.toBeInTheDocument());
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: node.id,
      type: CanvasNodeType.ValueMapping,
      configuration: { operations: node.configuration.operations },
    });
  });

  it('loads and applies a bounded WINDOW definition', async () => {
    const node: Extract<CanvasNodeDefinition, { type: 'WINDOW' }> = {
      id: '71254d23-5960-481e-8563-32f98171fdbe',
      type: CanvasNodeType.Window,
      name: '窗口计算',
      layout: { x: 320, y: 20, width: 240, height: 120 },
      configuration: {
        sourceTableName: 'orders',
        outputTableName: 'orders_ranked',
        partitionByColumns: ['customer_id'],
        orderBy: [{
          columnName: 'ordered_at',
          direction: 'DESC',
          nullOrdering: 'LAST',
        }],
        functions: [{
          kind: 'ROW_NUMBER',
          outputColumnName: 'order_rank',
        }],
      },
    };
    const validation: CanvasNodeValidationResult = {
      nodeId: node.id,
      issues: [],
      inputTables: [canvasTable('orders')],
      outputTables: [],
    };
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        validation={validation}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    expect(screen.getByText('ROW_NUMBER')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '排序只用于窗口计算' })).toBeInTheDocument();
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: node.id,
      type: CanvasNodeType.Window,
      configuration: node.configuration,
    });
  });

  it('loads and applies partitioned TOP_N and blocks it in streaming mode', async () => {
    const node: Extract<CanvasNodeDefinition, { type: 'TOP_N' }> = {
      id: 'e23faece-0f77-4dc9-a18e-67c0193bf9f1',
      type: CanvasNodeType.TopN,
      name: 'Top N',
      layout: { x: 320, y: 20, width: 240, height: 120 },
      configuration: {
        operations: [{
          operationId: '78304473-a7f7-4dbb-b540-b180bdc33d40',
          sourceTableName: 'orders',
          output: { mode: 'CREATE_NEW_TABLE', outputTableName: 'top_orders' },
          partitionByColumns: ['customer_id'],
          orderBy: [{
            columnName: 'amount',
            direction: 'DESC',
            nullOrdering: 'LAST',
          }],
          limit: 3,
          tieStrategy: 'WITH_TIES',
        }],
        sourceTableName: 'orders',
        outputTableName: 'top_orders',
        partitionByColumns: ['customer_id'],
        orderBy: [{
          columnName: 'amount',
          direction: 'DESC',
          nullOrdering: 'LAST',
        }],
        limit: 3,
        tieStrategy: 'WITH_TIES',
      },
    };
    const validation: CanvasNodeValidationResult = {
      nodeId: node.id,
      issues: [],
      inputTables: [canvasTable('orders')],
      outputTables: [],
    };
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();
    const { rerender } = render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        validation={validation}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await waitForInspector();

    await openFirstProcessorOperation();
    expect(screen.getByText('每组前 N')).toBeInTheDocument();
    expect(screen.getByText('保留并列')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '保存此项' }));
    await waitFor(() => expect(screen.queryByText('配置处理表 · orders')).not.toBeInTheDocument());
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: node.id,
      type: CanvasNodeType.TopN,
      configuration: { operations: node.configuration.operations },
    });

    onApply.mockClear();
    rerender(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={node}
        executionMode="STREAMING"
        validation={validation}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    await openFirstProcessorOperation();
    expect(await screen.findByText('Top N 仅支持批处理')).toBeInTheDocument();
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledTimes(1);
  });
});
