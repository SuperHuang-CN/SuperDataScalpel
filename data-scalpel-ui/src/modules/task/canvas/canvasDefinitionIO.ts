import {
  CANVAS_LEGACY_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_VERSION,
  CanvasNodeType,
  type CanvasDefinition,
  type CanvasEdgeDefinition,
  type CanvasNodeDefinition,
  type CanvasColumnMapping,
  type ColumnMappingMode,
  type JdbcWriteMode,
  type JoinCondition,
  type JoinType,
  type StreamJoinType,
  type HttpApiRuntimeParameter,
  type KafkaValueColumn,
  type KafkaValueSchema,
} from './canvasTypes';

export type CanvasDefinitionParseResult =
  | { success: true; definition: CanvasDefinition }
  | { success: false; errors: string[] };

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const sensitiveRuntimeParameterPattern = /(password|passwd|secret|token|credential|api[-_.]?key|access[-_.]?key|signature)/i;

export const isSensitiveRuntimeParameterName = (name: string) => (
  sensitiveRuntimeParameterPattern.test(name)
);

const isRecord = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

const stringValue = (value: unknown) => typeof value === 'string' ? value : '';

const validateOptionalUuid = (value: string, path: string, errors: string[]) => {
  if (value && !uuidPattern.test(value)) errors.push(`${path} 必须是 UUID`);
  return value;
};

const legacyTableName = (value: unknown) => isRecord(value) ? stringValue(value.tableName) : '';

