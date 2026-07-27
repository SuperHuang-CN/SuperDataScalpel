import { AutoComplete, Button, Drawer, Form, Input, Select, Space, Typography } from 'antd';
import { useEffect } from 'react';
import type { TaskSchedule, TaskScheduleRequest } from '../model/task';
import { isValidIanaZoneId, quartzCronValidationMessage } from '../model/taskScheduleValidation';

interface TaskScheduleDrawerProps {
  open: boolean;
  schedule: TaskSchedule | null;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (request: TaskScheduleRequest) => Promise<void>;
}

const DEFAULT_VALUES: TaskScheduleRequest = {
  name: '',
  cronExpression: '0 0 2 * * ?',
  zoneId: 'Asia/Shanghai',
  misfirePolicy: 'FIRE_ONCE_NOW',
  overlapPolicy: 'FORBID',
};

const TIME_ZONE_OPTIONS = [
  'Asia/Shanghai',
  'Asia/Hong_Kong',
  'Asia/Tokyo',
  'Asia/Singapore',
  'UTC',
  'Europe/London',
  'America/New_York',
].map((value) => ({ value }));

export const TaskScheduleDrawer = ({
  open,
  schedule,
  submitting,
  onClose,
  onSubmit,
}: TaskScheduleDrawerProps) => {
  const [form] = Form.useForm<TaskScheduleRequest>();

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(schedule ? {
      name: schedule.name,
      cronExpression: schedule.cronExpression,
      zoneId: schedule.zoneId,
      misfirePolicy: schedule.misfirePolicy,
      overlapPolicy: schedule.overlapPolicy,
    } : DEFAULT_VALUES);
  }, [form, open, schedule]);

  const submit = async () => {
    let values: TaskScheduleRequest;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    await onSubmit({
      ...values,
      name: values.name.trim(),
      cronExpression: values.cronExpression.trim(),
      zoneId: values.zoneId.trim(),
    });
  };

  return (
    <Drawer
      title={schedule ? `编辑计划：${schedule.name}` : '新建定时计划'}
      open={open}
      size={520}
      onClose={onClose}
      destroyOnHidden
      footer={(
        <div style={{ textAlign: 'right' }}>
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" loading={submitting} onClick={() => void submit()}>保存</Button>
          </Space>
        </div>
      )}
    >
      <Form<TaskScheduleRequest> form={form} layout="vertical" requiredMark="optional">
        <Form.Item name="name" label="计划名称" rules={[
          { required: true, whitespace: true, message: '请输入计划名称' },
          { max: 100, message: '计划名称不能超过 100 个字符' },
        ]}>
          <Input placeholder="例如：每日凌晨同步" />
        </Form.Item>
        <Form.Item
          name="cronExpression"
          label="Quartz Cron 表达式"
          extra={<Typography.Text type="secondary">示例：<code>0 0 2 * * ?</code>，表示每天 02:00 执行。</Typography.Text>}
          rules={[
            { required: true, whitespace: true, message: '请输入 Quartz Cron 表达式' },
            { validator: (_, value: string) => {
              const validationMessage = quartzCronValidationMessage(value ?? '');
              return validationMessage ? Promise.reject(new Error(validationMessage)) : Promise.resolve();
            } },
          ]}
        >
          <Input placeholder="0 0 2 * * ?" />
        </Form.Item>
        <Form.Item
          name="zoneId"
          label="IANA 时区"
          extra="可直接输入其他 IANA Zone ID。"
          rules={[
            { required: true, whitespace: true, message: '请输入时区' },
            { max: 64, message: '时区不能超过 64 个字符' },
            { validator: (_, value: string) => (
              isValidIanaZoneId(value ?? '')
                ? Promise.resolve()
                : Promise.reject(new Error('请输入有效的 IANA 时区'))
            ) },
          ]}
        >
          <AutoComplete options={TIME_ZONE_OPTIONS} placeholder="Asia/Shanghai" />
        </Form.Item>
        <Form.Item name="misfirePolicy" label="错过触发策略" rules={[{ required: true }]}>
          <Select options={[
            { value: 'FIRE_ONCE_NOW', label: '恢复后立即补触发一次' },
            { value: 'SKIP', label: '跳过停机期间错过的批次' },
          ]} />
        </Form.Item>
        <Form.Item name="overlapPolicy" label="重叠策略" rules={[{ required: true }]}>
          <Select options={[
            { value: 'FORBID', label: '禁止重叠：有活动实例时记录为已跳过' },
            { value: 'ALLOW', label: '允许重叠：仍创建新的运行实例' },
          ]} />
        </Form.Item>
      </Form>
    </Drawer>
  );
};
