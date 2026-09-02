import { CalendarOutlined, ClockCircleOutlined, ControlOutlined } from '@ant-design/icons';
import { AutoComplete, Badge, Button, Col, Drawer, Form, Input, Row, Select, Space, Tag, Typography } from 'antd';
import { useEffect } from 'react';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import {
  taskScheduleStatusLabels,
  type TaskSchedule,
  type TaskScheduleRequest,
} from '../model/task';
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
      rootClassName="business-overlay business-drawer-overlay"
      className="data-model-drawer task-schedule-drawer"
      title={(
        <div className="data-model-drawer-title">
          <span className="data-model-drawer-title-icon" aria-hidden="true"><CalendarOutlined /></span>
          <span className="data-model-drawer-title-copy">
            <span>{schedule ? '修改定时计划' : '新建定时计划'}</span>
            <Typography.Text type="secondary">配置触发时间、补偿策略与并发边界</Typography.Text>
          </span>
        </div>
      )}
      extra={<Tag className="data-model-drawer-header-tag">{schedule ? taskScheduleStatusLabels[schedule.status] : '新计划'}</Tag>}
      open={open}
      size={720}
      onClose={onClose}
      destroyOnHidden
      footer={(
        <div className="data-model-drawer-footer">
          <Badge
            status={schedule?.status === 'ENABLED' ? 'success' : 'default'}
            text={schedule ? `${taskScheduleStatusLabels[schedule.status]} · 保存后保持当前状态` : '创建后默认处于停用状态'}
          />
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" loading={submitting} onClick={() => void submit()}>{schedule ? '保存修改' : '创建计划'}</Button>
          </Space>
        </div>
      )}
    >
      <Form<TaskScheduleRequest>
        name="task-schedule-editor-form"
        className="data-model-form task-schedule-form"
        autoComplete="off"
        form={form}
        layout="vertical"
      >
        <section className="data-model-form-section">
          <header className="data-model-form-section-header">
            <span className="data-model-form-section-icon" aria-hidden="true"><ClockCircleOutlined /></span>
            <span className="data-model-form-section-copy">
              <span className="data-model-form-section-title">计划信息</span>
              <Typography.Text type="secondary">定义计划名称、Cron 时间表达式与执行时区</Typography.Text>
            </span>
          </header>
          <div className="data-model-form-section-body">
            <Row gutter={14}>
              <Col xs={24} sm={12}>
                <Form.Item name="name" label="计划名称" rules={[
                  { required: true, whitespace: true, message: '请输入计划名称' },
                  { max: 100, message: '计划名称不能超过 100 个字符' },
                ]}>
                  <Input name="task-schedule-name" autoComplete="off" placeholder="例如：每日凌晨同步" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <div className="data-model-form-help-field">
                  <div className="data-model-form-external-label">
                    <label htmlFor="task-schedule-editor-form_zoneId"><span aria-hidden="true">*</span>IANA 时区</label>
                    <ContextHelp
                      ariaLabel="查看 IANA 时区说明"
                      content="可直接输入标准 IANA Zone ID；夏令时切换由对应时区规则自动处理。"
                    />
                  </div>
                  <Form.Item
                    name="zoneId"
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
                    <AutoComplete options={TIME_ZONE_OPTIONS}>
                      <Input
                        aria-label="IANA 时区"
                        name="task-schedule-zone-id"
                        autoComplete="off"
                        placeholder="Asia/Shanghai"
                      />
                    </AutoComplete>
                  </Form.Item>
                </div>
              </Col>
              <Col span={24}>
                <div className="data-model-form-help-field">
                  <div className="data-model-form-external-label">
                    <label htmlFor="task-schedule-editor-form_cronExpression"><span aria-hidden="true">*</span>Quartz Cron 表达式</label>
                    <ContextHelp
                      ariaLabel="查看 Quartz Cron 说明"
                      content={<>使用 6 或 7 个字段的 Quartz Cron 语法。例如 <code>0 0 2 * * ?</code> 表示每天 02:00 执行。</>}
                    />
                  </div>
                  <Form.Item
                    name="cronExpression"
                    rules={[
                      { required: true, whitespace: true, message: '请输入 Quartz Cron 表达式' },
                      { validator: (_, value: string) => {
                        const validationMessage = quartzCronValidationMessage(value ?? '');
                        return validationMessage ? Promise.reject(new Error(validationMessage)) : Promise.resolve();
                      } },
                    ]}
                  >
                    <Input aria-label="Quartz Cron 表达式" className="task-schedule-code-input" name="task-schedule-cron-expression" autoComplete="off" placeholder="0 0 2 * * ?" />
                  </Form.Item>
                </div>
              </Col>
            </Row>
          </div>
        </section>

        <section className="data-model-form-section">
          <header className="data-model-form-section-header">
            <span className="data-model-form-section-icon" aria-hidden="true"><ControlOutlined /></span>
            <span className="data-model-form-section-copy">
              <span className="data-model-form-section-title">触发与并发策略</span>
              <Typography.Text type="secondary">控制停机补偿以及前次运行未结束时的处理方式</Typography.Text>
            </span>
          </header>
          <div className="data-model-form-section-body">
            <Row gutter={14}>
              <Col xs={24} sm={12}>
                <Form.Item name="misfirePolicy" label="错过触发策略" rules={[{ required: true }]}>
                  <Select options={[
                    { value: 'FIRE_ONCE_NOW', label: '恢复后立即补触发一次' },
                    { value: 'SKIP', label: '跳过停机期间错过的批次' },
                  ]} />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item name="overlapPolicy" label="重叠策略" rules={[{ required: true }]}>
                  <Select options={[
                    { value: 'FORBID', label: '禁止重叠：有活动实例时记录为已跳过' },
                    { value: 'ALLOW', label: '允许重叠：仍创建新的运行实例' },
                  ]} />
                </Form.Item>
              </Col>
            </Row>
          </div>
        </section>
      </Form>
    </Drawer>
  );
};
