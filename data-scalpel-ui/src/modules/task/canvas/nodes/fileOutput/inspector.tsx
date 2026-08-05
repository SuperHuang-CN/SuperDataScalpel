import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  ReloadOutlined,
  UpOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd';
import { useImperativeHandle, useState } from 'react';
import { useDataSource } from '../../../../datasource';
import {
  CanvasNodeType,
  normalizeFileOutputPath,
  type CanvasColumnSchema,
  type CanvasTableSchema,
  type FileOutputConfiguration,
  type FileOutputFormatOptions,
  type GeoParquetCompressionCodec,
  type GeoParquetCoveringMode,
  type ShapefileAttributeMapping,
  type ShapefilePackageMode,
  type ShapefileShapeType,
} from '../../canvasTypes';
import { CanvasS3DataSourceSelect } from '../../components/CanvasS3DataSourceSelect';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import { spatialTableOptions } from '../spatialInspectorOptions';

type FileOutputFormatType = FileOutputFormatOptions['type'];
type GeometryKind = NonNullable<CanvasColumnSchema['geometry']>['kind'];

interface FileOutputFormValues {
  sourceTableName: string;
  dataSourceId: string;
  targetPath: string;
  conflictPolicy: FileOutputConfiguration['conflictPolicy'];
  formatType: FileOutputFormatType;
  header: boolean;
  delimiter: string;
  quote: string;
  escape: string;
  nullValue: string;
  ignoreNullFields: boolean;
  baseName: string;
  packageMode: ShapefilePackageMode;
  geometryColumnName: string;
  targetShapeType: ShapefileShapeType | null;
  geoParquetCompression: GeoParquetCompressionCodec;
  geoParquetCoveringMode: GeoParquetCoveringMode;
  idColumnName: string | null;
  ignoreNullProperties: boolean;
}

const DBF_SUPPORTED_TYPES = new Set<CanvasColumnSchema['fieldType']>([
  'BOOLEAN', 'BYTE', 'SHORT', 'INTEGER', 'LONG', 'DECIMAL', 'STRING', 'DATE',
]);

const GEOJSON_ID_TYPES = new Set<CanvasColumnSchema['fieldType']>([
  'STRING', 'BYTE', 'SHORT', 'INTEGER', 'LONG',
]);

const isSpatialFileFormat = (type: FileOutputFormatType): boolean => (
  type === 'SHAPEFILE' || type === 'GEOPARQUET' || type === 'GEOJSON'
);

const fingerprint = (value: FileOutputConfiguration): string => JSON.stringify(value);

const initialValues = (configuration: FileOutputConfiguration): FileOutputFormValues => ({
  sourceTableName: configuration.sourceTableName,
  dataSourceId: configuration.dataSourceId,
  targetPath: configuration.targetPath,
  conflictPolicy: configuration.conflictPolicy,
  formatType: configuration.formatOptions.type,
  header: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.header : true,
  delimiter: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.delimiter : ',',
  quote: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.quote : '"',
  escape: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.escape : '\\',
  nullValue: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.nullValue : '',
  ignoreNullFields: configuration.formatOptions.type === 'JSON_LINES'
    ? configuration.formatOptions.ignoreNullFields : false,
  baseName: configuration.formatOptions.type === 'SHAPEFILE'
    || configuration.formatOptions.type === 'GEOJSON'
    ? configuration.formatOptions.baseName : 'output',
  packageMode: configuration.formatOptions.type === 'SHAPEFILE'
    ? configuration.formatOptions.packageMode : 'ZIP',
  geometryColumnName: configuration.formatOptions.type === 'SHAPEFILE'
    || configuration.formatOptions.type === 'GEOPARQUET'
    || configuration.formatOptions.type === 'GEOJSON'
    ? configuration.formatOptions.geometryColumnName : '',
  targetShapeType: configuration.formatOptions.type === 'SHAPEFILE'
    ? configuration.formatOptions.targetShapeType : null,
  geoParquetCompression: configuration.formatOptions.type === 'GEOPARQUET'
    ? configuration.formatOptions.compression : 'SNAPPY',
  geoParquetCoveringMode: configuration.formatOptions.type === 'GEOPARQUET'
    ? configuration.formatOptions.coveringMode : 'ROW_BBOX',
  idColumnName: configuration.formatOptions.type === 'GEOJSON'
    ? configuration.formatOptions.idColumnName : null,
  ignoreNullProperties: configuration.formatOptions.type === 'GEOJSON'
    ? configuration.formatOptions.ignoreNullProperties : false,
});

