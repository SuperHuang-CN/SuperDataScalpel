import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { Node } from '@antv/x6';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeView, canvasNodeTemplates } from './canvasRegistry';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CanvasNodeType,
  type CanvasNodeRuntimeData,
} from './canvasTypes';
import { canvasNodeRegistry } from './nodes/nodeRegistry';
import { canvasNodeGroup } from './nodes/nodeGroups';
import { CANVAS_RUNTIME_NODE_SHAPE } from './nodes/nodeSpec';

vi.mock('@antv/x6-react-shape', () => ({ register: vi.fn() }));

const createNode = (data: CanvasNodeRuntimeData) => {
  const setData = vi.fn();
  const node = {
    getData: () => data,
    getSize: () => ({ width: 240, height: 120 }),
    setData,
    on: vi.fn(),
    off: vi.fn(),
    model: null,
  } as unknown as Node;
  return { node, setData };
};

describe('CanvasNodeView', () => {
  afterEach(cleanup);

  it('uses the category header color without rendering a category tag', () => {
    const { node } = createNode({
      type: CanvasNodeType.JdbcInput,
      name: '订单来源',
      configuration: { dataSourceId: '', tableName: '' },
    });

    const { container } = render(<CanvasNodeView node={node} />);

    expect(container.querySelector('.canvas-node-header-input')).toBeInTheDocument();
    expect(screen.queryByText('输入')).not.toBeInTheDocument();
  });

  it('renders the resolved real data-source and physical-table summary', () => {
    const { node } = createNode({
      type: CanvasNodeType.JdbcInput,
      name: '订单来源',
      configuration: { dataSourceId: 'real-source-id', tableName: 'orders' },
      summary: {
        kind: 'JDBC',
        dataSourceName: '业务 PostgreSQL',
        qualifiedTableName: 'order_db.public.orders',
      },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getByText('业务 PostgreSQL · order_db.public.orders')).toBeInTheDocument();
  });

  it('renders model identity and schema version instead of the model UUID', () => {
    const { node } = createNode({
      type: CanvasNodeType.ModelInput,
      name: '订单模型输入',
      configuration: { modelId: '04aee9c7-1877-47b4-a988-dd2968e4a85c' },
      summary: {
        kind: 'MODEL',
        modelName: '订单模型',
        modelCode: 'order_model',
        modelSchemaVersion: 7,
        dataSourceName: '模型仓库',
        qualifiedTableName: 'warehouse.public.order_model',
      },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getByText('订单模型 · order_model · v7')).toBeInTheDocument();
    expect(screen.queryByText(/04aee9c7/)).not.toBeInTheDocument();
  });

  it('renders the resolved file dataset table summary', () => {
    const { node } = createNode({
      type: CanvasNodeType.FileDatasetInput,
      name: '订单文件输入',
      configuration: { fileDatasetTableId: '04aee9c7-1877-47b4-a988-dd2968e4a85c' },
      summary: {
        kind: 'FILE_DATASET',
        fileDatasetName: '订单归档',
        tableName: '七月订单',
        tableCode: 'orders_202607',
        status: 'READY',
        geometry: {
          fieldName: '_geometry',
          kind: 'POINT',
          crs: { authority: 'EPSG', code: 4326 },
          dimension: 'XY',
        },
      },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getByText(
      '订单归档 · 七月订单 (orders_202607) · READY · _geometry: POINT EPSG:4326 XY',
    )).toBeInTheDocument();
  });

  it('renders the ModelOutput source, model identity and write mode', () => {
    const { node } = createNode({
      type: CanvasNodeType.ModelOutput,
      name: '订单模型输出',
      configuration: {
        sourceTableName: 'order_customer',
        targetModelId: '34ccdaed-bfa6-4afb-a085-7318153d75b1',
        writeMode: 'APPEND',
        columnMappingMode: 'BY_NAME',
        columnMappings: [],
      },
      summary: {
        kind: 'MODEL',
        modelName: '订单客户模型',
        modelCode: 'order_customer_model',
        modelSchemaVersion: 3,
        dataSourceName: '模型仓库',
        qualifiedTableName: 'warehouse.public.order_customer_model',
      },
    });

    render(<CanvasNodeView node={node} />);

    expect(screen.getByText('order_customer → 订单客户模型 · order_customer_model (APPEND)'))
      .toBeInTheDocument();
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

    expect(screen.getByText('orders → source_orders · 2 个字段')).toBeInTheDocument();
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
    expect(canvasNodeTemplates).toHaveLength(37);
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
      expect(spec.defaultSize.width).toBeGreaterThanOrEqual(180);
      expect(spec.defaultSize.height).toBeGreaterThanOrEqual(96);
      expect(spec.createDefaultConfiguration()).toEqual(
        canvasNodeRegistry.createDefaultConfiguration(spec.type),
      );
    });
    expect(new Set(canvasNodeTemplates.map((template) => template.shape)))
      .toEqual(new Set([CANVAS_RUNTIME_NODE_SHAPE]));
  });
});
