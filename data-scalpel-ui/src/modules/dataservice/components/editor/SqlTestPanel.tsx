import { Alert, Empty, Space, Table, Tag, Typography } from 'antd';
import type { SqlServiceTestResponse } from '../../model/dataService';
import { typeDescription } from '../../model/dataServiceEditor';

interface SqlTestPanelProps {
  result: SqlServiceTestResponse | null;
}

export const SqlTestPanel = ({ result }: SqlTestPanelProps) => {
  if (!result) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未执行 SQL 测试" />;

  const previewColumns = result.resultFields.map((field) => ({
    title: field.name,
    dataIndex: field.name,
    key: field.name,
    ellipsis: true,
    render: (value: unknown) => value === null || value === undefined ? '—' : String(value),
  }));

  return (
    <Space orientation="vertical" size={12} className="data-service-test-result">
      {result.valid ? (
        <Alert type="success" showIcon message={`SQL 测试通过，耗时 ${result.elapsedMs} ms`} />
      ) : (
        <Alert
          type="error"
          showIcon
          message="SQL 测试未通过"
          description={result.problems.map((problem) => (
            <div key={`${problem.code}-${problem.subject ?? ''}`}><Tag color="error">{problem.code}</Tag>{problem.message}</div>
          ))}
        />
      )}
      {result.resultFields.length > 0 && (
        <>
          <Typography.Text strong>输出字段</Typography.Text>
          <Table
            size="small"
            pagination={false}
            rowKey="name"
            dataSource={result.resultFields}
            columns={[
              { title: '输出字段', dataIndex: 'name' },
              { title: '平台类型', render: (_, field) => typeDescription(field.typeDefinition) },
              { title: '可空', dataIndex: 'nullable', render: (value: boolean) => value ? '是' : '否' },
            ]}
          />
        </>
      )}
      {result.preview && (
        <>
          <Typography.Text strong>预览数据</Typography.Text>
          <Table
            size="small"
            rowKey={(_, index) => String(index)}
            dataSource={result.preview.resultList}
            columns={previewColumns}
            pagination={false}
            scroll={{ x: 'max-content', y: 280 }}
          />
        </>
      )}
    </Space>
  );
};
