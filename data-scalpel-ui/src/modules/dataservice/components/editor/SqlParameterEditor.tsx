import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Col, Form, Input, InputNumber, Row, Select, Space, Switch, Typography } from 'antd';
import type { PlatformDataType } from '../../../model';
import {
  parameterNamePattern,
  parameterTypes,
  typeLabels,
  type DataServiceFormValues,
  type SqlParameterFormValue,
} from '../../model/dataServiceEditor';

interface SqlParameterEditorProps {
  form: ReturnType<typeof Form.useForm<DataServiceFormValues>>[0];
  readOnly: boolean;
  canTest: boolean;
  testValues: Record<string, string>;
  onTestValuesChange: (values: Record<string, string>) => void;
}

const testInput = (
  parameter: SqlParameterFormValue,
  value: string | undefined,
  disabled: boolean,
  onChange: (value: string | undefined) => void,
) => parameter.type === 'BOOLEAN' ? (
  <Select
    allowClear
    disabled={disabled}
    options={[{ value: 'true', label: 'true' }, { value: 'false', label: 'false' }]}
    value={value}
    onChange={onChange}
  />
) : (
  <Input
    disabled={disabled}
    value={value}
    onChange={(event) => onChange(event.target.value)}
    placeholder={parameter.required ? '必填测试值' : '留空绑定 NULL'}
  />
);

export const SqlParameterEditor = ({
  form,
  readOnly,
  canTest,
  testValues,
  onTestValuesChange,
}: SqlParameterEditorProps) => {
  const parameters = Form.useWatch('parameters', form) ?? [];

  return (
    <>
      <Form.List name="parameters">
        {(fields, { add, remove }) => (
          <Space orientation="vertical" className="data-service-parameter-list" size={8}>
            {fields.length === 0 && <Typography.Text type="secondary">当前 SQL 没有声明参数。</Typography.Text>}
            {fields.map((field) => {
              const parameterType = parameters[field.name]?.type;
              return (
                <Row gutter={[8, 8]} key={field.key} align="middle" className="data-service-parameter-row">
                  <Col xs={24} lg={4}>
                    <Form.Item name={[field.name, 'name']} rules={[{ required: true, message: '请输入参数名' }, { pattern: parameterNamePattern, message: '参数名格式不正确' }]} noStyle>
                      <Input disabled={readOnly} placeholder="参数名" />
                    </Form.Item>
                  </Col>
                  <Col xs={12} lg={4}>
                    <Form.Item name={[field.name, 'type']} rules={[{ required: true, message: '请选择类型' }]} noStyle>
                      <Select disabled={readOnly} placeholder="类型" options={parameterTypes.map((type) => ({ value: type, label: typeLabels[type] }))} />
                    </Form.Item>
                  </Col>
                  <Col xs={12} lg={4}>
                    {parameterType === 'STRING' && (
                      <Form.Item name={[field.name, 'length']} noStyle>
                        <InputNumber disabled={readOnly} min={1} placeholder="最大长度（可空）" className="data-service-full-width" />
                      </Form.Item>
                    )}
                    {parameterType === 'DECIMAL' && (
                      <Space.Compact block>
                        <Form.Item name={[field.name, 'precision']} rules={[{ required: true, message: '请输入精度' }]} noStyle>
                          <InputNumber disabled={readOnly} min={1} max={38} placeholder="精度" className="data-service-half-width" />
                        </Form.Item>
                        <Form.Item name={[field.name, 'scale']} rules={[{ required: true, message: '请输入小数位' }]} noStyle>
                          <InputNumber disabled={readOnly} min={0} max={38} placeholder="小数位" className="data-service-half-width" />
                        </Form.Item>
                      </Space.Compact>
                    )}
                    {!['STRING', 'DECIMAL'].includes(parameterType ?? '') && <Typography.Text type="secondary">无类型参数</Typography.Text>}
                  </Col>
                  <Col xs={8} lg={3}>
                    <Form.Item name={[field.name, 'required']} valuePropName="checked" noStyle>
                      <Switch disabled={readOnly} checkedChildren="必填" unCheckedChildren="可选" />
                    </Form.Item>
                  </Col>
                  <Col xs={14} lg={7}>
                    <Form.Item name={[field.name, 'description']} noStyle>
                      <Input disabled={readOnly} maxLength={500} placeholder="参数说明" />
                    </Form.Item>
                  </Col>
                  <Col xs={2} lg={2}>
                    {!readOnly && <Button danger type="text" icon={<DeleteOutlined />} aria-label="删除 SQL 参数" onClick={() => remove(field.name)} />}
                  </Col>
                </Row>
              );
            })}
            {!readOnly && <Button type="dashed" icon={<PlusOutlined />} disabled={fields.length >= 50} onClick={() => add({ type: 'STRING' satisfies PlatformDataType, required: true })}>添加参数</Button>}
          </Space>
        )}
      </Form.List>

      {parameters.filter((parameter) => parameter?.name).length > 0 && (
        <div className="data-service-test-arguments">
          <Typography.Text strong>测试参数</Typography.Text>
          <Typography.Text type="secondary">仅用于当前测试，不会保存到服务定义。</Typography.Text>
          <Row gutter={[8, 4]}>
            {parameters.filter((parameter) => parameter?.name).map((parameter, index) => {
              const name = parameter.name ?? '';
              return (
                <Col xs={24} md={12} xl={8} key={`${name}-${index}`}>
                  <Form.Item label={`${name} · ${parameter.type ?? '未选择类型'}`}>
                    {testInput(parameter, testValues[name], !canTest, (value) => onTestValuesChange({ ...testValues, [name]: value ?? '' }))}
                  </Form.Item>
                </Col>
              );
            })}
          </Row>
        </div>
      )}
    </>
  );
};
