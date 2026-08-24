import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { Node } from '@antv/x6';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeView, canvasNodeTemplates } from './canvasRegistry';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CanvasNodeCategory,
  CanvasNodeType,
  type CanvasColumnSchema,
  type FileOutputFormatOptions,
  type CanvasNodeRuntimeData,
} from './canvasTypes';
import { canvasNodeRegistry } from './nodes/nodeRegistry';
import { canvasNodeGroup } from './nodes/nodeGroups';
import { CANVAS_RUNTIME_NODE_SHAPE } from './nodes/nodeSpec';

vi.mock('@antv/x6-react-shape', () => ({ register: vi.fn() }));

const canvasColumn = (
  name: string,
  fieldType: CanvasColumnSchema['fieldType'],
  nullable: boolean,
  length: number | null = null,
): CanvasColumnSchema => ({
  name,
  fieldType,
  length,
  precision: null,
  scale: null,
  nullable,
  defaultValue: null,
  autoIncrement: false,
  generated: false,
  comment: null,
  geometry: null,
});

const createNode = (data: CanvasNodeRuntimeData) => {
  const setData = vi.fn();
  const resize = vi.fn();
  const node = {
    getData: () => data,
    getSize: () => ({ width: 240, height: 120 }),
    setData,
    resize,
    on: vi.fn(),
    off: vi.fn(),
    model: null,
  } as unknown as Node;
  return { node, resize, setData };
};

