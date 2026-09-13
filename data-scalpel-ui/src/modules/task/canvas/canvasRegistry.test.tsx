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
        tables: [{
          fileDatasetTableId: '04aee9c7-1877-47b4-a988-dd2968e4a85c',
          tableName: '七月订单',
          tableCode: 'orders_202607',
          datasetType: 'SHAPEFILE',
          status: 'READY',
          schema: {
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
          },
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
        ...canvasNodeRegistry.require(CanvasNodeType.Rename).createDefaultConfiguration(),
        operations: [{
          operationId: '11111111-1111-4111-8111-111111111111',
          sourceTableName: 'orders',
          output: { mode: 'REPLACE_SOURCE', outputTableName: 'source_orders' },
          columnMappings: [
            { sourceColumnName: 'id', targetColumnName: 'order_id' },
            { sourceColumnName: 'customer_id', targetColumnName: 'buyer_id' },
          ],
        }],
      },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getAllByText('orders')).not.toHaveLength(0);
    expect(screen.getAllByText('source_orders')).not.toHaveLength(0);
    expect(screen.getAllByText('2 个字段')).not.toHaveLength(0);
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
    expect(canvasNodeTemplates).toHaveLength(Object.values(CanvasNodeType).length);
    expect(new Set(canvasNodeTemplates.map((template) => template.type)))
      .toEqual(new Set(Object.values(CanvasNodeType)));
    expect(canvasNodeTemplates.every((template) => template.supportedModes.length > 0)).toBe(true);
    expect(canvasNodeTemplates.every((template) => template.description.length > 0)).toBe(true);
    expect(canvasNodeTemplates.every((template) => template.searchKeywords.length > 0)).toBe(true);
    expect(new Set(canvasNodeTemplates
      .filter((template) => template.supportedModes.includes('STREAMING'))
      .map((template) => template.type)))
      .toEqual(new Set([
        CanvasNodeType.JdbcInput,
        CanvasNodeType.JdbcIncrementalInput,
        CanvasNodeType.TdEngineTmqInput,
        CanvasNodeType.JdbcQueryInput,
        CanvasNodeType.KafkaInput,
        CanvasNodeType.StreamJoin,
        CanvasNodeType.GeometryConstruct,
        CanvasNodeType.GeometryDerive,
        CanvasNodeType.GeometrySimplify,
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
    const spec = canvasNodeRegistry.require(CanvasNodeType.JdbcInput);
    const baseSize = spec.canvasView.resolveSize({ dataSourceId: '', tables: [] });
    expect(resize).toHaveBeenCalledWith(baseSize.width, baseSize.height + 28, { canvasPresentationUpdate: true });
    expect(spec.canvasView.resolveSize({ dataSourceId: '', tables: [] })).toEqual(baseSize);
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
          ...canvasNodeRegistry.require(CanvasNodeType.Filter).createDefaultConfiguration(),
          operations: [{
            operationId: '11111111-1111-4111-8111-111111111111',
            sourceTableName: 'orders',
            output: { mode: 'CREATE_NEW_TABLE', outputTableName: 'valid_orders' },
            mode: 'STRUCTURED',
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
            sqlExpression: '',
          }],
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
          joinType: 'LEFT',
          conditions: [{
            leftGeometryColumnName: 'order_geom',
            predicate: 'WITHIN',
            rightGeometryColumnName: 'district_geom',
          }],
          attributeConditions: [{
            leftColumnName: 'tenant_id',
            operator: 'EQUALS',
            rightColumnName: 'tenant_id',
          }],
          outputColumns: [
            { sourceSide: 'LEFT', sourceColumnName: 'id', outputColumnName: 'id', included: true },
            { sourceSide: 'RIGHT', sourceColumnName: 'id', outputColumnName: 'districts_id', included: true },
          ],
          joinOperation: 'JOIN_ONE_TO_MANY',
          temporalCondition: {
            relationship: 'NEAR_BEFORE',
            leftStartColumnName: 'private_target_start',
            leftEndColumnName: 'private_target_end',
            rightStartColumnName: 'private_join_start',
            rightEndColumnName: 'private_join_end',
            nearDistance: 314159,
            nearDistanceUnit: 'SECONDS',
          },
        },
      },
      {
        type: CanvasNodeType.SpatialMultiVariableGrid,
        name: '城市多变量格网',
        configuration: {
          ...canvasNodeRegistry.require(CanvasNodeType.SpatialMultiVariableGrid)
            .createDefaultConfiguration(),
          outputTableName: 'city_grid',
          variables: [{
            variableId: '66666666-6666-4666-8666-666666666661',
            sourceTableName: 'facilities',
            geometryColumnName: 'shape',
            kind: 'DISTANCE_TO_NEAREST',
            attributeColumnName: null,
            statisticKind: null,
            statisticColumnName: null,
            searchDistance: 2,
            searchDistanceUnit: 'KILOMETERS',
            filter: {
              kind: 'PREDICATE', columnName: 'private_status', operator: 'EQUALS',
              values: [{ dataType: 'STRING', value: 'SECRET_LITERAL' }],
            },
            outputColumnName: 'nearest_distance',
          }, {
            variableId: '66666666-6666-4666-8666-666666666662',
            sourceTableName: 'events',
            geometryColumnName: 'shape',
            kind: 'ATTRIBUTE_SUMMARY_OF_RELATED',
            attributeColumnName: null,
            statisticKind: 'COUNT',
            statisticColumnName: null,
            searchDistance: null,
            searchDistanceUnit: null,
            filter: null,
            outputColumnName: 'event_count',
          }],
        },
      },
      {
        type: CanvasNodeType.SpatialEnrichFromGrid,
        name: '丰富事件点',
        configuration: {
          pointTableName: 'events',
          pointGeometryColumnName: 'shape',
          gridTableName: 'city_grid',
          gridGeometryColumnName: 'bin_geometry',
          gridIdColumnName: 'bin_id',
          enrichFields: [
            { sourceColumnName: 'nearest_distance', outputColumnName: 'nearest_distance' },
            { sourceColumnName: 'population_sum', outputColumnName: 'grid_population_sum' },
          ],
          outputTableName: 'enriched_events',
        },
      },
      {
        type: CanvasNodeType.SpatialGroupByProximity,
        name: '邻近事件组',
        configuration: {
          ...canvasNodeRegistry.require(CanvasNodeType.SpatialGroupByProximity)
            .createDefaultConfiguration(),
          sourceTableName: 'events',
          geometryColumnName: 'shape',
          spatialRelationship: 'NEAR_PLANAR',
          spatialNearDistance: 500,
          spatialNearDistanceUnit: 'METERS',
          temporalCondition: {
            relationship: 'NEAR', startColumnName: 'event_at', endColumnName: null,
            nearDistance: 10, nearDistanceUnit: 'MINUTES',
          },
          attributeConditions: [{
            columnName: 'private_region', relationship: 'EQUALS', maximumDifference: null,
          }],
          groupIdColumnName: 'group_id',
          outputTableName: 'event_groups',
        },
      },
      {
        type: CanvasNodeType.TraceProximityEvents,
        name: '追踪邻近事件',
        configuration: {
          ...canvasNodeRegistry.require(CanvasNodeType.TraceProximityEvents)
            .createDefaultConfiguration(),
          sourceTableName: 'device_observations',
          pointGeometryColumnName: 'shape',
          entityIdColumnName: 'device_id',
          timeColumnName: 'observed_at',
          distanceMethod: 'PLANAR',
          spatialSearchDistance: 15,
          spatialSearchDistanceUnit: 'METERS',
          temporalSearchDistance: 5,
          temporalSearchDistanceUnit: 'MINUTES',
          entitiesOfInterest: [{ entityId: 'PRIVATE-ENTITY-ID', startEpochMillis: null }],
          maxTraceDepth: 3,
          attributeMatchColumns: ['building'],
          includeTracks: true,
          outputTableName: 'trace_events',
          tracksOutputTableName: 'trace_tracks',
        },
      },
      {
        type: CanvasNodeType.SnapTracks,
        name: '吸附轨迹',
        configuration: {
          ...canvasNodeRegistry.require(CanvasNodeType.SnapTracks)
            .createDefaultConfiguration(),
          pointTableName: 'vehicle_observations',
          pointGeometryColumnName: 'shape',
          trackIdColumns: ['vehicle_id'],
          timeColumnName: 'observed_at',
          lineTableName: 'road_network',
          lineGeometryColumnName: 'shape',
          lineIdColumnName: 'road_id',
          fromNodeColumnName: 'from_node',
          toNodeColumnName: 'to_node',
          searchDistance: 314159,
          searchDistanceUnit: 'KILOMETERS',
          distanceMethod: 'GEODESIC',
          directionMatching: {
            directionColumnName: 'private_direction',
            forwardValue: 'PRIVATE_FORWARD',
            backwardValue: 'PRIVATE_BACKWARD',
            bothValue: 'PRIVATE_BOTH',
            noneValue: 'PRIVATE_NONE',
          },
          lineFields: [{ sourceColumnName: 'road_class', outputColumnName: 'matched_road_class' }],
          outputMode: 'MATCHED_FEATURES',
          outputTableName: 'snapped_tracks',
        },
      },
    ];
    const expectedTexts = [
      ['INNER JOIN', 'customer_id', 'id'],
      ['FILTER', 'valid_orders', '生成新表', '1 个条件', '1 条筛选'],
      ['SUM(amount)', 'total_amount'],
      ['ROW_NUMBER', 'customer_id'],
      ['SPATIAL JOIN', 'LEFT', '1:N', 'WITHIN', 'district_geom', '1 拓扑 · 1 属性', '时间 NEAR_BEFORE', '2 字段'],
      ['统一格网', '2 张来源表', 'facilities', 'nearest_distance · 最近距离',
        'events', 'event_count · 关联汇总', '1 个半径搜索', '1 个筛选'],
      ['Point', 'events', '变量格网', 'city_grid', '相交回填', 'enriched_events',
        'nearest_distance', 'population_sum', '2 个丰富字段', '未命中保留'],
      ['events', '连通分组', 'event_groups', '平面邻近', '时间', '邻近',
        '属性 1 项', '传递闭包', 'group_id', '原要素保留'],
      ['device_observations', '邻近传播', 'trace_events', '平面', '空间 15 米 · 时间 5 分钟',
        '1 个起始实体', '最多传播 3 层', '首次接触', '1 个同值字段', '含后续轨迹'],
      ['vehicle_observations', '路网吸附', 'snapped_tracks', 'road_network',
        'shape · road_id', '测地线', '搜索范围单位 · 千米', '1 个轨迹标识',
        '方向匹配', '1 个道路属性', '仅匹配观测'],
    ];

    nodes.forEach((data, index) => {
      const { node } = createNode(data);
      const { unmount } = render(<CanvasNodeView node={node} />);
      expectedTexts[index].forEach((content) => {
        expect(screen.getAllByText(content)).not.toHaveLength(0);
      });
      expect(screen.queryByText('SECRET_LITERAL')).not.toBeInTheDocument();
      expect(screen.queryByText('PRIVATE-ENTITY-ID')).not.toBeInTheDocument();
      expect(screen.queryByText(/314159|PRIVATE_FORWARD|PRIVATE_BACKWARD|PRIVATE_BOTH|PRIVATE_NONE/))
        .not.toBeInTheDocument();
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
          sourceTableName: 'orders',
          targetTableName: 'dwd_orders',
          writeMode: 'UPSERT',
          upsertKeyColumns: ['id'],
          columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'order_id' }],
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
          sourceTableName: 'orders',
          topic: 'order-events',
          valueSchema: { columns: [canvasColumn('id', 'LONG', false)] },
          keyColumnName: 'id',
          columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'id' }],
          writes: [{
            writeId: '22222222-2222-4222-8222-222222222222',
            sourceTableName: 'orders',
            topic: 'order-events',
            valueFormat: null,
            valueColumnNames: [],
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
          sourceTableName: 'districts',
          targetPath: 'exports/districts',
          conflictPolicy: 'FAIL_IF_EXISTS',
          formatOptions: {
            type: 'GEOPARQUET',
            geometryColumnName: 'geom',
            compression: 'ZSTD',
            coveringMode: 'ROW_BBOX',
          },
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
      ['order-events', '旧版 JSON · KEY id', '1 个子查询'],
      ['GEOPARQUET · FAIL_IF_EXISTS', 'exports/districts', '1 个写入'],
    ];

    nodes.forEach((data, index) => {
      const { node } = createNode(data);
      const { unmount } = render(<CanvasNodeView node={node} />);
      expectedTexts[index].forEach((content) => {
        expect(screen.getAllByText(content)).not.toHaveLength(0);
      });
      if (data.type === CanvasNodeType.SpatialJoin) {
        expect(screen.queryByText(/private_/)).not.toBeInTheDocument();
        expect(screen.queryByText(/314159/)).not.toBeInTheDocument();
      }
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
          sourceTableName: 'districts',
          targetPath: 'exports/districts',
          conflictPolicy: 'FAIL_IF_EXISTS',
          formatOptions: options,
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
    const randomUuid = vi.spyOn(globalThis.crypto, 'randomUUID')
      .mockReturnValue('00000000-0000-4000-8000-000000000000');
    try {
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
    } finally {
      randomUuid.mockRestore();
    }
  });
});