const configuration = (
  values: FileOutputFormValues,
  attributeMappings: ShapefileAttributeMapping[],
): FileOutputConfiguration => {
  let formatOptions: FileOutputFormatOptions;
  if (values.formatType === 'CSV') {
    formatOptions = {
      type: 'CSV',
      header: values.header,
      delimiter: values.delimiter,
      quote: values.quote,
      escape: values.escape,
      nullValue: values.nullValue ?? '',
    };
  } else if (values.formatType === 'JSON_LINES') {
    formatOptions = { type: 'JSON_LINES', ignoreNullFields: values.ignoreNullFields };
  } else if (values.formatType === 'PARQUET') {
    formatOptions = { type: 'PARQUET' };
  } else if (values.formatType === 'SHAPEFILE') {
    formatOptions = {
      type: 'SHAPEFILE',
      baseName: values.baseName?.trim() ?? '',
      packageMode: values.packageMode,
      geometryColumnName: values.geometryColumnName ?? '',
      targetShapeType: values.targetShapeType ?? 'POINT',
      attributeMappings: attributeMappings.map((mapping) => ({
        ...mapping,
        sourceColumnName: mapping.sourceColumnName.trim(),
        targetFieldName: mapping.targetFieldName.trim(),
      })),
    };
  } else if (values.formatType === 'GEOPARQUET') {
    formatOptions = {
      type: 'GEOPARQUET',
      geometryColumnName: values.geometryColumnName ?? '',
      compression: values.geoParquetCompression,
      coveringMode: values.geoParquetCoveringMode,
    };
  } else {
    formatOptions = {
      type: 'GEOJSON',
      baseName: values.baseName?.trim() ?? '',
      geometryColumnName: values.geometryColumnName ?? '',
      idColumnName: values.idColumnName || null,
      ignoreNullProperties: values.ignoreNullProperties,
    };
  }
  return {
    sourceTableName: values.sourceTableName ?? '',
    dataSourceId: values.dataSourceId ?? '',
    targetPath: normalizeFileOutputPath(values.targetPath ?? ''),
    conflictPolicy: values.conflictPolicy,
    formatOptions,
  };
};

const shapeTypeFor = (column: CanvasColumnSchema | undefined): ShapefileShapeType | null => {
  switch (column?.geometry?.kind) {
    case 'POINT': return 'POINT';
    case 'MULTIPOINT': return 'MULTIPOINT';
    case 'LINESTRING':
    case 'MULTILINESTRING': return 'POLYLINE';
    case 'POLYGON':
    case 'MULTIPOLYGON': return 'POLYGON';
    default: return null;
  }
};

const shapeTypeOptions = (
  column: CanvasColumnSchema | undefined,
  current: ShapefileShapeType | null,
) => {
  const all: Array<{ value: ShapefileShapeType; label: string }> = [
    { value: 'POINT', label: 'POINT · 单点' },
    { value: 'MULTIPOINT', label: 'MULTIPOINT · 多点' },
    { value: 'POLYLINE', label: 'POLYLINE · 线或多线' },
    { value: 'POLYGON', label: 'POLYGON · 面或多面' },
  ];
  const allowed = switchShapeTypes(column?.geometry?.kind).map((value) => (
    all.find((option) => option.value === value)!
  ));
  return current && !allowed.some((option) => option.value === current)
    ? [{ value: current, label: `${current}（与上游不兼容）`, disabled: true }, ...allowed]
    : allowed;
};

const switchShapeTypes = (
  kind: GeometryKind | undefined,
): ShapefileShapeType[] => {
  switch (kind) {
    case 'POINT': return ['POINT', 'MULTIPOINT'];
    case 'MULTIPOINT': return ['MULTIPOINT'];
    case 'LINESTRING':
    case 'MULTILINESTRING': return ['POLYLINE'];
    case 'POLYGON':
    case 'MULTIPOLYGON': return ['POLYGON'];
    case 'GEOMETRY': return ['POINT', 'MULTIPOINT', 'POLYLINE', 'POLYGON'];
    default: return [];
  }
};

const suggestedBaseName = (tableName: string | undefined): string => {
  const normalized = (tableName ?? 'output')
    .replace(/[^\p{L}\p{N}_-]+/gu, '_')
    .replace(/^[_-]+/, '')
    .replace(/_+/g, '_');
  return [...(normalized || 'output')].slice(0, 64).join('');
};