describe('CanvasNodeView', () => {
  afterEach(cleanup);

  it('uses the category header color without rendering a category tag', () => {
    const { node } = createNode({
      type: CanvasNodeType.JdbcInput,
      name: '订单来源',
      configuration: { dataSourceId: '', tables: [] },
    });

    const { container } = render(<CanvasNodeView node={node} />);

    expect(container.querySelector('.canvas-node-header-input')).toBeInTheDocument();
    expect(screen.queryByText('输入')).not.toBeInTheDocument();
  });

  it('renders the resolved real data-source and physical-table summary', () => {
    const { node } = createNode({
      type: CanvasNodeType.JdbcInput,
      name: '订单来源',
      configuration: { dataSourceId: 'real-source-id', tables: [{ tableName: 'orders', readOptions: [] }] },
      summary: {
        kind: 'JDBC',
        dataSourceName: '业务 PostgreSQL',
        dataSourceType: 'POSTGRESQL',
        qualifiedTableName: 'order_db.public.orders',
        primaryKeyColumns: ['order_id'],
      },
      compilation: {
        inputTables: [],
        outputTables: [{
          name: 'orders',
          origin: null,
          columns: [
            canvasColumn('description', 'STRING', true, 128),
            canvasColumn('created_at', 'TIMESTAMP', false),
            canvasColumn('order_id', 'LONG', false),
            canvasColumn('remark', 'STRING', true, 255),
          ],
          datasetKind: 'BOUNDED',
          eventTimeColumn: null,
          watermarkDelay: null,
        }],
      },
    });

    const { container } = render(<CanvasNodeView node={node} />);

    expect(screen.getByText('业务 PostgreSQL')).toBeInTheDocument();
    expect(screen.getByText('POSTGRESQL')).toBeInTheDocument();
    expect(screen.getAllByText('orders')).toHaveLength(1);
    expect([...container.querySelectorAll('.canvas-semantic-preview-label')].map(
      (element) => element.textContent,
    )).toEqual(['orders']);
    expect(screen.getByRole('button', { name: 'orders：4 字段' })).toBeInTheDocument();
    expect(screen.queryByText('order_id')).not.toBeInTheDocument();
    expect(screen.queryByText('created_at')).not.toBeInTheDocument();
    expect(screen.queryByText('READ')).not.toBeInTheDocument();
    expect(screen.queryByText('order_db.public.orders')).not.toBeInTheDocument();
  });

  it('renders model identity and schema version instead of the model UUID', () => {
    const { node } = createNode({
      type: CanvasNodeType.ModelInput,
      name: '订单模型输入',
      configuration: { models: [{ modelId: '04aee9c7-1877-47b4-a988-dd2968e4a85c' }] },
      summary: {
        kind: 'MODEL',
        modelName: '订单模型',
        modelCode: 'order_model',
        modelSchemaVersion: 7,
        dataSourceName: '模型仓库',
        qualifiedTableName: 'order_model',
      },
      compilation: {
        inputTables: [],
        outputTables: [{
          name: 'order_model',
          origin: {
            kind: 'MODEL',
            dataSourceId: null,
            tableName: null,
            modelId: '04aee9c7-1877-47b4-a988-dd2968e4a85c',
            modelCode: 'order_model',
            modelSchemaVersion: 7,
          },
          columns: [canvasColumn('id', 'LONG', false)],
          datasetKind: 'BOUNDED',
          eventTimeColumn: null,
          watermarkDelay: null,
        }],
      },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getByText('订单模型')).toBeInTheDocument();
    expect(screen.getByText('order_model')).toBeInTheDocument();
    expect(screen.getByText('v7')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'order_model：1 字段' })).toBeInTheDocument();
    expect(screen.queryByText('warehouse.public.order_model')).not.toBeInTheDocument();
    expect(screen.queryByText(/04aee9c7/)).not.toBeInTheDocument();
  });

  it('renders the resolved file dataset table summary', () => {
    const { node } = createNode({
      type: CanvasNodeType.FileDatasetInput,
      name: '订单文件输入',
      configuration: { fileDatasetId: '04aee9c7-1877-47b4-a988-dd2968e4a85c', tables: [{ fileDatasetTableId: '04aee9c7-1877-47b4-a988-dd2968e4a85c' }] },
      summary: {
        kind: 'FILE_DATASET',
        fileDatasetName: '订单归档',
        tableName: '七月订单',
        tableCode: 'orders_202607',
        datasetType: 'SHAPEFILE',
        status: 'READY',
        geometry: {
          fieldName: '_geometry',
          kind: 'POINT',
          crs: { authority: 'EPSG', code: 4326 },
          dimension: 'XY',
        },
      },
      compilation: {
        inputTables: [],
        outputTables: [{
          name: 'orders_202607',
          origin: null,
          columns: [
            canvasColumn('id', 'LONG', false),
            {
              ...canvasColumn('_geometry', 'GEOMETRY', false),
              geometry: {
                kind: 'POINT',
                crs: { authority: 'EPSG', code: 4326 },
                dimension: 'XY',
              },
            },
          ],
          datasetKind: 'BOUNDED',
          eventTimeColumn: null,
          watermarkDelay: null,
        }],
      },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getByText('订单归档')).toBeInTheDocument();
    expect(screen.getByText('orders_202607')).toBeInTheDocument();
    expect(screen.getByText('SHAPEFILE')).toBeInTheDocument();
    expect(screen.getByText('POINT · EPSG:4326 · XY')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'orders_202607：2 字段' })).toBeInTheDocument();
  });

  it('renders the ModelOutput source, model identity and write mode', () => {
    const { node } = createNode({
      type: CanvasNodeType.ModelOutput,
      name: '订单模型输出',
      configuration: {
        writes: [{
          writeId: '11111111-1111-4111-8111-111111111111',
          sourceTableName: 'order_customer',
          targetModelId: '34ccdaed-bfa6-4afb-a085-7318153d75b1',
          writeMode: 'APPEND',
          columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'id' }],
        }],
      },
      summary: {
        kind: 'MODEL',
        modelName: '订单客户模型',
        modelCode: 'order_customer_model',
        modelSchemaVersion: 3,
        dataSourceName: '模型仓库',
        qualifiedTableName: 'order_customer_model',
      },
      compilation: {
        inputTables: [{
          name: 'order_customer',
          origin: null,
          columns: [canvasColumn('id', 'LONG', false), canvasColumn('name', 'STRING', true)],
          datasetKind: 'BOUNDED',
          eventTimeColumn: null,
          watermarkDelay: null,
        }],
        outputTables: [],
      },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getByText('订单客户模型')).toBeInTheDocument();
    expect(screen.getByText('order_customer_model · v3')).toBeInTheDocument();
    expect(screen.queryByText('warehouse.public.order_customer_model')).not.toBeInTheDocument();
    expect(screen.getAllByText('APPEND')).not.toHaveLength(0);
    expect(screen.getByRole('button', { name: 'order_customer：映射 1/2' })).toBeInTheDocument();
  });

  it('renders the Rename table transition and mapping count', () => {
    const { node } = createNode({
      type: CanvasNodeType.Rename,
      name: '订单重命名',
      configuration: {
        sourceTableName: 'orders',
        outputTableName: 'source_orders',
        columnMappings: [
          { sourceColumnName: 'id', targetColumnName: 'order_id' },
          { sourceColumnName: 'customer_id', targetColumnName: 'buyer_id' },
        ],
      },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getByText('orders')).toBeInTheDocument();
    expect(screen.getByText('source_orders')).toBeInTheDocument();
    expect(screen.getByText('2 个字段')).toBeInTheDocument();
  });

  it('renames from the title and restores the original name for empty input', () => {
    const { node, setData } = createNode({
      type: CanvasNodeType.Join,
      name: '订单关联',
      configuration: {
        leftTableName: '',
        rightTableName: '',
        outputTableName: '',
        joinType: null,
        conditions: [],
        outputColumns: [],
      },
    });
    render(<CanvasNodeView node={node} />);

    fireEvent.doubleClick(screen.getByRole('button', { name: '重命名节点 订单关联' }));
    const input = screen.getByLabelText('节点名称');
    fireEvent.change(input, { target: { value: '  订单客户关联  ' } });
    fireEvent.keyDown(input, { key: 'Enter' });

    expect(setData).toHaveBeenCalledWith({ name: '订单客户关联' }, { canvasNodeRename: true });

    fireEvent.doubleClick(screen.getByRole('button', { name: '重命名节点 订单关联' }));
    const emptyInput = screen.getByLabelText('节点名称');
    fireEvent.change(emptyInput, { target: { value: '   ' } });
    fireEvent.blur(emptyInput);
    expect(setData).toHaveBeenCalledTimes(1);
  });

  it('declares a non-empty execution mode set for every registered node', () => {
    expect(canvasNodeTemplates).toHaveLength(40);
    expect(canvasNodeTemplates.every((template) => template.supportedModes.length > 0)).toBe(true);
    expect(canvasNodeTemplates.every((template) => template.description.length > 0)).toBe(true);
    expect(canvasNodeTemplates.every((template) => template.searchKeywords.length > 0)).toBe(true);
    expect(new Set(canvasNodeTemplates
      .filter((template) => template.supportedModes.includes('STREAMING'))
      .map((template) => template.type)))
      .toEqual(new Set([
        CanvasNodeType.JdbcInput,
        CanvasNodeType.JdbcQueryInput,
        CanvasNodeType.KafkaInput,
        CanvasNodeType.StreamJoin,
        CanvasNodeType.GeometryConstruct,
        CanvasNodeType.GeometryValidate,
        CanvasNodeType.GeometryRepair,
        CanvasNodeType.GeometryBuffer,
        CanvasNodeType.GeometryExplode,
        CanvasNodeType.SpatialMeasure,
        CanvasNodeType.GeometrySerialize,
        CanvasNodeType.Rename,
        CanvasNodeType.Filter,
        CanvasNodeType.SelectColumns,
        CanvasNodeType.DeriveColumns,
        CanvasNodeType.TypeCast,
        CanvasNodeType.Union,
        CanvasNodeType.NullHandling,
        CanvasNodeType.ValueMapping,
        CanvasNodeType.MaskFields,
        CanvasNodeType.JsonExtract,
        CanvasNodeType.ModelOutput,
        CanvasNodeType.JdbcOutput,
        CanvasNodeType.KafkaOutput,
      ]));
    expect(canvasNodeTemplates
      .filter((template) => template.supportedModes.includes('BATCH'))
      .map((template) => template.type))
      .not.toContain(CanvasNodeType.KafkaInput);
    expect(canvasNodeTemplates.find((template) => template.type === CanvasNodeType.FileDatasetInput)
      ?.supportedModes)
      .toEqual(['BATCH']);
    expect(canvasNodeTemplates.find(
      (template) => template.type === CanvasNodeType.JdbcSnapshotSyncOutput,
    )?.supportedModes).toEqual(['BATCH']);
    expect(canvasNodeTemplates.find(
      (template) => template.type === CanvasNodeType.ModelSnapshotSyncOutput,
    )?.supportedModes).toEqual(['BATCH']);
  });

  it('renders every registered node with its own default semantic body', () => {
    canvasNodeRegistry.all().forEach((spec) => {
      const { node } = createNode(canvasNodeRegistry.createRuntimeData(spec.type));
      const { container, unmount } = render(<CanvasNodeView node={node} />);
      expect(container.querySelector('.canvas-node-body')).toBeInTheDocument();
      expect(container.querySelector('.canvas-semantic-empty')).toBeInTheDocument();
      unmount();
    });
  });

  it('derives list height and spatial file-output size from configuration', () => {
    const joinSpec = canvasNodeRegistry.require(CanvasNodeType.Join);
    const join = joinSpec.createDefaultConfiguration();
    expect(joinSpec.canvasView.resolveSize(join)).toEqual({ width: 360, height: 116 });
    expect(joinSpec.canvasView.resolveSize({
      ...join,
      leftTableName: 'orders',
      conditions: [{
        leftColumnName: 'customer_id',
        operator: 'EQUALS',
        rightColumnName: 'customer_id',
      }],
    })).toEqual({ width: 360, height: 192 });

    const fileOutputSpec = canvasNodeRegistry.require(CanvasNodeType.FileOutput);
    const fileOutput = fileOutputSpec.createDefaultConfiguration();
    expect(fileOutputSpec.canvasView.resolveSize({
      ...fileOutput,
      writes: [{
        writeId: '22222222-2222-4222-8222-222222222222',
        sourceTableName: 'districts',
        targetPath: 'exports/districts',
        conflictPolicy: 'OVERWRITE',
        formatOptions: {
          type: 'GEOPARQUET',
          geometryColumnName: 'geom',
          compression: 'ZSTD',
          coveringMode: 'ROW_BBOX',
        },
      }],
    })).toEqual({ width: 420, height: 136 });
  });

  it('adds issue height without serializing it into the base node size', () => {
    const { node, resize } = createNode({
      type: CanvasNodeType.JdbcInput,
      name: '订单来源',
      configuration: { dataSourceId: '', tables: [] },
      validation: { status: 'ERROR', message: '请选择物理表' },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getByText('请选择物理表')).toHaveClass('canvas-node-issue');
    expect(resize).toHaveBeenCalledWith(320, 132, { canvasPresentationUpdate: true });
  });

  it('renders representative configured processors without exposing literal values', () => {
    const nodes: CanvasNodeRuntimeData[] = [
      {
        type: CanvasNodeType.Join,
        name: '订单客户关联',
        configuration: {
          leftTableName: 'orders',
          rightTableName: 'customers',
          outputTableName: 'order_customer',
          joinType: 'INNER',
          conditions: [{
            leftColumnName: 'customer_id',
            operator: 'EQUALS',
            rightColumnName: 'id',
          }],
          outputColumns: [],
        },
      },
      {
        type: CanvasNodeType.Filter,
        name: '有效订单筛选',
        configuration: {
          sourceTableName: 'orders',
          outputTableName: 'valid_orders',
          condition: {
            kind: 'GROUP',
            operator: 'AND',
            children: [{
              kind: 'PREDICATE',
              columnName: 'status',
              operator: 'EQUALS',
              values: [{ dataType: 'STRING', value: 'SECRET_LITERAL' }],
            }],
          },
        },
      },
      {
        type: CanvasNodeType.Aggregate,
        name: '客户订单聚合',
        configuration: {
          sourceTableName: 'orders',
          outputTableName: 'customer_order_metrics',
          groupByColumns: ['customer_id'],
          aggregations: [{
            function: 'SUM',
            sourceColumnName: 'amount',
            outputColumnName: 'total_amount',
            distinct: false,
          }],
        },
      },
      {
        type: CanvasNodeType.Window,
        name: '客户订单排名',
        configuration: {
          sourceTableName: 'orders',
          outputTableName: 'ranked_orders',
          partitionByColumns: ['customer_id'],
          orderBy: [{ columnName: 'created_at', direction: 'DESC', nullOrdering: 'LAST' }],
          functions: [{ kind: 'ROW_NUMBER', outputColumnName: 'row_number' }],
        },
      },
      {
        type: CanvasNodeType.SpatialJoin,
        name: '订单归属区域',
        configuration: {
          leftTableName: 'orders',
          rightTableName: 'districts',
          outputTableName: 'district_orders',
          joinType: 'INNER',
          conditions: [{
            leftGeometryColumnName: 'order_geom',
            predicate: 'WITHIN',
            rightGeometryColumnName: 'district_geom',
          }],
        },
      },
    ];
    const expectedTexts = [
      ['INNER JOIN', 'customer_id', 'id'],
      ['FILTER', '1 个值', 'AND'],
      ['SUM(amount)', 'total_amount'],
      ['ROW_NUMBER', 'customer_id'],
      ['SPATIAL JOIN', 'WITHIN', 'district_geom'],
    ];

    nodes.forEach((data, index) => {
      const { node } = createNode(data);
      const { unmount } = render(<CanvasNodeView node={node} />);
      expectedTexts[index].forEach((content) => {
        expect(screen.getAllByText(content)).not.toHaveLength(0);
      });
      expect(screen.queryByText('SECRET_LITERAL')).not.toBeInTheDocument();
      unmount();
    });
  });

  it('renders configured JDBC, snapshot, Kafka and spatial file outputs', () => {
    const nodes: CanvasNodeRuntimeData[] = [
      {
        type: CanvasNodeType.JdbcOutput,
        name: '订单写库',
        configuration: {
          dataSourceId: 'jdbc-target',
          writes: [{
            writeId: '11111111-1111-4111-8111-111111111111',
            sourceTableName: 'orders',
            targetTableName: 'dwd_orders',
            writeMode: 'UPSERT',
            upsertKeyColumns: ['id'],
            columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'order_id' }],
          }],
        },
      },
      {
        type: CanvasNodeType.JdbcSnapshotSyncOutput,
        name: '订单快照同步',
        configuration: {
          sourceTableName: 'orders',
          dataSourceId: 'jdbc-target',
          targetTableName: 'snapshot_orders',
          keyColumns: ['id'],
          columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'id' }],
          deletePolicy: { action: 'DELETE', maxDeleteRows: 100, maxDeleteRatio: 0.2 },
        },
      },
      {
        type: CanvasNodeType.KafkaOutput,
        name: '订单事件输出',
        configuration: {
          dataSourceId: 'kafka-target',
          writes: [{
            writeId: '22222222-2222-4222-8222-222222222222',
            sourceTableName: 'orders',
            topic: 'order-events',
            valueSchema: { columns: [canvasColumn('id', 'LONG', false)] },
            keyColumnName: 'id',
            columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'id' }],
          }],
        },
      },
      {
        type: CanvasNodeType.FileOutput,
        name: '区域 GeoParquet 输出',
        configuration: {
          dataSourceId: 's3-target',
          writes: [{
            writeId: '33333333-3333-4333-8333-333333333333',
            sourceTableName: 'districts',
            targetPath: 'exports/districts',
            conflictPolicy: 'FAIL_IF_EXISTS',
            formatOptions: {
              type: 'GEOPARQUET',
              geometryColumnName: 'geom',
              compression: 'ZSTD',
              coveringMode: 'ROW_BBOX',
            },
          }],
        },
      },
    ];
    const expectedTexts = [
      ['UPSERT', 'dwd_orders', '1 个写入'],
      ['SNAPSHOT SYNC', 'DELETE', '100 行 · 0.2 比例'],
      ['order-events', 'KEY id', '1 个子查询'],
      ['GEOPARQUET · FAIL_IF_EXISTS', 'exports/districts', '1 个写入'],
    ];

    nodes.forEach((data, index) => {
      const { node } = createNode(data);
      const { unmount } = render(<CanvasNodeView node={node} />);
      expectedTexts[index].forEach((content) => {
        expect(screen.getAllByText(content)).not.toHaveLength(0);
      });
      unmount();
    });
  });

  it('renders every file-output format in a compact write row', () => {
    const formats: FileOutputFormatOptions[] = [
      {
        type: 'CSV',
        header: true,
        delimiter: ',',
        quote: '"',
        escape: '\\',
        nullValue: '',
      },
      { type: 'JSON_LINES', ignoreNullFields: true },
      { type: 'PARQUET' },
      {
        type: 'SHAPEFILE',
        baseName: 'districts',
        packageMode: 'ZIP',
        geometryColumnName: 'geom',
        targetShapeType: 'POLYGON',
        attributeMappings: [{
          sourceColumnName: 'district_id',
          targetFieldName: 'DIST_ID',
          targetStringByteLength: null,
        }],
      },
      {
        type: 'GEOPARQUET',
        geometryColumnName: 'geom',
        compression: 'SNAPPY',
        coveringMode: 'ROW_BBOX',
      },
      {
        type: 'GEOJSON',
        baseName: 'districts',
        geometryColumnName: 'geom',
        idColumnName: 'district_id',
        ignoreNullProperties: true,
      },
    ];

    formats.forEach((options, index) => {
      const data: CanvasNodeRuntimeData = {
        type: CanvasNodeType.FileOutput,
        name: `${options.type} 输出`,
        configuration: {
          dataSourceId: 's3-target',
          writes: [{
            writeId: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
            sourceTableName: 'districts',
            targetPath: 'exports/districts',
            conflictPolicy: 'FAIL_IF_EXISTS',
            formatOptions: options,
          }],
        },
      };
      const { node } = createNode(data);
      const { unmount } = render(<CanvasNodeView node={node} />);
      expect(screen.getAllByText(new RegExp(`^${options.type}`))).not.toHaveLength(0);
      expect(screen.getByText(
        'baseName' in options ? `exports/districts/${options.baseName}` : 'exports/districts',
      )).toBeInTheDocument();
      unmount();
    });
  });

  it('registers every stable node exactly once with coherent extension metadata', () => {
    const specs = canvasNodeRegistry.all();
    expect(specs).toHaveLength(Object.values(CanvasNodeType).length);
    expect(new Set(specs.map((spec) => spec.type)).size).toBe(specs.length);
    specs.forEach((spec) => {
      expect(canvasNodeGroup(spec.group).category).toBe(spec.category);
      expect(spec.introducedInMinor).toBeGreaterThanOrEqual(0);
      expect(spec.introducedInMinor).toBeLessThanOrEqual(CANVAS_SCHEMA_MINOR_VERSION);
      expect(spec.supportedModes.length).toBeGreaterThan(0);
      const size = canvasNodeRegistry.resolveSize(canvasNodeRegistry.createRuntimeData(spec.type));
      expect(size.width).toBeGreaterThanOrEqual(180);
      expect(size.height).toBeGreaterThanOrEqual(96);
      expect(spec.canvasView.Body).toBeTypeOf('function');
      expect(spec.createDefaultConfiguration()).toEqual(
        canvasNodeRegistry.createDefaultConfiguration(spec.type),
      );
    });
    expect(new Set(canvasNodeTemplates.map((template) => template.shape)))
      .toEqual(new Set([CANVAS_RUNTIME_NODE_SHAPE]));
    const processorSpecs = specs.filter((spec) => spec.category === CanvasNodeCategory.Processor);
    processorSpecs.forEach((spec) => {
      expect(spec.graph).toMatchObject({
        minInputs: 1,
        maxInputs: null,
        minOutputs: 0,
        maxOutputs: null,
      });
    });
  });
});
