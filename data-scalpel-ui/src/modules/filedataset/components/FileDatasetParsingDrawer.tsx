import type { TableProps } from 'antd';
import { Alert, Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Spin, Switch, Table, Tag, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useConfigureFileDatasetParsing,
  useFileDatasetParsing,
  useFileDatasetPreview,
  useParseFileDataset,
} from '../hooks/useFileDatasets';
import type {
  FileDataset,
  FileDatasetField,
  FileDatasetFormat,
  FileDatasetParsingOptions,
  FileRecordDelimiter,
} from '../model/fileDataset';

interface FileDatasetParsingDrawerProps {
  fileDataset: FileDataset | null;
  open: boolean;
  onClose: () => void;
}

interface ParsingFormValues {
  charset?: string;
  fieldDelimiter?: string;
  recordDelimiter?: FileRecordDelimiter;
  quoteCharacter?: string;
  escapeCharacter?: string;
  firstRowHeader?: boolean;
  rootPointer?: string;
  sheetName?: string;
  headerRowIndex?: number;
  dataStartRowIndex?: number;
  layerName?: string;
}

const charsetOptions = ['UTF-8', 'GBK', 'GB18030', 'ISO-8859-1'].map((value) => ({ value, label: value }));
const recordDelimiterOptions: { value: FileRecordDelimiter; label: string }[] = [
  { value: 'AUTO', label: '自动识别' },
  { value: 'LF', label: 'LF（Unix）' },
  { value: 'CRLF', label: 'CRLF（Windows）' },
  { value: 'CR', label: 'CR（旧版 Mac）' },
];
const fieldDelimiterOptions = [
  { value: ',', label: '逗号 (,)' },
  { value: '\t', label: '制表符 (Tab)' },
  { value: ';', label: '分号 (;)' },
  { value: '|', label: '竖线 (|)' },
];
const supportedFormats: FileDatasetFormat[] = ['CSV', 'TSV', 'TXT', 'JSON', 'JSONL', 'XLS', 'XLSX', 'PARQUET', 'AVRO'];

const defaultValues = (format: FileDatasetFormat): ParsingFormValues => {
  switch (format) {
    case 'CSV': return { charset: 'UTF-8', fieldDelimiter: ',', recordDelimiter: 'AUTO', quoteCharacter: '"', escapeCharacter: '\\', firstRowHeader: true };
    case 'TSV': return { charset: 'UTF-8', fieldDelimiter: '\t', recordDelimiter: 'AUTO', quoteCharacter: '"', escapeCharacter: '\\', firstRowHeader: true };
    case 'TXT':
    case 'JSONL': return { charset: 'UTF-8', recordDelimiter: 'AUTO' };
    case 'JSON': return { charset: 'UTF-8' };
    case 'XLS':
    case 'XLSX': return { headerRowIndex: 0, dataStartRowIndex: 1 };
    case 'SHP': return { charset: 'UTF-8' };
    case 'GDB':
    case 'PARQUET':
    case 'AVRO':
    case 'OTHER': return {};
  }
};

const valuesFromOptions = (format: FileDatasetFormat, options: FileDatasetParsingOptions | null): ParsingFormValues => {
  if (!options) return defaultValues(format);
  switch (options.kind) {
    case 'CSV': return options;
    case 'TEXT': return options;
    case 'JSON': return options;
    case 'JSON_LINES': return options;
    case 'SPREADSHEET': return options;
    case 'PARQUET': return {};
    case 'AVRO': return {};
    case 'SHAPEFILE': return options;
    case 'FILE_GDB': return options;
  }
};

const buildOptions = (format: FileDatasetFormat, values: ParsingFormValues): FileDatasetParsingOptions => {
  switch (format) {
    case 'CSV': return {
      kind: 'CSV',
      charset: values.charset as string,
      fieldDelimiter: values.fieldDelimiter as string,
      recordDelimiter: values.recordDelimiter as FileRecordDelimiter,
      quoteCharacter: values.quoteCharacter || undefined,
      escapeCharacter: values.escapeCharacter || undefined,
      firstRowHeader: values.firstRowHeader ?? true,
    };
    case 'TSV': return {
      kind: 'CSV',
      charset: values.charset as string,
      fieldDelimiter: '\t',
      recordDelimiter: values.recordDelimiter as FileRecordDelimiter,
      quoteCharacter: values.quoteCharacter || undefined,
      escapeCharacter: values.escapeCharacter || undefined,
      firstRowHeader: values.firstRowHeader ?? true,
    };
    case 'TXT': return { kind: 'TEXT', charset: values.charset as string, recordDelimiter: values.recordDelimiter as FileRecordDelimiter };
    case 'JSON': return { kind: 'JSON', charset: values.charset as string, rootPointer: values.rootPointer || undefined };
    case 'JSONL': return { kind: 'JSON_LINES', charset: values.charset as string, recordDelimiter: values.recordDelimiter as FileRecordDelimiter };
    case 'XLS':
    case 'XLSX': return {
      kind: 'SPREADSHEET',
      sheetName: values.sheetName || undefined,
      headerRowIndex: values.headerRowIndex as number,
      dataStartRowIndex: values.dataStartRowIndex as number,
    };
    case 'PARQUET': return { kind: 'PARQUET' };
    case 'AVRO': return { kind: 'AVRO' };
    case 'SHP': return { kind: 'SHAPEFILE', charset: values.charset as string, layerName: values.layerName || undefined };
    case 'GDB': return { kind: 'FILE_GDB', layerName: values.layerName || undefined };
    case 'OTHER': throw new Error('其他格式暂不支持解析配置');
  }
};