const parseLayout = (value: unknown, path: string, errors: string[]) => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是布局对象`);
    return null;
  }
  const coordinates = ['x', 'y', 'width', 'height'] as const;
  if (coordinates.some((key) => typeof value[key] !== 'number' || !Number.isFinite(value[key]))) {
    errors.push(`${path} 必须包含有限数值 x、y、width、height`);
    return null;
  }
  const x = value.x as number;
  const y = value.y as number;
  const width = value.width as number;
  const height = value.height as number;
  if (x < -100_000 || x > 100_000 || y < -100_000 || y > 100_000
    || width < 180 || width > 1000 || height < 96 || height > 1000) {
    errors.push(`${path} 超出允许范围`);
    return null;
  }
  return { x, y, width, height };
};

const parseJoinType = (value: unknown, path: string, errors: string[]): JoinType | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'INNER' || value === 'LEFT' || value === 'RIGHT' || value === 'FULL') return value;
  errors.push(`${path} 不是受支持的 Join 类型`);
  return null;
};

const parseStreamJoinType = (
  value: unknown,
  path: string,
  errors: string[],
): StreamJoinType | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'INNER' || value === 'LEFT') return value;
  errors.push(`${path} 不是受支持的流 Join 类型`);
  return null;
};

const parseJoinConditions = (value: unknown, path: string, errors: string[]): JoinCondition[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): JoinCondition[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    if (item.operator !== undefined && item.operator !== 'EQUALS') {
      errors.push(`${path}[${index}].operator 仅支持 EQUALS`);
    }
    return [{
      leftColumnName: stringValue(item.leftColumnName),
      operator: 'EQUALS',
      rightColumnName: stringValue(item.rightColumnName),
    }];
  });
};

const parseWriteMode = (value: unknown, path: string, errors: string[]): JdbcWriteMode | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'APPEND' || value === 'OVERWRITE') return value;
  errors.push(`${path} 不是受支持的写入模式`);
  return null;
};

const parseMappingMode = (value: unknown, path: string, errors: string[]): ColumnMappingMode | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'BY_NAME' || value === 'EXPLICIT') return value;
  errors.push(`${path} 不是受支持的字段映射模式`);
  return null;
};

const parseMappings = (value: unknown, path: string, errors: string[]): CanvasColumnMapping[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): CanvasColumnMapping[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    return [{
      sourceColumnName: stringValue(item.sourceColumnName),
      targetColumnName: stringValue(item.targetColumnName),
    }];
  });
};

const parseRuntimeParameters = (
  value: unknown,
  path: string,
  errors: string[],
): HttpApiRuntimeParameter[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): HttpApiRuntimeParameter[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    const name = stringValue(item.name);
    const parameterValue = stringValue(item.value);
    if (name && !/^[A-Za-z][A-Za-z0-9_.-]{0,127}$/.test(name)) {
      errors.push(`${path}[${index}].name 无效`);
    }
    if (isSensitiveRuntimeParameterName(name)) {
      errors.push(`${path}[${index}].name 不允许用于密码、Token、密钥或签名`);
    }
    return [{ name, value: parameterValue }];
  });
};

const platformDataTypes = new Set([
  'BOOLEAN',
  'BYTE',
  'SHORT',
  'INTEGER',
  'LONG',
  'FLOAT',
  'DOUBLE',
  'DECIMAL',
  'STRING',
  'BINARY',
  'DATE',
  'TIMESTAMP',
  'TIMESTAMP_NTZ',
]);

const optionalInteger = (value: unknown, path: string, errors: string[]) => {
  if (value === null || value === undefined) return null;
  if (typeof value !== 'number' || !Number.isInteger(value)) {
    errors.push(`${path} 必须是整数或 null`);
    return null;
  }
  return value;
};

const parseKafkaValueSchema = (
  value: unknown,
  path: string,
  errors: string[],
): KafkaValueSchema => {
  if (value === undefined || value === null) return { columns: [] };
  if (!isRecord(value) || !Array.isArray(value.columns)) {
    errors.push(`${path}.columns 必须是数组`);
    return { columns: [] };
  }
  return {
    columns: value.columns.flatMap((item, index) => {
      const columnPath = `${path}.columns[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${columnPath} 必须是对象`);
        return [];
      }
      const fieldType = stringValue(item.fieldType);
      if (!platformDataTypes.has(fieldType)) {
        errors.push(`${columnPath}.fieldType 不是受支持的平台类型`);
      }
      if (typeof item.nullable !== 'boolean') {
        errors.push(`${columnPath}.nullable 必须是 Boolean`);
      }
      if (item.comment !== null && item.comment !== undefined && typeof item.comment !== 'string') {
        errors.push(`${columnPath}.comment 必须是字符串或 null`);
      }
      return [{
        name: stringValue(item.name),
        fieldType: platformDataTypes.has(fieldType)
          ? fieldType as KafkaValueColumn['fieldType']
          : 'STRING',
        length: optionalInteger(item.length, `${columnPath}.length`, errors),
        precision: optionalInteger(item.precision, `${columnPath}.precision`, errors),
        scale: optionalInteger(item.scale, `${columnPath}.scale`, errors),
        nullable: typeof item.nullable === 'boolean' ? item.nullable : true,
        comment: typeof item.comment === 'string' ? item.comment : null,
      }];
    }),
  };
};

const parseNode = (value: unknown, index: number, errors: string[]): CanvasNodeDefinition | null => {
  const path = `nodes[${index}]`;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象`);
    return null;
  }
  const id = stringValue(value.id);
  if (!uuidPattern.test(id)) errors.push(`${path}.id 必须是 UUID`);
  if (typeof value.name !== 'string') errors.push(`${path}.name 必须是字符串`);
  const name = stringValue(value.name);
  const layout = parseLayout(value.layout, `${path}.layout`, errors);
  const configuration = isRecord(value.configuration) ? value.configuration : {};
  if (!layout) return null;

  switch (value.type) {
    case CanvasNodeType.ModelInput:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          modelId: validateOptionalUuid(
            stringValue(configuration.modelId),
            `${path}.configuration.modelId`,
            errors,
          ),
        },
      };
    case CanvasNodeType.JdbcInput:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          dataSourceId: stringValue(configuration.dataSourceId),
          tableName: stringValue(configuration.tableName) || legacyTableName(configuration.table),
        },
      };
    case CanvasNodeType.FileDatasetInput:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          fileDatasetTableId: validateOptionalUuid(
            stringValue(configuration.fileDatasetTableId),
            `${path}.configuration.fileDatasetTableId`,
            errors,
          ),
        },
      };
    case CanvasNodeType.HttpApiInput:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          dataSourceId: validateOptionalUuid(
            stringValue(configuration.dataSourceId),
            `${path}.configuration.dataSourceId`,
            errors,
          ),
          resourceId: validateOptionalUuid(
            stringValue(configuration.resourceId),
            `${path}.configuration.resourceId`,
            errors,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          runtimeParameters: parseRuntimeParameters(
            configuration.runtimeParameters,
            `${path}.configuration.runtimeParameters`,
            errors,
          ),
        },
      };
    case CanvasNodeType.KafkaInput:
      if (configuration.startingOffsets !== undefined
        && configuration.startingOffsets !== null
        && configuration.startingOffsets !== ''
        && configuration.startingOffsets !== 'EARLIEST'
        && configuration.startingOffsets !== 'LATEST') {
        errors.push(`${path}.configuration.startingOffsets 仅支持 EARLIEST 或 LATEST`);
      }
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          dataSourceId: validateOptionalUuid(
            stringValue(configuration.dataSourceId),
            `${path}.configuration.dataSourceId`,
            errors,
          ),
          topic: stringValue(configuration.topic),
          valueSchema: parseKafkaValueSchema(
            configuration.valueSchema,
            `${path}.configuration.valueSchema`,
            errors,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          startingOffsets: configuration.startingOffsets === 'EARLIEST'
            || configuration.startingOffsets === 'LATEST'
            ? configuration.startingOffsets
            : null,
        },
      };
    case CanvasNodeType.Join:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          leftTableName: stringValue(configuration.leftTableName),
          rightTableName: stringValue(configuration.rightTableName),
          outputTableName: stringValue(configuration.outputTableName),
          joinType: parseJoinType(configuration.joinType, `${path}.configuration.joinType`, errors),
          conditions: parseJoinConditions(configuration.conditions, `${path}.configuration.conditions`, errors),
        },
      };
    case CanvasNodeType.StreamJoin:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          leftTableName: stringValue(configuration.leftTableName),
          rightTableName: stringValue(configuration.rightTableName),
          outputTableName: stringValue(configuration.outputTableName),
          joinType: parseStreamJoinType(
            configuration.joinType,
            `${path}.configuration.joinType`,
            errors,
          ),
          conditions: parseJoinConditions(
            configuration.conditions,
            `${path}.configuration.conditions`,
            errors,
          ),
        },
      };
    case CanvasNodeType.Rename:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          columnMappings: parseMappings(
            configuration.columnMappings,
            `${path}.configuration.columnMappings`,
            errors,
          ),
        },
      };
    case CanvasNodeType.ModelOutput:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          sourceTableName: stringValue(configuration.sourceTableName),
          targetModelId: validateOptionalUuid(
            stringValue(configuration.targetModelId),
            `${path}.configuration.targetModelId`,
            errors,
          ),
          writeMode: parseWriteMode(configuration.writeMode, `${path}.configuration.writeMode`, errors),
          columnMappingMode: parseMappingMode(
            configuration.columnMappingMode,
            `${path}.configuration.columnMappingMode`,
            errors,
          ),
          columnMappings: parseMappings(
            configuration.columnMappings,
            `${path}.configuration.columnMappings`,
            errors,
          ),
        },
      };
    case CanvasNodeType.JdbcOutput:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          sourceTableName: stringValue(configuration.sourceTableName),
          dataSourceId: stringValue(configuration.dataSourceId),
          targetTableName: stringValue(configuration.targetTableName) || legacyTableName(configuration.targetTable),
          writeMode: parseWriteMode(configuration.writeMode, `${path}.configuration.writeMode`, errors),
          columnMappingMode: parseMappingMode(
            configuration.columnMappingMode,
            `${path}.configuration.columnMappingMode`,
            errors,
          ),
          columnMappings: parseMappings(
            configuration.columnMappings,
            `${path}.configuration.columnMappings`,
            errors,
          ),
        },
      };
    case CanvasNodeType.KafkaOutput:
      return {
        id,
        type: value.type,
        name,
        layout,
        configuration: {
          sourceTableName: stringValue(configuration.sourceTableName),
          dataSourceId: validateOptionalUuid(
            stringValue(configuration.dataSourceId),
            `${path}.configuration.dataSourceId`,
            errors,
          ),
          topic: stringValue(configuration.topic),
          valueSchema: parseKafkaValueSchema(
            configuration.valueSchema,
            `${path}.configuration.valueSchema`,
            errors,
          ),
          keyColumnName: stringValue(configuration.keyColumnName),
          columnMappingMode: parseMappingMode(
            configuration.columnMappingMode,
            `${path}.configuration.columnMappingMode`,
            errors,
          ),
          columnMappings: parseMappings(
            configuration.columnMappings,
            `${path}.configuration.columnMappings`,
            errors,
          ),
        },
      };
    default:
      errors.push(`${path}.type 不是受支持的节点类型`);
      return null;
  }
};

