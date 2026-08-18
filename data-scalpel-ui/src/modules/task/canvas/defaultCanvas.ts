import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_VERSION,
  CanvasNodeType,
  type CanvasDefinition,
} from './canvasTypes';

export const emptyCanvasDefinition = (): CanvasDefinition => ({
  schemaVersion: CANVAS_SCHEMA_VERSION,
  schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
  nodes: [],
  edges: [],
});

export const exampleCanvasDefinition = (): CanvasDefinition => ({
  schemaVersion: CANVAS_SCHEMA_VERSION,
  schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
  nodes: [
    {
      id: '878f22f4-86cf-4487-b697-5bc34eccb169',
      type: CanvasNodeType.JdbcInput,
      name: '订单输入',
      layout: { x: 80, y: 80, width: 300, height: 164 },
      configuration: {
        dataSourceId: 'c5c021bd-35d1-43ae-bbdb-ff90ff824ba0',
        tableName: 'orders',
      },
    },
    {
      id: '3952906c-083d-434c-ac9c-d4d388bac74c',
      type: CanvasNodeType.JdbcInput,
      name: '客户输入',
      layout: { x: 80, y: 280, width: 300, height: 164 },
      configuration: {
        dataSourceId: 'c5c021bd-35d1-43ae-bbdb-ff90ff824ba0',
        tableName: 'customers',
      },
    },
    {
      id: '1f17a225-f602-4a25-a08e-1e67e0b1b2f5',
      type: CanvasNodeType.Join,
      name: '订单关联客户',
      layout: { x: 440, y: 180, width: 360, height: 192 },
      configuration: {
        leftTableName: 'orders',
        rightTableName: 'customers',
        outputTableName: 'order_customer',
        joinType: 'INNER',
        conditions: [{
          leftColumnName: 'customer_id',
          operator: 'EQUALS',
          rightColumnName: 'customer_key',
        }],
      },
    },
    {
      id: 'af86e1c1-7575-4ed3-9600-dad157e6e085',
      type: CanvasNodeType.JdbcOutput,
      name: '订单客户结果输出',
      layout: { x: 860, y: 180, width: 352, height: 216 },
      configuration: {
        sourceTableName: 'order_customer',
        dataSourceId: '04d11960-1ee1-4282-8963-6fb52a21ab0c',
        targetTableName: 'dwd_order_customer',
        writeMode: 'APPEND',
        upsertKeyColumns: [],
        columnMappings: [
          { sourceColumnName: 'order_id', targetColumnName: 'order_id' },
          { sourceColumnName: 'customer_id', targetColumnName: 'customer_id' },
          { sourceColumnName: 'customer_key', targetColumnName: 'customer_key' },
          { sourceColumnName: 'customer_name', targetColumnName: 'customer_name' },
        ],
      },
    },
  ],
  edges: [
    {
      id: 'b1f04da1-5202-4112-8ba6-ac02ea9ba249',
      sourceNodeId: '878f22f4-86cf-4487-b697-5bc34eccb169',
      targetNodeId: '1f17a225-f602-4a25-a08e-1e67e0b1b2f5',
    },
    {
      id: '66e50e26-a0d0-46e9-ae05-6a47ba47294a',
      sourceNodeId: '3952906c-083d-434c-ac9c-d4d388bac74c',
      targetNodeId: '1f17a225-f602-4a25-a08e-1e67e0b1b2f5',
    },
    {
      id: 'b825d9b1-0321-48a1-9a13-11ec068b7b08',
      sourceNodeId: '1f17a225-f602-4a25-a08e-1e67e0b1b2f5',
      targetNodeId: 'af86e1c1-7575-4ed3-9600-dad157e6e085',
    },
  ],
});

