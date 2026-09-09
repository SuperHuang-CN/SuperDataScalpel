import { CheckCircleOutlined } from '@ant-design/icons';
import { Col, Form, Input, InputNumber, Row, Select, Switch, Typography } from 'antd';
import type { FileDatasetType, FileRecordDelimiter } from '../model/fileDataset';

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

export const FileDatasetParsingOptionsFields = ({ type }: { type: FileDatasetType }) => {
  switch (type) {
    case 'CSV':
    case 'TSV': return (
      <Row gutter={12}>
        <Col span={12}><CharsetField /></Col>
        <Col span={12}>
          <Form.Item label="字段分隔符" name="fieldDelimiter" rules={[{ required: true, message: '请选择字段分隔符' }]}>
            <Select
              options={type === 'TSV' ? fieldDelimiterOptions.filter((option) => option.value === '\t') : fieldDelimiterOptions}
              disabled={type === 'TSV'}
            />
          </Form.Item>
        </Col>
        <Col span={12}><RecordDelimiterField /></Col>
        <Col span={6}><Form.Item label="引号字符" name="quoteCharacter" rules={[{ max: 1, message: '只能输入一个字符' }]}><Input name="file-dataset-quote-character" autoComplete="off" /></Form.Item></Col>
        <Col span={6}><Form.Item label="转义字符" name="escapeCharacter" rules={[{ max: 1, message: '只能输入一个字符' }]}><Input name="file-dataset-escape-character" autoComplete="off" /></Form.Item></Col>
        <Col span={12}><Form.Item label="首行为表头" name="firstRowHeader" valuePropName="checked"><Switch aria-label="首行为表头" /></Form.Item></Col>
      </Row>
    );
    case 'TXT':
    case 'JSONL': return <Row gutter={12}><Col span={12}><CharsetField /></Col><Col span={12}><RecordDelimiterField /></Col></Row>;
    case 'JSON': return (
      <Row gutter={12}>
        <Col span={12}><CharsetField /></Col>
        <Col span={12}>
          <Form.Item label="数据根路径" name="rootPointer" extra="可选，使用 JSON Pointer，例如 /data/items。">
            <Input name="file-dataset-json-root-pointer" autoComplete="off" placeholder="/data/items" />
          </Form.Item>
        </Col>
      </Row>
    );
    case 'GEOJSON':
    case 'GEOJSONL': return (
      <Form.Item
        label="EPSG code"
        name="epsgCode"
        extra={type === 'GEOJSONL'
          ? '每个非空物理行必须是一个 GeoJSON Feature；EPSG 只声明坐标，不转换。'
          : '声明坐标值的 EPSG；系统不自动转换坐标。'}
        rules={[{ required: true, message: '请输入 EPSG code' }]}
      >
        <InputNumber name={`file-dataset-${type.toLowerCase()}-epsg-code`} min={1} precision={0} className="file-dataset-number-input" />
      </Form.Item>
    );
    case 'EXCEL': return (
      <Row gutter={12}>
        <Col span={12}><Form.Item label="表头行" name="headerRowIndex" extra="从 0 开始。" rules={[{ required: true, message: '请输入表头行' }]}><InputNumber name="file-dataset-header-row-index" min={0} precision={0} className="file-dataset-number-input" /></Form.Item></Col>
        <Col span={12}><Form.Item label="数据起始行" name="dataStartRowIndex" extra="必须在表头行之后。" rules={[{ required: true, message: '请输入数据起始行' }]}><InputNumber name="file-dataset-data-start-row-index" min={1} precision={0} className="file-dataset-number-input" /></Form.Item></Col>
      </Row>
    );
    case 'PARQUET': return (
      <div className="file-dataset-native-parsing">
        <CheckCircleOutlined aria-hidden="true" />
        <span>
          <strong>无需额外解析参数</strong>
          <Typography.Text type="secondary">Parquet 文件自带字段类型和编码信息。</Typography.Text>
        </span>
      </div>
    );
    case 'GEOPARQUET': return (
      <div className="file-dataset-native-parsing">
        <CheckCircleOutlined aria-hidden="true" />
        <span>
          <strong>按 GeoParquet Footer 自动识别</strong>
          <Typography.Text type="secondary">读取 WKB Geometry、CRS 和 Geometry 类型；不提供手工 EPSG 覆盖。</Typography.Text>
        </span>
      </div>
    );
    case 'GPKG': return (
      <div className="file-dataset-native-parsing">
        <CheckCircleOutlined aria-hidden="true" />
        <span>
          <strong>按 GeoPackage 元数据自动识别</strong>
          <Typography.Text type="secondary">后台发现 features 图层和 attributes 属性表；每个空间图层从文件识别 CRS。</Typography.Text>
        </span>
      </div>
    );
    case 'AVRO': return (
      <div className="file-dataset-native-parsing">
        <CheckCircleOutlined aria-hidden="true" />
        <span>
          <strong>无需额外解析参数</strong>
          <Typography.Text type="secondary">Avro Object Container File 自带 Schema 和 codec。</Typography.Text>
        </span>
      </div>
    );
    case 'GDB': return (
      <Form.Item
        label="回退 EPSG code"
        name="epsgCode"
        extra="可选；仅在图层 WKT 没有明确 EPSG 标识时使用，不执行坐标转换。"
      >
        <InputNumber name="file-dataset-gdb-epsg-code" min={1} precision={0} placeholder="例如 4490" className="file-dataset-number-input" />
      </Form.Item>
    );
    case 'SHP': return (
      <Row gutter={12}>
          <Col span={12}>
            <Form.Item
              label="强制 DBF 编码"
              name="dbfCharsetOverride"
              extra="可选；设置后优先于 CPG 和 DBF Language Driver。"
            >
              <Select allowClear showSearch options={charsetOptions} placeholder="按文件元数据自动识别" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label="DBF 回退编码"
              name="dbfFallbackCharset"
              extra="仅在没有强制编码、CPG 和已知 DBF 标记时使用。"
              rules={[{ required: true, message: '请选择 DBF 回退编码' }]}
            >
              <Select showSearch options={charsetOptions} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label="回退 EPSG code"
              name="epsgCode"
              extra="可选；仅在 PRJ 没有明确 EPSG 标识时使用，不执行坐标转换。"
            >
              <InputNumber name="file-dataset-shp-epsg-code" min={1} precision={0} placeholder="例如 4490" className="file-dataset-number-input" />
            </Form.Item>
          </Col>
        </Row>
    );
  }
};
