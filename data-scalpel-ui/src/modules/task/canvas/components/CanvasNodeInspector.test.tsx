import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createRef } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { buildDataSourceSearch, useDataSourceTables, useTableMetadata } from '../../../datasource';
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
      purposes: ['STORAGE'],
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
      result.outputTables = [node.configuration.tableName === 'customers' ? customers : orders];
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

vi.mock('../../../datasource', () => {
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
      indexes: [],
    };
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
    useDataSourceTables: vi.fn((id: string | undefined) => ({
      data: { tables: tablesFor(id), truncated: metadataFixtures.truncated },
      isFetching: false,
      isError: false,
      refetch: vi.fn(),
    })),
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
  usePhysicalTableInspection: vi.fn((id: string | undefined) => ({
    data: id ? {
      mode: modelFixtures.details.find((detail) => detail.model.id === id)?.model.physicalTableMode ?? 'MANAGED',
      state: 'MATCHED',
      catalogName: 'demo',
      schemaName: 'dw',
      tableName: modelFixtures.details.find((detail) => detail.model.id === id)?.model.code ?? '',
      exists: true,
      compatible: true,
      createSupported: true,
      message: '物理表结构与模型一致',
      differences: [],
    } : undefined,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  })),
}));

const modelInputNode = (modelId: string): Extract<CanvasNodeDefinition, { type: 'MODEL_INPUT' }> => ({
  id: 'd52cf848-e6d9-46e1-813e-0e96eafdb463',
  type: CanvasNodeType.ModelInput,
  name: '模型输入',
  layout: { x: 80, y: 80, width: 240, height: 120 },
  configuration: { modelId },
});

