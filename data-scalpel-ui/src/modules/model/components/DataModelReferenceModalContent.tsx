import { Alert, Button, Empty, Space, Table, Tag, Typography } from 'antd';
import type { DataModelReferences } from '../model/dataModel';

interface DataModelReferenceModalContentProps {
  references: DataModelReferences;
}

const open = (path: string) => window.open(path, '_blank', 'noopener,noreferrer');

export const DataModelReferenceModalContent = ({ references }: DataModelReferenceModalContentProps) => {
  if (references.deletable) return <Empty description="没有发现任务或数据服务引用" />;
  return (
    <Space orientation="vertical" size={16} className="model-reference-modal-content">
      <Alert
        type="warning"
        showIcon
        message="当前模型不能删除"
        description="请先删除或修改下列引用对象。草稿、已发布和已停用对象都会阻止删除。"
      />
      {references.tasks.length > 0 && (
        <Table
          size="small"
          rowKey={(item) => `${item.id}-${item.referenceType}-${item.nodeId ?? item.nodeName ?? ''}`}
          pagination={false}
          title={() => `任务引用（${references.tasks.length}）`}
          dataSource={references.tasks}
          columns={[
            {
              title: '任务', dataIndex: 'name',
              render: (name: string, item) => (
                <Button type="link" size="small" onClick={() => open(`/task/${item.id}`)}>{name}</Button>
              ),
            },
            { title: '状态', dataIndex: 'status', width: 90, render: (status) => <Tag>{status}</Tag> },
            { title: '角色', dataIndex: 'role', width: 80, render: (role) => role === 'INPUT' ? '输入' : '输出' },
            {
              title: '位置', width: 190,
              render: (_, item) => item.referenceType === 'MODEL_QUALITY_TARGET'
                ? '质检目标模型'
                : item.referenceType === 'SPARK_JAR_RESOURCE_BINDING'
                  ? 'Spark JAR 资源绑定'
                : item.referenceType === 'CURRENT_LINEAGE'
                  ? `当前血缘${item.nodeName ? ` · ${item.nodeName}` : ''}`
                  : item.nodeName ?? item.referenceType,
            },
          ]}
        />
      )}
      {references.services.length > 0 && (
        <Table
          size="small"
          rowKey={(item) => `${item.id}-${item.role}-${item.ordinal ?? ''}`}
          pagination={false}
          title={() => `数据服务引用（${references.services.length}）`}
          dataSource={references.services}
          columns={[
            {
              title: '数据服务', dataIndex: 'name',
              render: (name: string, item) => (
                <Button type="link" size="small" onClick={() => open(`/dataservice/${item.id}`)}>{name}</Button>
              ),
            },
            { title: '状态', dataIndex: 'status', width: 90, render: (status) => <Tag>{status}</Tag> },
            { title: '角色', dataIndex: 'role', width: 100, render: (role) => role === 'PRIMARY' ? '主模型' : '引用模型' },
          ]}
        />
      )}
      <Typography.Text type="secondary">对象名称会在新窗口打开，处理引用后请重新执行删除。</Typography.Text>
    </Space>
  );
};
