import { Col, Form, Input, Row, TreeSelect } from 'antd';
import { directoryTreeSelectData, useDirectoryTree } from '../../../directory';
import { type DataServiceFormValues } from '../../model/dataServiceEditor';

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
            <Input
              name="data-service-code"
              autoComplete="off"
              autoFocus={creating}
              disabled={readOnly || !creating}
              placeholder="如：customer_query"
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item<DataServiceFormValues> label="服务名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入服务名称' }, { max: 100 }]}>
            <Input
              name="data-service-display-name"
              autoComplete="off"
              autoFocus={!creating}
              disabled={readOnly}
            />
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
              <Input.TextArea
                name="data-service-description"
                autoComplete="off"
                disabled={readOnly}
                autoSize={{ minRows: 2, maxRows: 5 }}
                maxLength={1000}
                showCount
              />
            </Form.Item>
          </Col>
        )}
      </Row>
    );
  }

  return includeDescription ? (
    <Form.Item<DataServiceFormValues> label="说明" name="description" rules={[{ max: 1000 }]}>
      <Input.TextArea
        name="data-service-description"
        autoComplete="off"
        disabled={readOnly}
        autoSize={{ minRows: 2, maxRows: 5 }}
        maxLength={1000}
        showCount
      />
    </Form.Item>
  ) : null;
};
