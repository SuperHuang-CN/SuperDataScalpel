import { NodeIndexOutlined, SwapOutlined } from '@ant-design/icons';
import { Button, Form, InputNumber, Modal, Space, Tag, TreeSelect, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useMoveStandardDictionaryItem } from '../hooks/useStandardDictionaries';
import { useStandardFormLeaveGuard } from '../hooks/useStandardFormLeaveGuard';
import type {
  StandardDictionary,
  StandardDictionaryTreeNode,
} from '../model/standardDictionary';

interface Props {
  open: boolean;
  dictionary: StandardDictionary;
  item: StandardDictionaryTreeNode | null;
  tree: StandardDictionaryTreeNode[];
  onClose: () => void;
}

interface FormValue {
  targetParentId: string;
  targetIndex: number;
}

interface TreeOption {
  value: string;
  title: string;
  children?: TreeOption[];
}

const ROOT_VALUE = '__ROOT__';

const options = (
  nodes: StandardDictionaryTreeNode[],
  excludedId: string | undefined,
): TreeOption[] => nodes
  .filter((node) => node.id !== excludedId)
  .map((node) => ({
    value: node.id,
    title: `${node.code} · ${node.name}`,
    children: options(node.children, excludedId),
  }));

export const MoveStandardDictionaryItemModal = ({
  open,
  dictionary,
  item,
  tree,
  onClose,
}: Props) => {
  const [form] = Form.useForm<FormValue>();
  const [messageApi, contextHolder] = message.useMessage();
  const [dirty, setDirty] = useState(false);
  const [operationError, setOperationError] = useState<string | null>(null);
  const mutation = useMoveStandardDictionaryItem();
  const treeData = useMemo<TreeOption[]>(() => [
    { value: ROOT_VALUE, title: '根节点' },
    ...options(tree, item?.id),
  ], [item?.id, tree]);

  useEffect(() => {
    if (!open || !item) return;
    form.resetFields();
    form.setFieldsValue({
      targetParentId: item.parentId ?? ROOT_VALUE,
      targetIndex: item.sortOrder,
    });
  }, [form, item, open]);
  const discard = useCallback(() => {
    setDirty(false);
    setOperationError(null);
    form.resetFields();
    onClose();
  }, [form, onClose]);
  const requestClose = useStandardFormLeaveGuard({
    dirty: open && dirty,
    content: '码表节点的新位置尚未保存。',
    onDiscard: discard,
  });

  const submit = async (value: FormValue) => {
    if (!item) return;
    setOperationError(null);
    try {
      await mutation.mutateAsync({
        dictionaryId: dictionary.id,
        itemId: item.id,
        request: {
          expectedVersion: dictionary.version,
          ...(value.targetParentId === ROOT_VALUE ? {} : { targetParentId: value.targetParentId }),
          targetIndex: value.targetIndex,
        },
      });
      messageApi.success('节点位置已调整');
      setDirty(false);
      onClose();
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : '移动节点失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  return (
    <>
      {contextHolder}
      <Modal
        rootClassName="business-overlay business-modal-overlay standard-dictionary-move-modal"
        title={(
          <div className="standard-dictionary-move-title">
            <span className="standard-dictionary-move-title-icon" aria-hidden="true"><SwapOutlined /></span>
            <span className="standard-dictionary-move-title-copy">
              <span>调整码表节点位置</span>
              <Typography.Text type="secondary">选择新的父节点和同级排序位置</Typography.Text>
            </span>
          </div>
        )}
        open={open}
        destroyOnHidden
        closable={!mutation.isPending}
        maskClosable={!mutation.isPending}
        onCancel={requestClose}
        footer={(
          <div className="standard-dictionary-move-footer">
            {operationError ? (
              <InlineFeedback tone="error" label="节点移动失败" detail={operationError} />
            ) : (
              <InlineFeedback tone="info" label={item ? `移动 ${item.name} · ${item.code}` : '等待选择节点'} />
            )}
            <Space>
              <Button disabled={mutation.isPending} onClick={requestClose}>取消</Button>
              <Button type="primary" loading={mutation.isPending} onClick={() => form.submit()}>确认移动</Button>
            </Space>
          </div>
        )}
      >
        <div className="standard-dictionary-move-context">
          <span className="standard-dictionary-move-context-icon" aria-hidden="true"><NodeIndexOutlined /></span>
          <span>
            <strong>{item?.name ?? '—'}</strong>
            <Typography.Text type="secondary">{item?.code ?? '—'}</Typography.Text>
          </span>
          <Tag>{dictionary.code}</Tag>
        </div>
        <Form<FormValue>
          autoComplete="off"
          form={form}
          layout="vertical"
          className="standard-dictionary-move-form"
          onFieldsChange={() => setDirty(true)}
          onFinish={(value) => void submit(value)}
        >
          <Form.Item label="目标父节点" name="targetParentId" rules={[{ required: true }]}>
            <TreeSelect treeData={treeData} treeDefaultExpandAll showSearch treeNodeFilterProp="title" />
          </Form.Item>
          <Form.Item
            label={(
              <span className="standard-dictionary-move-field-label">
                目标同级位置
                <ContextHelp ariaLabel="查看同级位置规则" content="从 0 开始，不能超过目标父节点当前的子节点数量。" />
              </span>
            )}
            name="targetIndex"
            rules={[{ required: true, message: '请输入同级位置' }]}
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};