const parseEdge = (value: unknown, index: number, errors: string[]): CanvasEdgeDefinition | null => {
  const path = `edges[${index}]`;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象`);
    return null;
  }
  const id = stringValue(value.id);
  const sourceNodeId = stringValue(value.sourceNodeId);
  const targetNodeId = stringValue(value.targetNodeId);
  if (!uuidPattern.test(id)) errors.push(`${path}.id 必须是 UUID`);
  if (!uuidPattern.test(sourceNodeId)) errors.push(`${path}.sourceNodeId 必须是 UUID`);
  if (!uuidPattern.test(targetNodeId)) errors.push(`${path}.targetNodeId 必须是 UUID`);
  return { id, sourceNodeId, targetNodeId };
};

export const parseCanvasDefinition = (value: unknown): CanvasDefinitionParseResult => {
  const errors: string[] = [];
  if (!isRecord(value)) return { success: false, errors: ['Canvas 定义必须是 JSON 对象'] };
  if (value.schemaVersion !== CANVAS_SCHEMA_VERSION) {
    errors.push(`schemaVersion 仅支持 ${CANVAS_SCHEMA_VERSION}`);
  }
  const sourceSchemaMinorVersion = value.schemaMinorVersion === undefined
    ? CANVAS_LEGACY_SCHEMA_MINOR_VERSION
    : value.schemaMinorVersion;
  if (typeof sourceSchemaMinorVersion !== 'number'
      || !Number.isInteger(sourceSchemaMinorVersion)
      || sourceSchemaMinorVersion < CANVAS_LEGACY_SCHEMA_MINOR_VERSION
      || sourceSchemaMinorVersion > CANVAS_SCHEMA_MINOR_VERSION) {
    errors.push(
      `schemaMinorVersion 仅支持 ${CANVAS_LEGACY_SCHEMA_MINOR_VERSION} 到 ${CANVAS_SCHEMA_MINOR_VERSION}`,
    );
  }
  if (!Array.isArray(value.nodes)) errors.push('nodes 必须是数组');
  if (!Array.isArray(value.edges)) errors.push('edges 必须是数组');
  if (errors.length > 0) return { success: false, errors };

  const nodes = (value.nodes as unknown[]).flatMap((item, index): CanvasNodeDefinition[] => {
    const parsed = parseNode(item, index, errors);
    return parsed ? [parsed] : [];
  });
  const edges = (value.edges as unknown[]).flatMap((item, index): CanvasEdgeDefinition[] => {
    const parsed = parseEdge(item, index, errors);
    return parsed ? [parsed] : [];
  });

  if (sourceSchemaMinorVersion === CANVAS_LEGACY_SCHEMA_MINOR_VERSION
      && nodes.some((node) => node.type === CanvasNodeType.ModelInput || node.type === CanvasNodeType.ModelOutput)) {
    errors.push('MODEL_INPUT 和 MODEL_OUTPUT 从 Canvas 1.1 开始支持');
  }
  if (typeof sourceSchemaMinorVersion === 'number'
      && sourceSchemaMinorVersion < 2
      && nodes.some((node) => node.type === CanvasNodeType.Rename)) {
    errors.push('RENAME 从 Canvas 1.2 开始支持');
  }
  if (typeof sourceSchemaMinorVersion === 'number'
      && sourceSchemaMinorVersion < 3
      && nodes.some((node) => node.type === CanvasNodeType.StreamJoin)) {
    errors.push('STREAM_JOIN 从 Canvas 1.3 开始支持');
  }
  if (typeof sourceSchemaMinorVersion === 'number'
      && sourceSchemaMinorVersion < 4
      && nodes.some((node) => node.type === CanvasNodeType.FileDatasetInput)) {
    errors.push('FILE_DATASET_INPUT 从 Canvas 1.4 开始支持');
  }
  if (typeof sourceSchemaMinorVersion === 'number'
      && sourceSchemaMinorVersion < 5
      && nodes.some((node) => (
        node.type === CanvasNodeType.KafkaInput
        || node.type === CanvasNodeType.KafkaOutput
      ))) {
    errors.push('KAFKA_INPUT 和 KAFKA_OUTPUT 的内联 Value Schema 从 Canvas 1.5 开始支持');
  }

  const nodeIds = new Set<string>();
  nodes.forEach((node) => {
    if (nodeIds.has(node.id)) errors.push(`节点 ID ${node.id} 重复`);
    nodeIds.add(node.id);
  });
  const edgeIds = new Set<string>();
  edges.forEach((edge) => {
    if (edgeIds.has(edge.id)) errors.push(`连线 ID ${edge.id} 重复`);
    edgeIds.add(edge.id);
    if (!nodeIds.has(edge.sourceNodeId) || !nodeIds.has(edge.targetNodeId)) {
      errors.push(`连线 ${edge.id} 引用了不存在的节点`);
    }
  });

  return errors.length > 0
    ? { success: false, errors }
    : {
      success: true,
      definition: {
        schemaVersion: CANVAS_SCHEMA_VERSION,
        schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
        nodes,
        edges,
      },
    };
};

export const parseCanvasDefinitionJson = (content: string): CanvasDefinitionParseResult => {
  try {
    return parseCanvasDefinition(JSON.parse(content) as unknown);
  } catch (error) {
    return {
      success: false,
      errors: [`JSON 解析失败：${error instanceof Error ? error.message : '未知错误'}`],
    };
  }
};

export const formatCanvasDefinition = (definition: CanvasDefinition) => JSON.stringify(definition, null, 2);

export const CANVAS_DEFINITION_FILE_NAME = 'canvas-task-definition.json';

export const downloadCanvasDefinition = (definition: CanvasDefinition) => {
  const blob = new Blob([formatCanvasDefinition(definition)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = CANVAS_DEFINITION_FILE_NAME;
  anchor.click();
  URL.revokeObjectURL(url);
};
