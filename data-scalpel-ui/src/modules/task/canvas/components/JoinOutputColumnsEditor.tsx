import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Checkbox, Form, Input, Popconfirm, Space, Tag, Typography } from 'antd';
import type {
  CanvasNodeValidationResult,
  JoinCondition,
  JoinOutputColumn,
} from '../canvasTypes';
import { suggestJoinOutputColumns } from './joinOutputColumns';

type CanvasTable = CanvasNodeValidationResult['inputTables'][number];

export const JoinOutputColumnsEditor = ({
  left,
  right,
  conditions,
  outputColumns,
  leftLabel,
  rightLabel,
  onProgrammaticChange,
}: {
  left: CanvasTable | undefined;
  right: CanvasTable | undefined;
  conditions: JoinCondition[];
  outputColumns: JoinOutputColumn[];
  leftLabel: string;
  rightLabel: string;
  onProgrammaticChange: (columns: JoinOutputColumn[]) => void;
}) => {
  const outputNameCounts = outputColumns.reduce<Map<string, number>>((counts, column) => {
    if (!column.included || !column.outputColumnName) return counts;
    const normalizedName = column.outputColumnName.toLocaleLowerCase();
    counts.set(normalizedName, (counts.get(normalizedName) ?? 0) + 1);
    return counts;
  }, new Map());
  const duplicateOutputNames = new Set(
    [...outputNameCounts.entries()].filter(([, count]) => count > 1).map(([name]) => name),
  );
  const includedOutputCount = outputColumns.filter((column) => column.included).length;

  return <>
    <div style={{ marginTop: 16, marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
      <Space size={6} wrap>
        <Typography.Text strong>输出字段</Typography.Text>
        <Tag>{includedOutputCount} 个输出</Tag>
        {duplicateOutputNames.size > 0 && <Tag color="error">{duplicateOutputNames.size} 个重名待处理</Tag>}
      </Space>
      <Space size={4} wrap>
        <Button
          size="small"
          disabled={outputColumns.length === 0 || conditions.length === 0}
          onClick={() => {
            const rightJoinKeys = new Set(conditions.map((condition) => condition.rightColumnName));
            onProgrammaticChange(outputColumns.map((column) => (
              column.sourceSide === 'RIGHT' && rightJoinKeys.has(column.sourceColumnName)
                ? { ...column, included: false }
                : column
            )));
          }}
        >
          排除右侧 Join Key
        </Button>
        <Popconfirm
          title="重建输出字段建议？"
          description="这会覆盖当前的字段改名、排除和排序。"
          okText="重建"
          cancelText="取消"
          onConfirm={() => {
            if (!left || !right) return;
            onProgrammaticChange(suggestJoinOutputColumns(left, right));
          }}
        >
          <Button size="small" disabled={!left || !right}>重建建议</Button>
        </Popconfirm>
      </Space>
    </div>
    <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
      {leftLabel}字段默认保持原名；{rightLabel}重名字段使用“右表名_字段名”。建议后仍重名时请手动调整。
    </Typography.Paragraph>
    <Form.List name="outputColumns">
      {(fields, { move }) => (
        <Space orientation="vertical" size={4} style={{ width: '100%' }}>
          {fields.map((field, index) => {
            const column = outputColumns[index];
            const sourceTable = column?.sourceSide === 'RIGHT' ? right : left;
            const sourceExists = sourceTable?.columns.some(
              (item) => item.name === column?.sourceColumnName,
            ) ?? false;
            const duplicated = Boolean(
              column?.included
              && duplicateOutputNames.has(column.outputColumnName.toLocaleLowerCase()),
            );
            return (
              <div
                key={field.key}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '30px minmax(150px, 1fr) minmax(150px, 1fr) 58px',
                  gap: 8,
                  alignItems: 'center',
                  padding: '6px 8px',
                  border: `1px solid ${duplicated || !sourceExists ? '#ffccc7' : '#f0f0f0'}`,
                  borderRadius: 6,
                }}
              >
                <Form.Item name={[field.name, 'sourceSide']} hidden><Input /></Form.Item>
                <Form.Item name={[field.name, 'sourceColumnName']} hidden><Input /></Form.Item>
                <Form.Item name={[field.name, 'included']} valuePropName="checked" noStyle>
                  <Checkbox aria-label={`输出字段 ${index + 1}`} />
                </Form.Item>
                <div style={{ minWidth: 0 }}>
                  <Typography.Text ellipsis title={`${sourceTable?.name ?? column?.sourceSide}.${column?.sourceColumnName}`}>
                    <Tag color={column?.sourceSide === 'RIGHT' ? 'purple' : 'blue'}>
                      {column?.sourceSide === 'RIGHT' ? rightLabel : leftLabel}
                    </Tag>
                    {column?.sourceColumnName || '失效字段'}
                  </Typography.Text>
                  {!sourceExists && <div><Typography.Text type="danger">上游字段已失效</Typography.Text></div>}
                </div>
                <Form.Item
                  name={[field.name, 'outputColumnName']}
                  rules={[{ required: true, whitespace: true, message: '请输入输出字段名' }]}
                  style={{ marginBottom: 0 }}
                  validateStatus={duplicated ? 'error' : undefined}
                  help={duplicated ? '输出字段名重复' : undefined}
                >
                  <Input placeholder="输出字段名" disabled={column?.included === false} />
                </Form.Item>
                <Space size={0}>
                  <Button
                    type="text"
                    size="small"
                    icon={<UpOutlined />}
                    disabled={index === 0}
                    aria-label={`上移输出字段 ${index + 1}`}
                    onClick={() => move(index, index - 1)}
                  />
                  <Button
                    type="text"
                    size="small"
                    icon={<DownOutlined />}
                    disabled={index === fields.length - 1}
                    aria-label={`下移输出字段 ${index + 1}`}
                    onClick={() => move(index, index + 1)}
                  />
                </Space>
              </div>
            );
          })}
          {fields.length === 0 && (
            <Alert type="warning" showIcon title="请选择左右表以生成输出字段建议" />
          )}
        </Space>
      )}
    </Form.List>
  </>;
};
