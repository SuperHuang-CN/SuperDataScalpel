import { Alert, Button, Divider, Drawer, Form, Input, Select, Space, message } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useServiceEngines } from '../../serviceengine';
import { useCreateDataService } from '../hooks/useDataServices';
import {
  dataServiceTypeLabels,
  type DataServiceDetail,
  type DataServiceType,
} from '../model/dataService';
import {
  buildDataServiceCreateRequest,
  initialDataServiceFormValues,
  type DataServiceFormValues,
} from '../model/dataServiceEditor';
import { CommonServiceFields } from './editor/CommonServiceFields';

interface DataServiceCreateDrawerProps {
  open: boolean;
  type: DataServiceType | null;
  initialDirectoryId?: string;
  canViewDirectories: boolean;
  canViewEngines: boolean;
  onClose: () => void;
  onCreated: (dataService: DataServiceDetail) => void;
}

const problemMessage = (error: unknown) => (
  error instanceof ApiError ? error.problem?.detail ?? error.message : '创建数据服务失败'
);

export const DataServiceCreateDrawer = ({
  open,
  type,
  initialDirectoryId,
  canViewDirectories,
  canViewEngines,
  onClose,
  onCreated,
}: DataServiceCreateDrawerProps) => {
  const [form] = Form.useForm<DataServiceFormValues>();
  const [operationError, setOperationError] = useState<string | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const createMutation = useCreateDataService();
  const enginesQuery = useServiceEngines(
    { page: 0, size: 500, sort: 'code' },
    open && canViewEngines,
  );

  useEffect(() => {
    if (!open || !type) return;
    form.resetFields();
    form.setFieldsValue(initialDataServiceFormValues(type, initialDirectoryId));
  }, [form, initialDirectoryId, open, type]);

  const closeDrawer = () => {
    if (createMutation.isPending) return;
    setOperationError(null);
    onClose();
  };

  const submit = async (values: DataServiceFormValues) => {
    setOperationError(null);
    try {
      const created = await createMutation.mutateAsync(buildDataServiceCreateRequest(values));
      onCreated(created);
    } catch (error) {
      const text = problemMessage(error);
      setOperationError(text);
      messageApi.error(text);
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        title={type ? `新建${dataServiceTypeLabels[type]}服务` : '新建数据服务'}
        open={open}
        size="large"
        onClose={closeDrawer}
        closable={!createMutation.isPending}
        maskClosable={!createMutation.isPending}
        destroyOnHidden
        footer={(
          <Space>
            <Button disabled={createMutation.isPending} onClick={closeDrawer}>取消</Button>
            <Button
              type="primary"
              loading={createMutation.isPending}
              disabled={!canViewEngines}
              onClick={() => form.submit()}
            >
              创建服务
            </Button>
          </Space>
        )}
      >
        <Alert
          type="info"
          showIcon
          title="创建后服务将保存为草稿，服务定义可以稍后从列表或详情页配置。"
        />
        {!canViewEngines && (
          <Alert
            type="warning"
            showIcon
            title="缺少 Service Engine 查看权限，无法创建数据服务。"
          />
        )}
        {operationError && <Alert type="error" showIcon title="创建失败" description={operationError} />}
        <Form<DataServiceFormValues>
          autoComplete="off"
          form={form}
          layout="vertical"
          onFinish={(values) => void submit(values)}
        >
          <Form.Item name="type" hidden><Input /></Form.Item>
          <Divider orientation="left" plain>基本信息</Divider>
          <CommonServiceFields
            creating
            readOnly={false}
            canViewDirectories={canViewDirectories}
            section="identity"
            includeDescription
          />
          <Divider orientation="left" plain>运行与访问</Divider>
          <Form.Item<DataServiceFormValues>
            label="所属 Service Engine"
            name="engineId"
            rules={[{ required: true, message: '请选择 Service Engine' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!canViewEngines}
              loading={enginesQuery.isFetching}
              options={(enginesQuery.data?.content ?? []).map((engine) => ({
                value: engine.id,
                label: `${engine.name}（${engine.code}）${engine.enabled ? '' : ' · 已停用'}`,
                disabled: !engine.enabled,
              }))}
              placeholder="选择启用的 Service Engine"
            />
          </Form.Item>
          <CommonServiceFields
            creating
            readOnly={false}
            canViewDirectories={false}
            section="routing"
            includeDescription={false}
          />
        </Form>
      </Drawer>
    </>
  );
};
