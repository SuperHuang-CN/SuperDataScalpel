import { Form } from 'antd';
import type { DataServiceFormValues } from '../../model/dataServiceEditor';
import { StandardModelPicker } from './StandardModelPicker';

interface StandardServiceEditorProps {
  form: ReturnType<typeof Form.useForm<DataServiceFormValues>>[0];
  serviceId: string;
  engineId: string;
  readOnly: boolean;
  canViewModels: boolean;
  canViewEngines: boolean;
}

export const StandardServiceEditor = ({
  form,
  serviceId,
  engineId,
  readOnly,
  canViewModels,
  canViewEngines,
}: StandardServiceEditorProps) => {
  const selectedModelId = Form.useWatch('modelId', form);

  const selectModel = (modelId: string) => {
    if (form.getFieldValue('modelId') === modelId) return;
    form.setFieldValue('modelId', modelId);
    form.validateFields(['modelId']).catch(() => undefined);
  };

  return (
    <div className="data-service-standard-definition-editor data-service-management-scope">
      <Form.Item<DataServiceFormValues>
        name="modelId"
        hidden
        rules={[{ required: true, message: '请选择发布模型' }]}
      ><input /></Form.Item>
      <StandardModelPicker
        serviceId={serviceId}
        engineId={engineId}
        value={selectedModelId}
        readOnly={readOnly}
        canViewModels={canViewModels}
        canViewEngines={canViewEngines}
        onChange={selectModel}
      />
    </div>
  );
};
