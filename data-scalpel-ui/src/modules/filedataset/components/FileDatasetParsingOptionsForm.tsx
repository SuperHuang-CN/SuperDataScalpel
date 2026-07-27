import { Alert, Col, Form, Input, InputNumber, Row, Select, Switch } from 'antd';
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
        <Col span={6}><Form.Item label="引号字符" name="quoteCharacter" rules={[{ max: 1, message: '只能输入一个字符' }]}><Input /></Form.Item></Col>
        <Col span={6}><Form.Item label="转义字符" name="escapeCharacter" rules={[{ max: 1, message: '只能输入一个字符' }]}><Input /></Form.Item></Col>
        <Col span={12}><Form.Item label="首行为表头" name="firstRowHeader" valuePropName="checked"><Switch /></Form.Item></Col>
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
    case 'EXCEL': return (
      <Row gutter={12}>
        <Col span={12}><Form.Item label="表头行" name="headerRowIndex" extra="从 0 开始。" rules={[{ required: true, message: '请输入表头行' }]}><InputNumber min={0} precision={0} className="file-dataset-number-input" /></Form.Item></Col>
        <Col span={12}><Form.Item label="数据起始行" name="dataStartRowIndex" extra="必须在表头行之后。" rules={[{ required: true, message: '请输入数据起始行' }]}><InputNumber min={1} precision={0} className="file-dataset-number-input" /></Form.Item></Col>
      </Row>
    );
    case 'PARQUET': return <Alert type="info" showIcon message="Parquet 自带字段类型和编码信息，无额外解析参数。" />;
    case 'AVRO': return <Alert type="info" showIcon message="Avro Object Container File 自带 Schema 和 codec，无额外解析参数。" />;
    case 'GDB': return <Alert type="info" showIcon message="FileGDB 图层目录和空间元数据由系统自动发现，无额外解析参数。" />;
    case 'SHP': return (
      <>
        <Alert
          type="info"
          showIcon
          className="file-dataset-form-alert"
          message="每个 ZIP 必须包含且只包含一套同名 .shp/.shx/.dbf，可选携带 .cpg/.prj。"
        />
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
        </Row>
      </>
    );
  }
};