export const exampleCanvasTopologyDefinition = (): CanvasDefinition => ({
  schemaVersion: CANVAS_SCHEMA_VERSION,
  schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
  nodes: [
    {
      id: '878f22f4-86cf-4487-b697-5bc34eccb169',
      type: CanvasNodeType.JdbcInput,
      name: '订单输入',
      layout: { x: 80, y: 80, width: 300, height: 104 },
      configuration: { dataSourceId: '', tableName: '' },
    },
    {
      id: '3952906c-083d-434c-ac9c-d4d388bac74c',
      type: CanvasNodeType.JdbcInput,
      name: '客户输入',
      layout: { x: 80, y: 280, width: 300, height: 104 },
      configuration: { dataSourceId: '', tableName: '' },
    },
    {
      id: '1f17a225-f602-4a25-a08e-1e67e0b1b2f5',
      type: CanvasNodeType.Join,
      name: '订单关联客户',
      layout: { x: 440, y: 180, width: 360, height: 116 },
      configuration: {
        leftTableName: '',
        rightTableName: '',
        outputTableName: '',
        joinType: null,
        conditions: [],
      },
    },
    {
      id: 'af86e1c1-7575-4ed3-9600-dad157e6e085',
      type: CanvasNodeType.JdbcOutput,
      name: '订单客户结果输出',
      layout: { x: 860, y: 180, width: 352, height: 112 },
      configuration: {
        sourceTableName: '',
        dataSourceId: '',
        targetTableName: '',
        writeMode: null,
        upsertKeyColumns: [],
        columnMappings: [],
      },
    },
  ],
  edges: [
    {
      id: 'b1f04da1-5202-4112-8ba6-ac02ea9ba249',
      sourceNodeId: '878f22f4-86cf-4487-b697-5bc34eccb169',
      targetNodeId: '1f17a225-f602-4a25-a08e-1e67e0b1b2f5',
    },
    {
      id: '66e50e26-a0d0-46e9-ae05-6a47ba47294a',
      sourceNodeId: '3952906c-083d-434c-ac9c-d4d388bac74c',
      targetNodeId: '1f17a225-f602-4a25-a08e-1e67e0b1b2f5',
    },
    {
      id: 'b825d9b1-0321-48a1-9a13-11ec068b7b08',
      sourceNodeId: '1f17a225-f602-4a25-a08e-1e67e0b1b2f5',
      targetNodeId: 'af86e1c1-7575-4ed3-9600-dad157e6e085',
    },
  ],
});

export const exampleStreamingCanvasTopologyDefinition = (): CanvasDefinition => ({
  schemaVersion: CANVAS_SCHEMA_VERSION,
  schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
  nodes: [
    {
      id: '6f450c78-756a-4e24-9d71-cc57ed47648f',
      type: CanvasNodeType.KafkaInput,
      name: '订单事件流',
      layout: { x: 80, y: 80, width: 332, height: 104 },
      configuration: {
        dataSourceId: '',
        topic: '',
        valueSchema: { columns: [] },
        outputTableName: 'order_events',
        startingOffsets: 'LATEST',
      },
    },
    {
      id: '6e1ce6f8-c96d-4892-aaec-6afe27bff337',
      type: CanvasNodeType.JdbcInput,
      name: '客户静态维表',
      layout: { x: 80, y: 280, width: 300, height: 104 },
      configuration: { dataSourceId: '', tableName: '' },
    },
    {
      id: '4ac766c1-111e-46d0-876e-a2c87b97b0db',
      type: CanvasNodeType.StreamJoin,
      name: '订单关联客户',
      layout: { x: 460, y: 180, width: 360, height: 168 },
      configuration: {
        leftTableName: 'order_events',
        rightTableName: '',
        outputTableName: 'order_customer_stream',
        joinType: 'LEFT',
        conditions: [],
      },
    },
    {
      id: '0b097dfd-bf6a-4641-93f1-196d8a2726fb',
      type: CanvasNodeType.KafkaOutput,
      name: '宽表事件输出',
      layout: { x: 880, y: 180, width: 352, height: 168 },
      configuration: {
        sourceTableName: 'order_customer_stream',
        dataSourceId: '',
        topic: '',
        valueSchema: { columns: [] },
        keyColumnName: '',
        columnMappings: [],
      },
    },
  ],
  edges: [
    {
      id: '67ed4eea-c0d9-47ae-8a38-00a1dfb38ecf',
      sourceNodeId: '6f450c78-756a-4e24-9d71-cc57ed47648f',
      targetNodeId: '4ac766c1-111e-46d0-876e-a2c87b97b0db',
    },
    {
      id: 'c36f944d-ccfa-4474-aedb-a19337906fed',
      sourceNodeId: '6e1ce6f8-c96d-4892-aaec-6afe27bff337',
      targetNodeId: '4ac766c1-111e-46d0-876e-a2c87b97b0db',
    },
    {
      id: '6c74484d-97af-4bed-8376-ff35655cae08',
      sourceNodeId: '4ac766c1-111e-46d0-876e-a2c87b97b0db',
      targetNodeId: '0b097dfd-bf6a-4641-93f1-196d8a2726fb',
    },
  ],
});
