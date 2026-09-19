import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { DownloadOutlined } from '@ant-design/icons';
import { Button, Drawer, Empty, Space, Spin, Table, Tag, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import {
  useDownloadQualityFailureSamples,
  useQualityFailureSamples,
} from '../hooks/useTasks';
interface QualityFailureSampleDrawerProps {
  open: boolean;
  runId: string | null;
  ruleId: string | null;
  ruleName: string | null;
  onClose: () => void;
}

const displayValue = (value: unknown) => {
  if (value === null || value === undefined) return <Typography.Text type="secondary">NULL</Typography.Text>;
  if (typeof value === 'boolean') return <Tag>{value ? 'true' : 'false'}</Tag>;
  return <Typography.Text ellipsis={{ tooltip: String(value) }}>{String(value)}</Typography.Text>;
};

const safeDownloadName = (value: string) => {
  const safe = value.replace(/[\r\n\t/\\";]/g, '_').trim().slice(0, 80);
  return `${safe || 'quality-rule'}-失败样本.parquet`;
};

export const QualityFailureSampleDrawer = ({
  open,
  runId,
  ruleId,
  ruleName,
  onClose,
}: QualityFailureSampleDrawerProps) => {
  const [messageApi, contextHolder] = message.useMessage();
  const query = useQualityFailureSamples(runId ?? undefined, ruleId ?? undefined, open);
  const downloadMutation = useDownloadQualityFailureSamples();
  const data = query.data;
  const columns: TableProps<Record<string, unknown>>['columns'] = data?.columns.map((column) => ({
    title: (
      <Space size={4}>
        <span>{column.name}</span>
        {column.primaryKey && <Tag color="blue">主键</Tag>}
        {column.diagnostic && <Tag>诊断</Tag>}
      </Space>
    ),
    dataIndex: column.code,
    key: column.code,
    width: 180,
    ellipsis: true,
    render: displayValue,
  })) ?? [];

  const download = async () => {
    if (!runId || !ruleId) return;
    try {
      const blob = await downloadMutation.mutateAsync({ runId, ruleId });
      downloadBlob(blob, safeDownloadName(ruleName || ruleId));
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载失败样本失败');
    }
  };

  return (
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      open={open}
      size="large"
      destroyOnHidden
      title={ruleName ? `失败样本：${ruleName}` : '失败样本'}
      onClose={onClose}
      extra={(
        <Button
          icon={<DownloadOutlined />}
          loading={downloadMutation.isPending}
          disabled={!data}
          onClick={() => void download()}
        >
          下载 Parquet
        </Button>
      )}
    >
      {contextHolder}
      {query.isPending && <Spin tip="正在解析失败样本…" />}
      {query.isError && (
        <Alert
          type="error"
          showIcon
          message="失败样本加载失败"
          description={query.error instanceof ApiError ? query.error.message : '请稍后重试。'}
          action={<Button size="small" onClick={() => void query.refetch()}>重试</Button>}
        />
      )}
      {data && (
        <Space orientation="vertical" size={12} style={{ width: '100%' }}>
          <Alert
            type="warning"
            showIcon
            message="样本可能包含业务数据，请按数据安全要求使用和下载。"
            description={(
              <Space orientation="vertical" size={2}>
                <span>
                  当前展示 {data.sampledRows} 条，异常总数 {data.violationRows} 条；
                  {data.truncated ? '这是截断样本，不代表全部异常。' : '本次样本包含全部异常记录。'}
                </span>
                {!data.rowLocatable && <span>目标模型没有可安全展示的完整主键，样本不能唯一定位源记录。</span>}
              </Space>
            )}
          />
          {data.rows.length === 0 ? <Empty description="没有样本记录" /> : (
            <Table<Record<string, unknown>>
              size="small"
              bordered
              columns={columns}
              dataSource={data.rows}
              rowKey={(row) => String(data.rows.indexOf(row))}
              pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
              scroll={{ x: Math.max(720, data.columns.length * 180), y: 440 }}
            />
          )}
        </Space>
      )}
    </Drawer>
  );
};
