import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Col, Form, Input, Row, Select, Space, Typography } from 'antd';
import type { ConnectionOptionDefinition } from '../model/dataSource';
import {
  JDBC_CONNECTION_OPTION_KEY_PATTERN,
  MAX_JDBC_CONNECTION_OPTIONS,
  MAX_JDBC_CONNECTION_OPTION_VALUE_LENGTH,
  jdbcCustomConnectionOptionKeyError,
  type JdbcConnectionOptionFormRow,
  validateJdbcConnectionOptions,
} from '../model/jdbcConnectionOptions';

interface JdbcConnectionOptionsFieldsProps {
  definitions: ConnectionOptionDefinition[];
}

const DefinedOptionField = ({ definition }: { definition: ConnectionOptionDefinition }) => (
  <Col span={12}>
    <Form.Item
      label={definition.label}
      name={['connection', 'options', definition.key]}
      rules={[{ max: MAX_JDBC_CONNECTION_OPTION_VALUE_LENGTH, message: `${definition.label}不能超过 512 个字符` }]}
    >
      {definition.type === 'TEXT'
        ? <Input maxLength={MAX_JDBC_CONNECTION_OPTION_VALUE_LENGTH} placeholder={definition.defaultValue ?? '可选'} />
        : <Select allowClear placeholder="使用驱动默认值" options={definition.choices} />}
    </Form.Item>
  </Col>
);

export const JdbcConnectionOptionsFields = ({ definitions }: JdbcConnectionOptionsFieldsProps) => {
  const form = Form.useFormInstance();

  return (
    <>
      {definitions.length > 0 && <Row gutter={12}>{definitions.map((definition) => (
        <DefinedOptionField definition={definition} key={definition.key} />
      ))}</Row>}
      <Typography.Text type="secondary" className="jdbc-options-help">
        自定义参数由 JDBC 驱动解释，请填写参数名和值，不要输入 ?、&、; 或完整 JDBC URL。敏感参数请勿放在这里。
      </Typography.Text>
      <Form.List
        name={['connection', 'customOptions']}
        rules={[{
          validator: async (_, rows: JdbcConnectionOptionFormRow[] | undefined) => {
            const predefinedOptions = form.getFieldValue(['connection', 'options']) as Record<string, string> | undefined;
            let validationError: string | undefined;
            try {
              validationError = validateJdbcConnectionOptions(predefinedOptions, rows, definitions);
            } catch (error) {
              validationError = error instanceof Error ? error.message : 'JDBC 连接参数无效';
            }
            if (validationError) throw new Error(validationError);
          },
        }]}
      >
        {(fields, { add, remove }, { errors }) => (
          <Space orientation="vertical" size={6} className="jdbc-custom-options">
            {fields.map((field, index) => (
              <Row gutter={8} align="top" key={field.key} wrap={false}>
                <Col flex="1 1 42%">
                  <Form.Item
                    name={[field.name, 'key']}
                    rules={[
                      { required: true, whitespace: true, message: '请输入参数名' },
                      { max: 64, message: '参数名不能超过 64 个字符' },
                      { pattern: JDBC_CONNECTION_OPTION_KEY_PATTERN, message: '参数名格式无效' },
                      {
                        validator: async (_, value: string | undefined) => {
                          const keyError = jdbcCustomConnectionOptionKeyError(value, definitions);
                          if (keyError) throw new Error(keyError);
                        },
                      },
                    ]}
                  >
                    <Input placeholder="参数名，如 tcpKeepAlive" />
                  </Form.Item>
                </Col>
                <Col flex="1 1 52%">
                  <Form.Item
                    name={[field.name, 'value']}
                    rules={[
                      { required: true, whitespace: true, message: '请输入参数值' },
                      { max: MAX_JDBC_CONNECTION_OPTION_VALUE_LENGTH, message: '参数值不能超过 512 个字符' },
                    ]}
                  >
                    <Input maxLength={MAX_JDBC_CONNECTION_OPTION_VALUE_LENGTH} placeholder="参数值" />
                  </Form.Item>
                </Col>
                <Col flex="none">
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label={`删除自定义连接参数 ${index + 1}`}
                    onClick={() => remove(field.name)}
                  />
                </Col>
              </Row>
            ))}
            <Button
              type="dashed"
              className="jdbc-add-option-button"
              icon={<PlusOutlined />}
              disabled={fields.length >= MAX_JDBC_CONNECTION_OPTIONS}
              onClick={() => add({ key: '', value: '' })}
            >
              添加自定义参数
            </Button>
            <Form.ErrorList errors={errors} />
          </Space>
        )}
      </Form.List>
    </>
  );
};
