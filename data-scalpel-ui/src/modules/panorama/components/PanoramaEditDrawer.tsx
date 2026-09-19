import { useRef } from 'react';
import { CameraOutlined } from '@ant-design/icons';
import { Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, TreeSelect, message } from 'antd';
import { ApiError } from '../../../shared/api/http';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import { useCurrentUser } from '../../system';
import { usePanoramaCommand } from '../hooks/usePanoramas';
import type { Panorama, UpdatePanorama } from '../model/panorama';
export const PanoramaEditDrawer = ({ panorama, onClose }: { panorama: Panorama; onClose: () => void }) => {
  const expectedVersion = useRef(panorama.contentVersion).current;
  const [form] = Form.useForm<UpdatePanorama>(); const command = usePanoramaCommand(); const [messageApi, context] = message.useMessage();
  const user = useCurrentUser(); const directories = useDirectoryTree('PANORAMA', user.data?.permissions.includes('directory.view') ?? false);
  const timeMode = Form.useWatch('timeMode', form) ?? panorama.timeMode;
  const locationMode = Form.useWatch('locationMode', form) ?? panorama.locationMode;
  const submit = async (values: UpdatePanorama) => {
    try {
      await command.mutateAsync({ id: panorama.id, action: 'update', body: { ...values, expectedContentVersion: expectedVersion,
        directoryId: values.directoryId ?? null, captureTime: values.captureTime || null, captureOffset: values.captureOffset || null,
        latitude: values.latitude ?? null, longitude: values.longitude ?? null } });
      messageApi.success('全景资料已保存'); onClose();
    } catch (e) { messageApi.error(e instanceof ApiError ? e.message : '保存失败'); }
  };
  return <Drawer rootClassName="business-overlay business-drawer-overlay" open size={680} onClose={onClose}
    title={<Space><CameraOutlined />修改全景资料</Space>} footer={<div className="panorama-drawer-footer"><span>{panorama.name}</span><Space><Button onClick={onClose}>取消</Button><Button type="primary" loading={command.isPending} onClick={() => form.submit()}>保存</Button></Space></div>}>
    {context}<Form form={form} layout="vertical" autoComplete="off" initialValues={{ ...panorama, expectedContentVersion: panorama.contentVersion }} onFinish={values => void submit(values)}>
      <Row gutter={16}><Col span={12}><Form.Item name="name" label="名称" rules={[{ required: true, whitespace: true }, { max: 255 }]}><Input maxLength={255} /></Form.Item></Col><Col span={12}>
        <Form.Item name="directoryId" label="目录"><TreeSelect allowClear disabled={!user.data?.permissions.includes('directory.view')} treeData={directoryTreeSelectData(directories.data ?? [])} placeholder="未分类" /></Form.Item></Col></Row>
      <Form.Item name="description" label="描述"><Input.TextArea rows={3} maxLength={10000} /></Form.Item>
      <Form.Item name="timeMode" label="拍摄时间来源"><Select options={[{ value: 'AUTO', label: '使用文件提取值' }, { value: 'MANUAL', label: '人工修订（留空表示清空）' }]} /></Form.Item>
      {timeMode === 'MANUAL' && <Row gutter={16}><Col span={16}><Form.Item name="captureTime" label="照片本地时间"><Input type="datetime-local" step={1} /></Form.Item></Col><Col span={8}><Form.Item name="captureOffset" label="时区偏移（可选）"><Input placeholder="+08:00" maxLength={10} /></Form.Item></Col></Row>}
      <Form.Item name="locationMode" label="位置来源"><Select options={[{ value: 'AUTO', label: '使用文件提取值' }, { value: 'MANUAL', label: '人工修订 WGS84（留空表示清空）' }]} /></Form.Item>
      {locationMode === 'MANUAL' && <Row gutter={16}><Col span={12}><Form.Item name="longitude" label="经度"><InputNumber min={-180} max={180} style={{ width: '100%' }} /></Form.Item></Col><Col span={12}><Form.Item name="latitude" label="纬度"><InputNumber min={-90} max={90} style={{ width: '100%' }} /></Form.Item></Col></Row>}
    </Form>
  </Drawer>;
};