const uniqueDbfName = (sourceName: string, used: Set<string>, fallbackIndex: number): string => {
  let base = sourceName
    .normalize('NFKD')
    .replace(/[^A-Za-z0-9_]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toUpperCase();
  if (/^[0-9]/.test(base)) base = `F_${base}`;
  if (!base) base = `F_${String(fallbackIndex).padStart(3, '0')}`;
  base = base.slice(0, 10);
  let candidate = base;
  let suffix = 2;
  while (used.has(candidate.toUpperCase())) {
    const tail = `_${suffix}`;
    candidate = `${base.slice(0, 10 - tail.length)}${tail}`;
    suffix += 1;
  }
  used.add(candidate.toUpperCase());
  return candidate;
};

const suggestShapefileAttributeMappings = (
  table: CanvasTableSchema | undefined,
): ShapefileAttributeMapping[] => {
  const used = new Set<string>();
  let fallbackIndex = 1;
  return (table?.columns ?? [])
    .filter((column) => DBF_SUPPORTED_TYPES.has(column.fieldType))
    .map((column) => {
      const targetFieldName = uniqueDbfName(column.name, used, fallbackIndex);
      fallbackIndex += 1;
      return {
        sourceColumnName: column.name,
        targetFieldName,
        targetStringByteLength: column.fieldType === 'STRING'
          ? Math.min((column.length ?? 64) * 4, 254)
          : null,
      };
    });
};

const mappingProblems = (
  mappings: ShapefileAttributeMapping[],
  table: CanvasTableSchema | undefined,
  compilerContextAvailable: boolean,
): Map<number, string[]> => {
  const result = new Map<number, string[]>();
  const sources = new Set<string>();
  const targets = new Set<string>();
  mappings.forEach((mapping, index) => {
    const errors: string[] = [];
    const column = table?.columns.find((candidate) => candidate.name === mapping.sourceColumnName);
    if ((table && (!column || !DBF_SUPPORTED_TYPES.has(column.fieldType)))
        || (!table && compilerContextAvailable)) {
      errors.push('来源字段已失效或类型不支持');
    }
    if (sources.has(mapping.sourceColumnName)) errors.push('来源字段重复');
    sources.add(mapping.sourceColumnName);
    if (!/^[A-Za-z_][A-Za-z0-9_]{0,9}$/.test(mapping.targetFieldName)) {
      errors.push('目标字段名必须是 1–10 位 ASCII 标识符');
    }
    const normalizedTarget = mapping.targetFieldName.toUpperCase();
    if (targets.has(normalizedTarget)) errors.push('目标字段名重复');
    targets.add(normalizedTarget);
    if (column?.fieldType === 'STRING') {
      if (!Number.isInteger(mapping.targetStringByteLength)
          || (mapping.targetStringByteLength ?? 0) < 1
          || (mapping.targetStringByteLength ?? 0) > 254) {
        errors.push('STRING 字节宽度必须是 1–254');
      }
    } else if (mapping.targetStringByteLength !== null) {
      errors.push('非 STRING 字段不能配置字符宽度');
    }
    if (errors.length > 0) result.set(index, errors);
  });
  return result;
};

const validateFileOutputPath = async (_: unknown, value: string | undefined) => {
  if (!value?.trim()) return;
  const normalized = normalizeFileOutputPath(value);
  if (normalized.length > 1024 || normalized.startsWith('/') || normalized.includes('\\')
      || normalized.includes('://') || normalized.includes('?') || normalized.includes('#')) {
    throw new Error('请输入合法的 S3 相对目录');
  }
  if (normalized.split('/').some((segment) => (
    !segment || segment === '.' || segment === '..' || segment.toLowerCase() === '_temporary'
  ))) {
    throw new Error('目录不能包含空段、.、.. 或 _temporary');
  }
};

const FileOutputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.FileOutput>) => {
  const [form] = Form.useForm<FileOutputFormValues>();
  const [attributeMappings, setAttributeMappings] = useState<ShapefileAttributeMapping[]>(
    node.configuration.formatOptions.type === 'SHAPEFILE'
      ? node.configuration.formatOptions.attributeMappings : [],
  );
  const [mappingErrors, setMappingErrors] = useState<Map<number, string[]>>(new Map());
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? node.configuration.sourceTableName;
  const dataSourceId = Form.useWatch('dataSourceId', form) ?? node.configuration.dataSourceId;
  const targetPath = Form.useWatch('targetPath', form) ?? node.configuration.targetPath;
  const conflictPolicy = Form.useWatch('conflictPolicy', form) ?? node.configuration.conflictPolicy;
  const formatType = Form.useWatch('formatType', form) ?? node.configuration.formatOptions.type;
  const baseName = Form.useWatch('baseName', form) ?? 'output';
  const packageMode = Form.useWatch('packageMode', form) ?? 'ZIP';
  const geometryColumnName = Form.useWatch('geometryColumnName', form) ?? '';
  const targetShapeType = Form.useWatch('targetShapeType', form) ?? null;
  const idColumnName = Form.useWatch('idColumnName', form) ?? null;
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const geometryColumns = sourceTable?.columns.filter((column) => column.fieldType === 'GEOMETRY') ?? [];
  const geometryColumn = geometryColumns.find((column) => column.name === geometryColumnName);
  const idColumn = sourceTable?.columns.find((column) => column.name === idColumnName);
  const idColumnValid = Boolean(idColumn && GEOJSON_ID_TYPES.has(idColumn.fieldType));
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const selectedDataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const selectedDataSource = selectedDataSourceQuery.data;
  const selectedAvailable = selectedDataSource
    ? selectedDataSource.enabled
      && selectedDataSource.connectionKind === 'S3'
      && selectedDataSource.connection.kind === 'S3'
      && selectedDataSource.purposes.includes('DISTRIBUTION')
    : selectedDataSourceQuery.isError ? false : undefined;

  const currentConfiguration = (
    values = form.getFieldsValue(true) as FileOutputFormValues,
    mappings = attributeMappings,
  ) => configuration(values, mappings);
  const markDirty = (mappings = attributeMappings) => {
    onDirtyChange(fingerprint(currentConfiguration(
      form.getFieldsValue(true) as FileOutputFormValues,
      mappings,
    )) !== fingerprint(node.configuration));
  };
  const updateMappings = (mappings: ShapefileAttributeMapping[]) => {
    setAttributeMappings(mappings);
    setMappingErrors(new Map());
    markDirty(mappings);
  };
  const rebuildMappings = (table = sourceTable) => {
    updateMappings(suggestShapefileAttributeMappings(table));
  };
  const initializeSpatialFormat = (
    nextFormat: FileOutputFormatType,
    table: CanvasTableSchema | undefined,
  ) => {
    const mappings = nextFormat === 'SHAPEFILE'
      ? suggestShapefileAttributeMappings(table) : attributeMappings;
    const firstGeometry = table?.columns.find((column) => column.fieldType === 'GEOMETRY');
    const current = form.getFieldsValue(true) as FileOutputFormValues;
    const nextValues = {
      ...current,
      geometryColumnName: current.geometryColumnName || firstGeometry?.name || '',
      targetShapeType: nextFormat === 'SHAPEFILE'
        ? current.targetShapeType || shapeTypeFor(firstGeometry) : current.targetShapeType,
      baseName: nextFormat === 'SHAPEFILE' || nextFormat === 'GEOJSON'
        ? current.baseName && current.baseName !== 'output'
          ? current.baseName : suggestedBaseName(table?.name)
        : current.baseName,
    };
    form.setFieldsValue(nextValues);
    if (nextFormat === 'SHAPEFILE') {
      setAttributeMappings(mappings);
      setMappingErrors(new Map());
    }
    onDirtyChange(
      fingerprint(configuration(nextValues, mappings)) !== fingerprint(node.configuration),
    );
  };
  const updateMapping = (index: number, mapping: ShapefileAttributeMapping) => {
    updateMappings(attributeMappings.map((candidate, candidateIndex) => (
      candidateIndex === index ? mapping : candidate
    )));
  };
  const moveMapping = (from: number, to: number) => {
    const next = [...attributeMappings];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    updateMappings(next);
  };
  const availableAttributeColumns = sourceTable?.columns.filter(
    (column) => DBF_SUPPORTED_TYPES.has(column.fieldType)
      && !attributeMappings.some((mapping) => mapping.sourceColumnName === column.name),
  ) ?? [];

  const submit = async (values: FileOutputFormValues): Promise<boolean> => {
    if (values.dataSourceId && selectedAvailable !== true) {
      form.setFields([{
        name: 'dataSourceId',
        errors: [selectedDataSourceQuery.isFetching
          ? '正在读取数据源信息，请稍候'
          : '数据源不存在、已停用、不是 S3 或不具有数据分发用途'],
      }]);
      return false;
    }
    if (values.formatType === 'SHAPEFILE') {
      const errors = mappingProblems(attributeMappings, sourceTable, Boolean(validation));
      setMappingErrors(errors);
      if (attributeMappings.length < 1 || attributeMappings.length > 255 || errors.size > 0) {
        return false;
      }
    }
    onApply({
      id: node.id,
      type: node.type,
      configuration: configuration(values, attributeMappings),
    });
    onDirtyChange(false);
    return true;
  };

  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(
    inspectorRef,
    () => ({
      apply: async () => {
        try {
          return submit(await form.validateFields());
        } catch {
          return false;
        }
      },
    }),
  );

  const normalizedPath = normalizeFileOutputPath(targetPath);
  const targetRoot = selectedDataSource?.connection.kind === 'S3' && normalizedPath
    ? `s3a://${selectedDataSource.connection.bucket}/${
      [selectedDataSource.connection.rootPrefix, normalizedPath].filter(Boolean).join('/')
    }`
    : null;
  const artifactPreview = targetRoot && formatType === 'SHAPEFILE'
    ? packageMode === 'ZIP'
      ? `${targetRoot}/${baseName || '<基础名>'}.zip`
      : `${targetRoot}/${baseName || '<基础名>'}.{shp,shx,dbf,prj,cpg}`
    : targetRoot && formatType === 'GEOJSON'
      ? `${targetRoot}/${baseName || '<基础名>'}.geojson + ${targetRoot}/_SUCCESS`
      : targetRoot && formatType === 'GEOPARQUET'
        ? `${targetRoot}/part-*.parquet + ${targetRoot}/_SUCCESS`
        : targetRoot ? `${targetRoot}/` : null;

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<FileOutputFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={initialValues(node.configuration)}
        onFinish={(values) => void submit(values)}
        onValuesChange={() => markDirty()}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，已有配置仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!validation}
            placeholder={validation ? '选择上游表' : '等待 Task Engine 计算上游表'}
            options={spatialTableOptions(tables, sourceTableName)}
            onChange={(nextSourceTableName) => {
              const nextTable = tables.find((table) => table.name === nextSourceTableName);
              if (isSpatialFileFormat(formatType) && !geometryColumnName) {
                initializeSpatialFormat(formatType, nextTable);
              }
            }}
          />
        </Form.Item>
        <Form.Item
          name="dataSourceId"
          label="目标数据源"
          rules={[{ required: true, message: '请选择 S3 数据分发数据源' }]}
        >
          <CanvasS3DataSourceSelect placeholder="选择 S3 数据分发数据源" />
        </Form.Item>
        <Form.Item
          name="targetPath"
          label="目标目录"
          extra="相对于数据源根目录；目录由当前输出独占。"
          rules={[
            { required: true, message: '请输入目标目录' },
            { validator: validateFileOutputPath },
          ]}
        >
          <Input placeholder="例如 exports/district-orders" maxLength={1024} />
        </Form.Item>
        {artifactPreview && (
          <Typography.Text type="secondary" code copyable>{artifactPreview}</Typography.Text>
        )}
        <Form.Item name="conflictPolicy" label="目录冲突策略" rules={[{ required: true }]}>
          <Select options={[
            { value: 'FAIL_IF_EXISTS', label: 'FAIL_IF_EXISTS · 已存在则失败' },
            { value: 'OVERWRITE', label: 'OVERWRITE · 完成暂存后覆盖' },
          ]} />
        </Form.Item>
        <Form.Item name="formatType" label="文件格式" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'CSV', label: 'CSV' },
              { value: 'JSON_LINES', label: 'JSON Lines · 每行一个对象' },
              { value: 'PARQUET', label: 'Parquet · Snappy' },
              { value: 'SHAPEFILE', label: 'Shapefile · SHP 空间文件' },
              { value: 'GEOPARQUET', label: 'GeoParquet 1.1 · 分布式空间数据' },
              { value: 'GEOJSON', label: 'GeoJSON · RFC 7946 单文件' },
            ]}
            onChange={(next: FileOutputFormatType) => {
              if (isSpatialFileFormat(next) && sourceTable) {
                initializeSpatialFormat(next, sourceTable);
              }
            }}
          />
        </Form.Item>

        {formatType === 'CSV' && (
          <>
            <Form.Item name="header" label="输出表头">
              <Select options={[{ value: true, label: '是' }, { value: false, label: '否' }]} />
            </Form.Item>
            {([
              ['delimiter', '分隔符'],
              ['quote', '引用符'],
              ['escape', '转义符'],
            ] as const).map(([name, label]) => (
              <Form.Item
                key={name}
                name={name}
                label={label}
                rules={[{
                  validator: async (_, value: string | undefined) => {
                    if (!value || [...value].length !== 1 || /[\r\n]/.test(value)) {
                      throw new Error(`${label}必须是一个非换行字符`);
                    }
                  },
                }]}
              >
                <Input maxLength={2} />
              </Form.Item>
            ))}
            <Form.Item name="nullValue" label="空值文本">
              <Input placeholder="默认输出为空字符串" />
            </Form.Item>
          </>
        )}
        {formatType === 'JSON_LINES' && (
          <Form.Item name="ignoreNullFields" label="忽略空值字段">
            <Select options={[
              { value: false, label: '否 · 保留所有字段' },
              { value: true, label: '是 · 省略 null 字段' },
            ]} />
          </Form.Item>
        )}

        {isSpatialFileFormat(formatType) && (
          <>
            {(formatType === 'SHAPEFILE' || formatType === 'GEOJSON') && (
              <Form.Item
                name="baseName"
                label="文件基础名"
                extra={formatType === 'SHAPEFILE'
                  ? '不包含 .shp 或 .zip 扩展名。' : '不包含 .geojson 扩展名。'}
                rules={[
                  { required: true, whitespace: true, message: '请输入文件基础名' },
                  {
                    pattern: /^[\p{L}\p{N}][\p{L}\p{N}_-]{0,63}$/u,
                    message: '只能包含中文、字母、数字、_、-，最多 64 个字符',
                  },
                ]}
              >
                <Input placeholder="例如 district_orders" maxLength={64} />
              </Form.Item>
            )}
            <Form.Item
              name="geometryColumnName"
              label="Geometry 字段"
              rules={[{ required: true, message: '请选择 Geometry 字段' }]}
              validateStatus={geometryColumnName && !geometryColumn ? 'error' : undefined}
              help={geometryColumnName && !geometryColumn ? '原 Geometry 字段已失效。' : undefined}
            >
              <Select
                showSearch
                optionFilterProp="label"
                disabled={!sourceTable}
                placeholder="选择一个 Geometry 字段"
                options={[
                  ...(geometryColumnName && !geometryColumn
                    ? [{ value: geometryColumnName, label: `${geometryColumnName}（已失效）`, disabled: true }]
                    : []),
                  ...geometryColumns.map((column) => ({
                    value: column.name,
                    label: `${column.name} · ${column.geometry?.kind ?? 'GEOMETRY'} · EPSG:${column.geometry?.crs.code ?? '-'}`,
                  })),
                ]}
                onChange={(name) => {
                  if (formatType === 'SHAPEFILE') {
                    const selected = geometryColumns.find((column) => column.name === name);
                    const recommended = shapeTypeFor(selected);
                    if (recommended && !targetShapeType) {
                      form.setFieldValue('targetShapeType', recommended);
                    }
                  }
                }}
              />
            </Form.Item>
            {geometryColumn?.geometry && (
              <Space size={6} wrap className="canvas-spatial-metadata-row">
                <Tag color="blue">{geometryColumn.geometry.kind}</Tag>
                <Tag>{`${geometryColumn.geometry.crs.authority}:${geometryColumn.geometry.crs.code}`}</Tag>
                <Tag>{geometryColumn.geometry.dimension}</Tag>
              </Space>
            )}
            {formatType === 'SHAPEFILE' && (
              <>
                <Form.Item name="packageMode" label="输出形态" rules={[{ required: true }]}>
                  <Select options={[
                    { value: 'ZIP', label: 'ZIP · 推荐，五个组件打包' },
                    { value: 'COMPONENT_DIRECTORY', label: '组件目录 · 直接输出五个文件' },
                  ]} />
                </Form.Item>
                <Form.Item
                  name="targetShapeType"
                  label="目标 Shape 类型"
                  rules={[{ required: true, message: '请选择目标 Shape 类型' }]}
                >
                  <Select
                    disabled={!geometryColumn}
                    options={shapeTypeOptions(geometryColumn, targetShapeType)}
                    placeholder={geometryColumn?.geometry?.kind === 'GEOMETRY'
                      ? '通用 Geometry 必须明确选择' : '根据上游类型选择'}
                  />
                </Form.Item>
              </>
            )}
            {formatType === 'GEOPARQUET' && (
              <>
                <Form.Item
                  name="geoParquetCompression"
                  label="压缩方式"
                  rules={[{ required: true }]}
                >
                  <Select options={[
                    { value: 'SNAPPY', label: 'Snappy · 默认，读取更快' },
                    { value: 'ZSTD', label: 'ZSTD · 压缩率更高' },
                  ]} />
                </Form.Item>
                <Form.Item
                  name="geoParquetCoveringMode"
                  label="空间 Covering"
                  rules={[{ required: true }]}
                  extra={geometryColumnName
                    ? `ROW_BBOX 将增加 ${geometryColumnName}_bbox 物理字段。` : undefined}
                >
                  <Select options={[
                    { value: 'ROW_BBOX', label: '生成逐行 bbox · 推荐，利于空间过滤' },
                    { value: 'NONE', label: '不生成逐行 bbox' },
                  ]} />
                </Form.Item>
              </>
            )}
            {formatType === 'GEOJSON' && (
              <>
                <Form.Item
                  name="idColumnName"
                  label="Feature ID 字段"
                  extra="可选；ID 字段仍会保留在 properties 中。"
                  validateStatus={idColumnName && !idColumnValid ? 'error' : undefined}
                  help={idColumnName && !idColumnValid
                    ? '原 Feature ID 字段已失效或类型不再受支持。' : undefined}
                >
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    disabled={!sourceTable}
                    placeholder="不设置 Feature ID"
                    options={[
                      ...(idColumnName && !idColumnValid
                        ? [{
                          value: idColumnName,
                          label: `${idColumnName}（已失效或类型不兼容）`,
                          disabled: true,
                        }]
                        : []),
                      ...(sourceTable?.columns ?? [])
                        .filter((column) => GEOJSON_ID_TYPES.has(column.fieldType))
                        .map((column) => ({
                          value: column.name,
                          label: `${column.name} · ${column.fieldType}`,
                        })),
                    ]}
                  />
                </Form.Item>
                <Form.Item name="ignoreNullProperties" label="忽略 NULL 属性">
                  <Select options={[
                    { value: false, label: '否 · 保留字段并写为 null' },
                    { value: true, label: '是 · 从当前 Feature properties 省略' },
                  ]} />
                </Form.Item>
              </>
            )}
          </>
        )}
      </Form>

      {formatType === 'SHAPEFILE' && (
        <>
          <div className="canvas-processor-section-header">
            <span>
              <Typography.Text strong>DBF 属性映射</Typography.Text>
              <Typography.Text type="secondary">{` · ${attributeMappings.length}/255`}</Typography.Text>
            </span>
            <Space size={4}>
              <Popconfirm
                title="重建 DBF 映射？"
                description="将覆盖当前字段名、顺序和 STRING 字节宽度。"
                okText="重建"
                cancelText="取消"
                onConfirm={() => rebuildMappings()}
              >
                <Button size="small" icon={<ReloadOutlined />}>重建</Button>
              </Popconfirm>
              <Button
                size="small"
                icon={<PlusOutlined />}
                disabled={attributeMappings.length >= 255 || availableAttributeColumns.length === 0}
                onClick={() => {
                  const column = availableAttributeColumns[0];
                  if (!column) return;
                  const used = new Set(attributeMappings.map(
                    (mapping) => mapping.targetFieldName.toUpperCase(),
                  ));
                  updateMappings([...attributeMappings, {
                    sourceColumnName: column.name,
                    targetFieldName: uniqueDbfName(column.name, used, attributeMappings.length + 1),
                    targetStringByteLength: column.fieldType === 'STRING'
                      ? Math.min((column.length ?? 64) * 4, 254) : null,
                  }]);
                }}
              >
                添加
              </Button>
            </Space>
          </div>
          {attributeMappings.length === 0 && (
            <Alert showIcon type="error" title="至少配置一个 DBF 属性字段" />
          )}
          <div className="canvas-processor-rule-list">
            {attributeMappings.map((mapping, index) => {
              const column = sourceTable?.columns.find(
                (candidate) => candidate.name === mapping.sourceColumnName,
              );
              const errors = mappingErrors.get(index) ?? [];
              const columnOptions = [
                ...(mapping.sourceColumnName && !column
                  ? [{
                    value: mapping.sourceColumnName,
                    label: `${mapping.sourceColumnName}（已失效）`,
                    disabled: true,
                  }]
                  : []),
                ...(sourceTable?.columns ?? [])
                  .filter((candidate) => DBF_SUPPORTED_TYPES.has(candidate.fieldType))
                  .map((candidate) => ({
                    value: candidate.name,
                    label: `${candidate.name} · ${candidate.fieldType}`,
                  })),
              ];
              return (
                <Card
                  size="small"
                  key={index}
                  className={`canvas-processor-rule-card${errors.length > 0 ? ' is-invalid' : ''}`}
                  title={(
                    <Space size={6}>
                      <Tag color="green">{index + 1}</Tag>
                      <span>{mapping.targetFieldName || '未命名字段'}</span>
                      {errors.length > 0 && <Tag color="error">存在问题</Tag>}
                    </Space>
                  )}
                  extra={(
                    <Space size={0}>
                      <Button
                        type="text"
                        size="small"
                        icon={<UpOutlined />}
                        disabled={index === 0}
                        aria-label={`上移 DBF 字段 ${index + 1}`}
                        onClick={() => moveMapping(index, index - 1)}
                      />
                      <Button
                        type="text"
                        size="small"
                        icon={<DownOutlined />}
                        disabled={index === attributeMappings.length - 1}
                        aria-label={`下移 DBF 字段 ${index + 1}`}
                        onClick={() => moveMapping(index, index + 1)}
                      />
                      <Button
                        type="text"
                        danger
                        size="small"
                        icon={<DeleteOutlined />}
                        aria-label={`删除 DBF 字段 ${index + 1}`}
                        onClick={() => updateMappings(
                          attributeMappings.filter((_, candidateIndex) => candidateIndex !== index),
                        )}
                      />
                    </Space>
                  )}
                >
                  <Space orientation="vertical" size={8} className="canvas-full-width">
                    <Select
                      showSearch
                      optionFilterProp="label"
                      value={mapping.sourceColumnName || undefined}
                      status={!column ? 'error' : undefined}
                      options={columnOptions}
                      placeholder="来源字段"
                      onChange={(sourceColumnName) => {
                        const nextColumn = sourceTable?.columns.find(
                          (candidate) => candidate.name === sourceColumnName,
                        );
                        updateMapping(index, {
                          ...mapping,
                          sourceColumnName,
                          targetStringByteLength: nextColumn?.fieldType === 'STRING'
                            ? Math.min((nextColumn.length ?? 64) * 4, 254) : null,
                        });
                      }}
                    />
                    <Input
                      value={mapping.targetFieldName}
                      status={!/^[A-Za-z_][A-Za-z0-9_]{0,9}$/.test(mapping.targetFieldName)
                        ? 'error' : undefined}
                      maxLength={10}
                      placeholder="DBF 目标字段名"
                      onChange={(event) => updateMapping(index, {
                        ...mapping,
                        targetFieldName: event.target.value,
                      })}
                    />
                    {column?.fieldType === 'STRING' && (
                      <InputNumber
                        min={1}
                        max={254}
                        precision={0}
                        className="canvas-full-width"
                        addonAfter="UTF-8 字节"
                        value={mapping.targetStringByteLength}
                        onChange={(value) => updateMapping(index, {
                          ...mapping,
                          targetStringByteLength: value,
                        })}
                      />
                    )}
                    {column && column.fieldType !== 'STRING'
                      && mapping.targetStringByteLength !== null && (
                      <Button
                        size="small"
                        danger
                        onClick={() => updateMapping(index, {
                          ...mapping,
                          targetStringByteLength: null,
                        })}
                      >
                        清除遗留 STRING 字节宽度
                      </Button>
                    )}
                    {errors.map((error) => (
                      <Typography.Text type="danger" key={error}>{error}</Typography.Text>
                    ))}
                  </Space>
                </Card>
              );
            })}
          </div>
          <Alert
            showIcon
            type="info"
            title="Shapefile 兼容性约束"
            description="输出包含 SHP、SHX、DBF、PRJ、CPG；DBF 字段名最多 10 位 ASCII，STRING 宽度按 UTF-8 字节计算，NULL 与空字符串可能无法区分。文件由 Driver 串行生成，单个组件及 ZIP 达到 1.8GB 时失败。"
          />
        </>
      )}
      {formatType === 'GEOPARQUET' && (
        <Alert
          showIcon
          type="info"
          title="GeoParquet 1.1 分布式输出"
          description={`这不是普通 Parquet：Geometry 使用 WKB，并从上游 EPSG 元数据生成显式 PROJJSON。首版只支持一个 EPSG + XY Geometry；ROW_BBOX 会增加 ${geometryColumnName || '<Geometry 字段>'}_bbox 物理字段。输出是可并行的 part-*.parquet 目录，不保证 part 数量和记录顺序，Empty Geometry 会在运行时失败。`}
        />
      )}
      {formatType === 'GEOJSON' && (
        <Alert
          showIcon
          type={geometryColumn?.geometry
            && (geometryColumn.geometry.crs.authority !== 'EPSG'
              || geometryColumn.geometry.crs.code !== 4326
              || geometryColumn.geometry.dimension !== 'XY') ? 'warning' : 'info'}
          title="RFC 7946 单文件 FeatureCollection"
          description="仅支持一个 EPSG:4326 + XY Geometry；Geometry 写入 Feature geometry，其余字段写入 properties。NULL Geometry 可以输出，Empty Geometry 不支持。LONG Feature ID 在部分 JavaScript 消费端可能丢失精度。文件由 Driver 串行生成，达到 1.8GB 时失败，大规模空间数据请使用 GeoParquet。"
        />
      )}
      {conflictPolicy === 'OVERWRITE' && (
        <Alert
          showIcon
          type="warning"
          title="S3 覆盖不是原子操作"
          description="系统会先完成临时制品，再替换目标目录；提交中断时可能留下没有 _SUCCESS 的不完整目录。"
        />
      )}
    </Space>
  );
};

export default FileOutputInspector;
