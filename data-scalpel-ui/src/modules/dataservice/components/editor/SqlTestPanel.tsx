import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import { Empty, Table, Tag } from 'antd';
import type { SqlServiceTestResponse } from '../../model/dataService';
import { typeDescription } from '../../model/dataServiceEditor';

interface SqlTestPanelProps {
  result: SqlServiceTestResponse | null;
}

export const SqlTestPanel = ({ result }: SqlTestPanelProps) => {
  if (!result) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请点击顶部“测试 SQL”执行查询" />;

  const previewColumns = result.resultFields.map((field) => ({
    title: (
      <span className="data-service-preview-column-title">
        <span>{field.name}</span>
        <span>{typeDescription(field.typeDefinition)} · {field.nullable ? '可空' : '非空'}</span>
      </span>
    ),
    dataIndex: field.name,
    key: field.name,
    width: 180,
    ellipsis: true,
    render: (value: unknown) => value === null || value === undefined ? '—' : String(value),
  }));
  const previewRows = result.preview?.items ?? [];

  return (
    <div className="data-service-test-result">
      {!result.valid && (
        <Alert
          type="error"
          showIcon
          message="SQL 测试未通过"
          description={result.problems.map((problem) => (
            <div key={`${problem.code}-${problem.subject ?? ''}`}>
              <Tag color="error">{problem.code}</Tag>
              {problem.message}
            </div>
          ))}
        />
      )}
      {result.resultFields.length > 0 && (
        <Table
          className="data-service-preview-table"
          size="small"
          rowKey={(record) => String(previewRows.indexOf(record))}
          dataSource={previewRows}
          columns={previewColumns}
          pagination={false}
          locale={{
            emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="查询成功，暂无预览数据" />,
          }}
          scroll={{ x: 'max-content', y: '100%' }}
        />
      )}
      {result.valid && result.resultFields.length === 0 && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="测试通过，但未返回输出字段" />
      )}
    </div>
  );
};