const CharsetField = () => (
  <Form.Item label="文件编码" name="charset" rules={[{ required: true, message: '请选择或输入文件编码' }]}>
    <Select showSearch options={charsetOptions} placeholder="选择常用编码" />
  </Form.Item>
);

const RecordDelimiterField = () => (
  <Form.Item label="记录分隔符" name="recordDelimiter" rules={[{ required: true, message: '请选择记录分隔符' }]}>
    <Select options={recordDelimiterOptions} />
  </Form.Item>
);

const ParsingFields = ({ format }: { format: FileDatasetFormat }) => {
  switch (format) {
    case 'CSV':
    case 'TSV': return (
      <Row gutter={12}>
        <Col span={12}><CharsetField /></Col>
        <Col span={12}>
          <Form.Item label="字段分隔符" name="fieldDelimiter" rules={[{ required: true, message: '请选择字段分隔符' }]}>
            <Select
              options={format === 'TSV' ? fieldDelimiterOptions.filter((option) => option.value === '\t') : fieldDelimiterOptions}
              disabled={format === 'TSV'}
            />
          </Form.Item>
        </Col>
        <Col span={12}><RecordDelimiterField /></Col>
        <Col span={6}>
          <Form.Item label="引号字符" name="quoteCharacter" rules={[{ max: 1, message: '只能输入一个字符' }]}><Input /></Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item label="转义字符" name="escapeCharacter" rules={[{ max: 1, message: '只能输入一个字符' }]}><Input /></Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="首行为表头" name="firstRowHeader" valuePropName="checked"><Switch /></Form.Item>
        </Col>
      </Row>
    );
    case 'TXT':
    case 'JSONL': return <Row gutter={12}><Col span={12}><CharsetField /></Col><Col span={12}><RecordDelimiterField /></Col></Row>;
    case 'JSON': return (
      <Row gutter={12}>
        <Col span={12}><CharsetField /></Col>
        <Col span={12}>
          <Form.Item label="数据根路径" name="rootPointer" extra="可选，使用 JSON Pointer，例如 /data/items。">
            <Input placeholder="/data/items" />
          </Form.Item>
        </Col>
      </Row>
    );
    case 'XLS':
    case 'XLSX': return (
      <Row gutter={12}>
        <Col span={12}><Form.Item label="工作表名称" name="sheetName" extra="留空时使用第一个工作表。"><Input /></Form.Item></Col>
        <Col span={6}><Form.Item label="表头行" name="headerRowIndex" extra="从 0 开始；0 表示第一行。" rules={[{ required: true, message: '请输入表头行' }]}><InputNumber min={0} precision={0} className="file-dataset-number-input" /></Form.Item></Col>
        <Col span={6}><Form.Item label="数据起始行" name="dataStartRowIndex" extra="从 0 开始，且必须在表头行之后。" rules={[{ required: true, message: '请输入数据起始行' }]}><InputNumber min={1} precision={0} className="file-dataset-number-input" /></Form.Item></Col>
      </Row>
    );
    case 'SHP': return (
      <Row gutter={12}>
        <Col span={12}><CharsetField /></Col>
        <Col span={12}><Form.Item label="图层名称" name="layerName" extra="ZIP 中只有一个图层时可留空。"><Input /></Form.Item></Col>
      </Row>
    );
    case 'GDB': return <Form.Item label="图层名称" name="layerName" extra="GDB 中只有一个图层时可留空。"><Input /></Form.Item>;
    case 'PARQUET': return <Alert type="info" showIcon message="Parquet 自带字段类型和编码信息，当前无需额外参数。" />;
    case 'AVRO': return <Alert type="info" showIcon message="Avro Object Container File 自带 Schema 和 codec 信息，当前无需额外参数。" />;
    case 'OTHER': return <Alert type="warning" showIcon message="其他格式暂不支持解析配置。" />;
  }
};

interface PreviewRow {
  key: number;
  values: unknown[];
}

const formatPreviewValue = (value: unknown): string => {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'string') return value;
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
};

const previewColumns = (fields: FileDatasetField[]): TableProps<PreviewRow>['columns'] => fields.map((field, index) => ({
  title: <Space size={4}>{field.name}<Tag>{field.logicalType}</Tag></Space>,
  key: field.name,
  width: 180,
  ellipsis: true,
  render: (_: unknown, row: PreviewRow) => formatPreviewValue(row.values[index]),
}));

