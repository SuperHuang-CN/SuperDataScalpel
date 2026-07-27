import { Col, Form, Input, Row, Select, TreeSelect } from 'antd';
import { directoryTreeSelectData, useDirectoryTree } from '../../../directory';
import { dataServiceAccessModeLabels } from '../../model/dataService';
import { routePathPattern, type DataServiceFormValues } from '../../model/dataServiceEditor';

interface CommonServiceFieldsProps {
  creating: boolean;
  readOnly: boolean;
  canViewDirectories: boolean;
  section: 'identity' | 'routing';
  includeDescription?: boolean;
}

export const CommonServiceFields = ({
  creating,
  readOnly,
  canViewDirectories,
  section,
  includeDescription = section === 'routing',
}: CommonServiceFieldsProps) => {
  const directoriesQuery = useDirectoryTree('DATA_SERVICE', canViewDirectories);

  if (section === 'identity') {
    return (
      <Row gutter={12}>
        <Col xs={24} md={12}>
          <Form.Item<DataServiceFormValues>
            label="服务编码"
            name="code"
            rules={[
              { required: true, whitespace: true, message: '请输入服务编码' },
              { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '以字母开头，仅支持字母、数字和下划线，最长 64 位' },
            ]}
          >
            <Input autoFocus={creating} disabled={readOnly || !creating} placeholder="如：customer_query" />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item<DataServiceFormValues> label="服务名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入服务名称' }, { max: 100 }]}>
            <Input autoFocus={!creating} disabled={readOnly} />
          </Form.Item>
        </Col>
        {canViewDirectories && (
          <Col span={24}>
            <Form.Item<DataServiceFormValues> label="所属目录" name="directoryId">
              <TreeSelect
                allowClear
                treeDefaultExpandAll
                disabled={readOnly}
                loading={directoriesQuery.isFetching}
                treeData={directoryTreeSelectData(directoriesQuery.data ?? [])}
                placeholder="未分类"
              />
            </Form.Item>
          </Col>
        )}
        {includeDescription && (
          <Col span={24}>
            <Form.Item<DataServiceFormValues> label="说明" name="description" rules={[{ max: 1000 }]}>
              <Input.TextArea disabled={readOnly} rows={3} maxLength={1000} showCount />
            </Form.Item>
          </Col>
        )}
      </Row>
    );
  }

  return (
    <>
      <Form.Item<DataServiceFormValues>
        label="访问模式"
        name="accessMode"
        extra="订阅访问会在发布时启用 API Key 认证和服务级授权。"
        rules={[{ required: true, message: '请选择访问模式' }]}
      >
        <Select
          disabled={readOnly}
          options={Object.entries(dataServiceAccessModeLabels).map(([value, label]) => ({ value, label }))}
        />
      </Form.Item>
      <Form.Item<DataServiceFormValues>
        label="公开路由"
        name="routePath"
        rules={[
          { required: true, whitespace: true, message: '请输入公开路由' },
          {
            validator: async (_, value: string | undefined) => {
              const normalized = value?.trim().toLowerCase() ?? '';
              if (!routePathPattern.test(normalized) || normalized.endsWith('/') || normalized.includes('//')) {
                throw new Error('路由必须是 /open-api/v1/ 下的小写静态路径，且不能以 / 结尾');
              }
            },
          },
        ]}
      >
        <Input disabled={readOnly} placeholder="如：/open-api/v1/customers" />
      </Form.Item>
      {includeDescription && (
        <Form.Item<DataServiceFormValues> label="说明" name="description" rules={[{ max: 1000 }]}>
          <Input.TextArea disabled={readOnly} rows={3} maxLength={1000} showCount />
        </Form.Item>
      )}
    </>
  );
};