const modelOutputNode = (
  targetModelId: string,
): Extract<CanvasNodeDefinition, { type: 'MODEL_OUTPUT' }> => ({
  id: '4fb252a7-4987-4178-afc1-9f55b8559779',
  type: CanvasNodeType.ModelOutput,
  name: '模型输出',
  layout: { x: 800, y: 180, width: 240, height: 120 },
  configuration: {
    sourceTableName: 'order_customer',
    targetModelId,
    writeMode: 'APPEND',
    columnMappingMode: 'EXPLICIT',
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
      (node) => node.type === CanvasNodeType.JdbcInput && node.configuration.tableName === 'orders',
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

    expect(screen.getByText('order_id')).toBeInTheDocument();
    expect(screen.queryByLabelText('节点名称')).not.toBeInTheDocument();
    await selectAntOption('物理表', 'payments');
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
          tableName: 'payments',
        },
      }));
      expect(onDirtyChange).toHaveBeenLastCalledWith(false);
    });
  });

  it('keeps invalid values unapplied and reports validation failure through the handle', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const input = definition.nodes.find((node) => node.type === CanvasNodeType.JdbcInput);
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();
    expect(input).toBeDefined();
    if (!input) return;
    input.configuration = { dataSourceId: '', tableName: '' };

    render(
      <CanvasNodeInspector
        ref={inspectorRef}
        node={input}
        validation={validation.nodeResults.get(input.id)}
        onApply={onApply}
        onDirtyChange={vi.fn()}
      />,
    );
    let applied: boolean | undefined;
    await act(async () => {
      applied = await inspectorRef.current?.apply();
    });

    expect(applied).toBe(false);
    expect(onApply).not.toHaveBeenCalled();
  });

  it('rejects sensitive HTTP API runtime parameter names before applying the node', async () => {
    const node: Extract<CanvasNodeDefinition, { type: 'HTTP_API_INPUT' }> = {
      id: '97f9ff44-95c1-4bcc-bc4f-afb39e238803',
      type: CanvasNodeType.HttpApiInput,
      name: 'HTTP API 输入',
      layout: { x: 80, y: 80, width: 240, height: 120 },
      configuration: {
        dataSourceId: '',
        resourceId: '',
        outputTableName: 'api_orders',
        runtimeParameters: [{ name: 'accessToken', value: 'must-not-be-persisted' }],
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

    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(false);
    });

    expect(onApply).not.toHaveBeenCalled();
    expect(await screen.findByText('运行时参数不能用于密码、Token、API Key、Secret 或签名')).toBeInTheDocument();
  });

  it('keeps the selected table visible when the data source changes and blocks an invalid combination', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const input = definition.nodes.find(
      (node) => node.type === CanvasNodeType.JdbcInput && node.configuration.tableName === 'orders',
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

    await selectAntOption('来源数据源', '归档 PostgreSQL');
    expect(screen.getByLabelText('来源数据源').closest('.ant-select-content'))
      .toHaveAttribute('title', '归档 PostgreSQL');
    await waitFor(() => {
      expect(vi.mocked(useTableMetadata).mock.calls.at(-1)?.[0]).toBe(metadataFixtures.archiveSourceId);
    });
    expect(screen.getByLabelText('物理表').closest('.ant-select-content')).toHaveAttribute('title', 'orders');
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(false);
    });
    expect(onApply).not.toHaveBeenCalled();
    expect(await screen.findByText('该物理表不存在或不属于当前数据源')).toBeInTheDocument();
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

    fireEvent.mouseDown(screen.getByLabelText('物理表'));
    expect(await screen.findByText('结果超过 500 项，请输入表名继续筛选')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '刷新物理表' })).toBeInTheDocument();
  });

  it('debounces physical-table input into a remote keyword query', () => {
    vi.useFakeTimers();
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

      fireEvent.change(screen.getByLabelText('物理表'), { target: { value: 'pay' } });
      act(() => vi.advanceTimersByTime(300));
      expect(vi.mocked(useDataSourceTables).mock.calls.at(-1)?.[1])
        .toEqual({ keyword: 'pay', includeViews: false });
    } finally {
      vi.useRealTimers();
    }
  });

  it('uses Task Engine input tables for Join table and field selections', () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const join = definition.nodes.find((node) => node.type === CanvasNodeType.Join);
    expect(join).toBeDefined();
    if (!join) return;

    render(<CanvasNodeInspector node={join} validation={validation.nodeResults.get(join.id)} onApply={vi.fn()} onDirtyChange={vi.fn()} />);

    expect(screen.getAllByText('orders').length).toBeGreaterThan(0);
    expect(screen.getAllByText('customers').length).toBeGreaterThan(0);
    expect(screen.getByText('条件 1')).toBeInTheDocument();
    const addCondition = screen.getByRole('button', { name: /添加 Join 条件/ });
    fireEvent.click(addCondition);
    expect(screen.getByText('条件 2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '删除条件 2' }));
    expect(screen.queryByText('条件 2')).not.toBeInTheDocument();
  });

  it('configures Rename from Task Engine tables and keeps mappings as one atomic operation', async () => {
    const rename: Extract<CanvasNodeDefinition, { type: 'RENAME' }> = {
      id: 'ee30aa02-b663-4d1c-95ca-3e63246113ae',
      type: CanvasNodeType.Rename,
      name: '订单重命名',
      layout: { x: 320, y: 20, width: 240, height: 120 },
      configuration: {
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

    expect(screen.getByDisplayValue('source_orders')).toBeInTheDocument();
    expect(screen.getByText(/所有映射同时生效/)).toBeInTheDocument();
    expect(screen.getByText('字段 1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /添加字段重命名/ }));
    expect(screen.getByText('字段 2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '删除字段映射 2' }));
    expect(screen.queryByText('字段 2')).not.toBeInTheDocument();

    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: rename.id,
      type: CanvasNodeType.Rename,
      configuration: rename.configuration,
    });
  });

  it('does not invent Join input tables when Task Engine has no current result', () => {
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

    expect(screen.getByText('Task Engine 尚未完成校验')).toBeInTheDocument();
    expect(screen.getByText('Task Engine 校验请求失败')).toBeInTheDocument();
    expect(screen.getByLabelText('左表')).toBeDisabled();
    expect(screen.getByLabelText('右表')).toBeDisabled();
  });

  it('keeps an invalid upstream table selection visible after schemas change', () => {
    const definition = exampleCanvasDefinition();
    const join = definition.nodes.find((node) => node.type === CanvasNodeType.Join);
    expect(join).toBeDefined();
    if (!join || join.type !== CanvasNodeType.Join) return;
    join.configuration.leftTableName = 'archived_orders';
    const validation = engineValidationFixture(definition);

    render(<CanvasNodeInspector node={join} validation={validation.nodeResults.get(join.id)} onApply={vi.fn()} onDirtyChange={vi.fn()} />);

    expect(screen.getByText('archived_orders')).toBeInTheDocument();
    expect(screen.queryByText('TABLE_NOT_FOUND')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '展开问题详情' }));
    expect(screen.getByText('TABLE_NOT_FOUND')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '收起问题详情' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('delegates BY_NAME output mapping validation to Task Engine', () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const output = definition.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    expect(output).toBeDefined();
    if (!output) return;

    render(<CanvasNodeInspector node={output} validation={validation.nodeResults.get(output.id)} onApply={vi.fn()} onDirtyChange={vi.fn()} />);

    expect(screen.getByText('BY_NAME 映射由 Task Engine 校验')).toBeInTheDocument();
    expect(screen.getByText(/Task Engine 会按照同名字段生成映射/)).toBeInTheDocument();
  });

  it('only accepts data-storage data sources for JDBC output', async () => {
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

    fireEvent.mouseDown(screen.getByLabelText('目标数据源'));
    expect(vi.mocked(buildDataSourceSearch)).toHaveBeenCalledWith({
      keyword: '',
      purpose: 'STORAGE',
      enabled: true,
    });
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(false);
    });
    expect(onApply).not.toHaveBeenCalled();
    expect(await screen.findByText('数据源不存在、已停用或不具有数据存储用途')).toBeInTheDocument();
  });

  it('switches the output inspector to editable explicit mappings', async () => {
    const definition = exampleCanvasDefinition();
    const validation = engineValidationFixture(definition);
    const output = definition.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    expect(output).toBeDefined();
    if (!output) return;

    render(<CanvasNodeInspector node={output} validation={validation.nodeResults.get(output.id)} onApply={vi.fn()} onDirtyChange={vi.fn()} />);

    await selectAntOption('字段映射模式', 'EXPLICIT · 显式映射');
    const addMapping = await screen.findByRole('button', { name: /添加字段映射/ });
    fireEvent.click(addMapping);
    expect(screen.getByText('字段映射 1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '删除字段映射 1' }));
    expect(screen.queryByText('字段映射 1')).not.toBeInTheDocument();
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

    expect(screen.getByText('模型详情 · 订单客户模型')).toBeInTheDocument();
    expect(screen.getByText('order_customer_model')).toBeInTheDocument();
    expect(screen.getByText('demo.dw.order_customer_model')).toBeInTheDocument();
    expect(screen.getByText('订单ID')).toBeInTheDocument();

    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: node.id,
      type: CanvasNodeType.ModelInput,
      configuration: { modelId: modelFixtures.managedId },
    });
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);
  });

  it('retains a disabled ModelInput selection and blocks applying it', async () => {
    const node = modelInputNode(modelFixtures.disabledId);
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

    expect(screen.getByText(/模型当前状态为已停用/)).toBeInTheDocument();
    expect(screen.getByText(/已停用订单模型（disabled_order_model）/)).toBeInTheDocument();
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(false);
    });
    expect(onApply).not.toHaveBeenCalled();
  });

  it('blocks OVERWRITE for an EXTERNAL target model without rewriting the old value', async () => {
    const node = modelOutputNode(modelFixtures.externalId);
    node.configuration.writeMode = 'OVERWRITE';
    node.configuration.columnMappingMode = 'BY_NAME';
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

    expect(screen.getByText('EXTERNAL 模型不允许 OVERWRITE')).toBeInTheDocument();
    expect(screen.getByLabelText('写入模式').closest('.ant-select-content'))
      .toHaveAttribute('title', 'OVERWRITE · EXTERNAL 模型不可用');
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(false);
    });
    expect(onApply).not.toHaveBeenCalled();
  });

  it('builds explicit mappings using exact field-code equality only', async () => {
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

    fireEvent.click(screen.getByRole('button', { name: '精确同名匹配' }));
    expect(await screen.findByText('字段映射 1')).toBeInTheDocument();
    expect(screen.queryByText('字段映射 2')).not.toBeInTheDocument();
    expect(onDirtyChange).toHaveBeenLastCalledWith(true);
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: node.id,
      type: CanvasNodeType.ModelOutput,
      configuration: {
        sourceTableName: 'order_customer',
        targetModelId: modelFixtures.managedId,
        writeMode: 'APPEND',
        columnMappingMode: 'EXPLICIT',
        columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'order_id' }],
      },
    });
  });

  it('keeps unavailable upstream tables and field mappings visible', () => {
    const node = modelOutputNode(modelFixtures.managedId);
    node.configuration.sourceTableName = 'archived_orders';
    node.configuration.columnMappings = [{
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

    expect(screen.getByText('archived_orders（上游已不可用）')).toBeInTheDocument();
    expect(screen.getByLabelText('来源字段映射 1').closest('.ant-select-content'))
      .toHaveAttribute('title', 'removed_source（来源字段已不可用）');
    expect(screen.getByLabelText('目标字段映射 1').closest('.ant-select-content'))
      .toHaveAttribute('title', 'removed_target（目标字段已不可用）');
  });
});
