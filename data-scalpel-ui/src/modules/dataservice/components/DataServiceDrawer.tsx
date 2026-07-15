import { Alert, Button, Col, Drawer, Form, Input, Row, Select, Space, TreeSelect, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import { useDataModels } from '../../model';
import { useServiceEngineDataSourceRegistrations } from '../../serviceengine';
import {
  useCreateDataService,
  useUpdateDataService,
} from '../hooks/useDataServices';
import type { DataService, CreateDataServiceRequest, UpdateDataServiceRequest } from '../model/dataService';

interface DataServiceDrawerProps {
  open: boolean;
  dataService: DataService | null;
  canViewDirectories: boolean;
  canViewModels: boolean;
  canViewEngines: boolean;
  onClose: () => void;
}

interface DataServiceFormValues {
  code?: string;
  name: string;
  directoryId?: string;
  modelId: string;
  engineId: string;
  routePath: string;
  description?: string;
}

const routePathPattern = /^\/open-api\/v1\/[a-z0-9][a-z0-9/_-]*$/;

const normalizedOptionalText = (value: string | undefined): string | undefined => value?.trim() || undefined;

export const DataServiceDrawer = ({
  open,
  dataService,
  canViewDirectories,
  canViewModels,
  canViewEngines,
  onClose,
}: DataServiceDrawerProps) => {
  const [form] = Form.useForm<DataServiceFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const editing = Boolean(dataService);
  const directoriesQuery = useDirectoryTree('DATA_SERVICE', open && canViewDirectories);
  const modelsQuery = useDataModels({ search: 'status:"PUBLISHED"', page: 0, size: 500, sort: 'code' }, open && canViewModels);
  const selectedModelId = Form.useWatch('modelId', form);
  const selectedModel = (modelsQuery.data?.content ?? []).find((model) => model.id === selectedModelId);
  const registrationsQuery = useServiceEngineDataSourceRegistrations(
    {
      search: selectedModel ? `dataSourceId:"${selectedModel.storageDataSourceId}"` : undefined,
      page: 0,
      size: 500,
      sort: 'engineId',
    },
    open && canViewEngines && Boolean(selectedModel),
  );
  const createMutation = useCreateDataService();
  const updateMutation = useUpdateDataService();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (dataService) {
      form.setFieldsValue({
        name: dataService.name,
        directoryId: dataService.directoryId ?? undefined,
        modelId: dataService.modelId,
        engineId: dataService.engineId,
        routePath: dataService.routePath,
        description: dataService.description ?? undefined,
      });
      return;
    }
    form.setFieldsValue({ routePath: '/open-api/v1/' });
  }, [dataService, form, open]);

  const close = () => {
    form.resetFields();
    onClose();
  };

  const submit = async (values: DataServiceFormValues) => {
    const request: UpdateDataServiceRequest = {
      name: values.name.trim(),
      directoryId: values.directoryId,
      modelId: values.modelId,
      engineId: values.engineId,
      routePath: values.routePath.trim().toLowerCase(),
      description: normalizedOptionalText(values.description),
    };
    try {
      if (dataService) {
        await updateMutation.mutateAsync({ id: dataService.id, request });
        messageApi.success('数据服务已保存');
      } else {
        await createMutation.mutateAsync({
          ...request,
          code: values.code?.trim().toLowerCase() ?? '',
        } satisfies CreateDataServiceRequest);
        messageApi.success('数据服务已创建');
      }
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存数据服务失败');
    }
  };

  const unavailableDependencies = [
    !canViewModels ? '模型' : undefined,
    !canViewEngines ? 'Service Engine' : undefined,
  ].filter((value): value is string => Boolean(value));

  return (
    <>
      {messageContext}
      <Drawer
        title={editing ? '修改数据服务' : '新建数据服务'}
        open={open}
        size={680}
        onClose={close}
        destroyOnHidden
        footer={(
          <Space>
            <Button onClick={close}>取消</Button>
            <Button
              type="primary"
              loading={createMutation.isPending || updateMutation.isPending}
              disabled={unavailableDependencies.length > 0}
              onClick={() => form.submit()}
            >
              {editing ? '保存' : '创建'}
            </Button>
          </Space>
        )}
      >
        {unavailableDependencies.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message={`缺少${unavailableDependencies.join('、')}查看权限，当前不能维护服务定义。`}
            className="file-dataset-form-alert"
          />
        )}
        <Form<DataServiceFormValues> form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Row gutter={12}>
            {!editing && (
              <Col span={12}>
                <Form.Item
                  label="服务编码"
                  name="code"
                  rules={[
                    { required: true, whitespace: true, message: '请输入服务编码' },
                    { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '以字母开头，仅支持字母、数字和下划线，最长 64 位' },
                  ]}
                >
                  <Input autoFocus placeholder="如：order_query" />
                </Form.Item>
              </Col>
            )}
            <Col span={editing ? 12 : 12}>
              <Form.Item label="服务名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入服务名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}>
                <Input autoFocus={editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="发布模型" name="modelId" rules={[{ required: true, message: '请选择已发布模型' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  loading={modelsQuery.isFetching}
                  placeholder="选择已发布模型"
                  options={(modelsQuery.data?.content ?? []).map((model) => ({
                    value: model.id,
                    label: `${model.name}（${model.code}）`,
                  }))}
                  onChange={(modelId: string) => {
                    if (modelId !== dataService?.modelId) form.setFieldValue('engineId', undefined);
                  }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="Service Engine"
                name="engineId"
                extra={selectedModel ? '仅显示已注册该模型存储数据源的 Engine；待同步的 Engine 可以保存草稿，但不能发布。' : '请先选择发布模型。'}
                rules={[{ required: true, message: '请选择 Service Engine' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  disabled={!selectedModel}
                  loading={registrationsQuery.isFetching}
                  placeholder={selectedModel ? '选择已注册的 Engine' : '请先选择发布模型'}
                  options={(registrationsQuery.data?.content ?? []).map((registration) => ({
                    value: registration.engineId,
                    label: `${registration.engineName}（${registration.engineCode}） · ${registration.status === 'READY' ? '已就绪' : '待同步'}`,
                  }))}
                />
              </Form.Item>
            </Col>
            {selectedModel && !registrationsQuery.isFetching && (registrationsQuery.data?.content.length ?? 0) === 0 && (
              <Col span={24}>
                <Alert
                  type="warning"
                  showIcon
                  message="该模型的存储数据源尚未注册到任何 Service Engine。请先在“服务引擎”模块完成注册并同步。"
                  className="file-dataset-form-alert"
                />
              </Col>
            )}
            <Col span={24}>
              <Form.Item
                label="公开路由"
                name="routePath"
                extra="第一版只允许 /open-api/v1/ 下的小写静态 POST 路径。"
                rules={[
                  { required: true, whitespace: true, message: '请输入公开路由' },
                  {
                    validator: async (_, value: string | undefined) => {
                      const normalized = value?.trim().toLowerCase() ?? '';
                      if (!routePathPattern.test(normalized) || normalized.endsWith('/') || normalized.includes('//')) {
                        throw new Error('路由必须是 /open-api/v1/ 下的小写静态路径，且不能以 / 结尾');
                      }
                    },
                  },
                ]}
              >
                <Input placeholder="如：/open-api/v1/orders" />
              </Form.Item>
            </Col>
            {canViewDirectories && (
              <Col span={24}>
                <Form.Item label="所属目录" name="directoryId">
                  <TreeSelect
                    allowClear
                    treeDefaultExpandAll
                    loading={directoriesQuery.isFetching}
                    treeData={directoryTreeSelectData(directoriesQuery.data ?? [])}
                    placeholder="未分类"
                  />
                </Form.Item>
              </Col>
            )}
            <Col span={24}>
              <Form.Item label="说明" name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}>
                <Input.TextArea rows={4} maxLength={1000} showCount />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
};