export const FileDatasetParsingDrawer = ({ fileDataset, open, onClose }: FileDatasetParsingDrawerProps) => {
  const [form] = Form.useForm<ParsingFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const parsingQuery = useFileDatasetParsing(fileDataset?.id, open);
  const configureMutation = useConfigureFileDatasetParsing();
  const parseMutation = useParseFileDataset();
  const parsing = parsingQuery.data;
  const parseStatus = parsing?.parseStatus;
  const previewQuery = useFileDatasetPreview(fileDataset?.id, open && parseStatus === 'READY');
  const configurationUnsupported = !fileDataset || fileDataset.format === 'OTHER';
  const parsingUnsupported = !fileDataset || !supportedFormats.includes(fileDataset.format);

  useEffect(() => {
    if (!open || !fileDataset) return;
    form.resetFields();
    form.setFieldsValue(valuesFromOptions(fileDataset.format, parsingQuery.data?.options ?? null));
  }, [fileDataset, form, open, parsingQuery.data?.options]);

  const submit = async (values: ParsingFormValues) => {
    if (!fileDataset) return;
    try {
      await configureMutation.mutateAsync({ id: fileDataset.id, options: buildOptions(fileDataset.format, values) });
      messageApi.success('解析参数已保存');
    } catch (error) {
      messageApi.error(error instanceof ApiError || error instanceof Error ? error.message : '保存解析参数失败');
    }
  };

  const parse = async () => {
    if (!fileDataset) return;
    if (!parsingQuery.data?.configured) {
      messageApi.warning('请先保存解析参数');
      return;
    }
    try {
      const response = await parseMutation.mutateAsync(fileDataset.id);
      if (response.parseStatus === 'READY') {
        messageApi.success(`解析完成，识别到 ${response.fields.length} 个字段`);
      } else {
        messageApi.error(response.parseError || '文件解析失败');
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError || error instanceof Error ? error.message : '执行文件解析失败');
    }
  };

  const previewRows: PreviewRow[] = (previewQuery.data?.rows ?? []).map((values, index) => ({ key: index, values }));

  return (
    <>
      {messageContext}
      <Drawer
        title={`解析设置 · ${fileDataset?.name ?? ''}`}
        open={open}
        size={620}
        className="file-dataset-drawer"
        onClose={onClose}
        destroyOnHidden
        footer={(
          <Space>
            <Button onClick={onClose}>关闭</Button>
            {!configurationUnsupported && <Button type="primary" loading={configureMutation.isPending} onClick={() => form.submit()}>保存解析设置</Button>}
            {!parsingUnsupported && <Button loading={parseMutation.isPending} disabled={!parsingQuery.data?.configured} onClick={() => void parse()}>执行解析</Button>}
          </Space>
        )}
      >
        <Alert
          type="info"
          showIcon
          className="file-dataset-form-alert"
          message={configurationUnsupported ? '该格式暂不支持解析配置' : parsingUnsupported ? '该格式尚未支持真实解析' : '保存参数后执行抽样解析'}
          description={configurationUnsupported
            ? '其他格式暂不支持保存或执行解析。'
            : parsingUnsupported
            ? '可以保存解析参数；空间文件的真实解析仍在后续阶段实现。'
            : '解析会最多读取 1,000 条记录，保存字段元数据；下方预览最多显示 50 条记录。'}
        />
        <Spin spinning={parsingQuery.isFetching || parseMutation.isPending}>
          {parsingQuery.isError && <Alert type="error" showIcon className="file-dataset-form-alert" message="解析配置加载失败" />}
          {parsingQuery.data?.parseError && <Alert type="error" showIcon className="file-dataset-form-alert" message="上次解析失败" description={parsingQuery.data.parseError} />}
          <Form<ParsingFormValues> form={form} layout="vertical" onFinish={(values) => void submit(values)}>
            {fileDataset && <ParsingFields format={fileDataset.format} />}
          </Form>
          {parsing?.parseStatus === 'READY' && (
            <>
              <Alert
                type="success"
                showIcon
                className="file-dataset-form-alert"
                message={`已解析 ${parsing.sampledRecordCount} 条样本记录，识别到 ${parsing.fields.length} 个字段`}
                description={parsing.truncated ? '文件记录数超过抽样上限，字段类型由前 1,000 条记录推断。' : undefined}
              />
              <Table<PreviewRow>
                size="small"
                rowKey="key"
                columns={previewColumns(previewQuery.data?.fields ?? parsing.fields)}
                dataSource={previewRows}
                loading={previewQuery.isFetching}
                pagination={false}
                scroll={{ x: 'max-content', y: 280 }}
                locale={{ emptyText: previewQuery.isError ? '样本预览加载失败' : '文件没有可预览的记录' }}
              />
            </>
          )}
        </Spin>
      </Drawer>
    </>
  );
};
