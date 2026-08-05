import { Form, InputNumber, Modal, TreeSelect, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
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
      messageApi.error(error instanceof ApiError ? error.message : '移动节点失败');
    }
  };

  return (
    <>
      {contextHolder}
      <Modal
        title={item ? `移动“${item.name}”` : '移动节点'}
        open={open}
        destroyOnHidden
        okText="移动"
        cancelText="取消"
        confirmLoading={mutation.isPending}
        onCancel={requestClose}
        onOk={() => form.submit()}
      >
        <Form<FormValue>
          autoComplete="off"
          form={form}
          layout="vertical"
          onFieldsChange={() => setDirty(true)}
          onFinish={(value) => void submit(value)}
        >
          <Form.Item label="目标父节点" name="targetParentId" rules={[{ required: true }]}>
            <TreeSelect treeData={treeData} treeDefaultExpandAll showSearch treeNodeFilterProp="title" />
          </Form.Item>
          <Form.Item
            label="目标同级位置"
            name="targetIndex"
            rules={[{ required: true, message: '请输入同级位置' }]}
            extra="从 0 开始，不能超过目标父节点当前子节点数量。"
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};
