import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { CodeOutlined } from '@ant-design/icons';
import { Button, Modal, Space, Typography, message } from 'antd';
import { ApiError } from '../../../shared/api/http';
import { fetchPhysicalTableDdlPlan } from '../api/dataModelApi';
import type { DataModel } from '../model/dataModel';

interface DataModelPublishConfirmationContentProps {
  model: Pick<DataModel, 'id' | 'physicalTableMode'>;
  discardUnsavedFields?: boolean;
}

export const DataModelPublishConfirmationContent = ({
  model,
  discardUnsavedFields = false,
}: DataModelPublishConfirmationContentProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();

  const showDdl = async () => {
    try {
      const plan = await fetchPhysicalTableDdlPlan(model.id);
      if (!plan.supported) {
        messageApi.warning(plan.message);
        return;
      }
      modalApi.info({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '建表 SQL',
        width: 820,
        okText: '关闭',
        content: (
          <div className="physical-table-ddl-dialog">
            <Alert type="warning" showIcon title="SQL 仅供查看，将由系统根据模型字段执行，不能直接编辑。" />
            <Typography.Paragraph
              className="physical-table-ddl-code"
              copyable={{ text: plan.statements.join(';\n') }}
            >
              <pre>{plan.statements.join(';\n')}</pre>
            </Typography.Paragraph>
          </div>
        ),
      });
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '读取建表 SQL 失败');
    }
  };

  return (
    <Space direction="vertical" size={12}>
      {messageContext}
      {modalContext}
      {discardUnsavedFields && (
        <Alert
          type="warning"
          showIcon
          message="当前字段定义有未保存修改"
          description="发布只使用最后保存的字段定义；发布成功后当前修改将被放弃。"
        />
      )}
      {model.physicalTableMode === 'MANAGED' ? (
        <Typography.Text>
          发布前会实时检查物理表；物理表不存在时将根据最后保存的字段自动创建，已存在时必须与模型定义一致。
        </Typography.Text>
      ) : (
        <Typography.Text>
          发布前会实时检查绑定的外部表是否存在且结构一致；平台不会创建或修改外部表。
        </Typography.Text>
      )}
      {model.physicalTableMode === 'MANAGED' && (
        <div>
          <Button size="small" icon={<CodeOutlined />} onClick={() => void showDdl()}>
            查看 DDL
          </Button>
        </div>
      )}
    </Space>
  );
};
