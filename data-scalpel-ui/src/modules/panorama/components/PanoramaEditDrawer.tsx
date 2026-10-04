import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { useRef } from 'react';
import { CameraOutlined, FolderOutlined } from '@ant-design/icons';
import { Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, TreeSelect, message } from 'antd';
import { ApiError } from '../../../shared/api/http';
import { directoryTreeSelectOptions, useDirectoryTree } from '../../directory';
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
  return <Drawer closable={{ placement: 'end' }} rootClassName="business-overlay business-drawer-overlay workspace-resource-overlay resource-workspace-overlay" open size="min(680px, 100vw)" onClose={onClose}
    title={<OverlayTitle icon={<CameraOutlined />} title="修改全景资料" description="更新全景影像的业务信息" />} footer={<div className="panorama-drawer-footer"><span>{panorama.name}</span><Space><Button onClick={onClose}>取消</Button><Button type="primary" loading={command.isPending} onClick={() => form.submit()}>保存</Button></Space></div>}>
    {context}<Form form={form} layout="vertical" autoComplete="off" initialValues={{ ...panorama, expectedContentVersion: panorama.contentVersion }} onFinish={values => void submit(values)}>
      <h3 className="resource-form-section-title">基本资料</h3>
      <Row gutter={16}><Col xs={24} sm={12}><Form.Item name="name" label="名称" rules={[{ required: true, whitespace: true }, { max: 255 }]}><Input maxLength={255} /></Form.Item></Col><Col xs={24} sm={12}>
        <Form.Item name="directoryId" label="目录"><TreeSelect treeIcon prefix={<FolderOutlined />} classNames={{ popup: { root: 'workspace-resource-select' } }} allowClear disabled={!user.data?.permissions.includes('directory.view')} treeData={directoryTreeSelectOptions(directories.data ?? [])} placeholder="未分类" /></Form.Item></Col></Row>
      <Form.Item name="description" label="描述"><Input.TextArea rows={3} maxLength={10000} /></Form.Item>
      <h3 className="resource-form-section-title">拍摄时间与位置</h3>
      <Form.Item name="timeMode" label="拍摄时间来源"><Select options={[{ value: 'AUTO', label: '使用文件提取值' }, { value: 'MANUAL', label: '人工修订（留空表示清空）' }]} /></Form.Item>
      {timeMode === 'MANUAL' && <Row gutter={16}><Col xs={24} sm={16}><Form.Item name="captureTime" label="照片本地时间"><Input type="datetime-local" step={1} /></Form.Item></Col><Col xs={24} sm={8}><Form.Item name="captureOffset" label="时区偏移（可选）"><Input placeholder="+08:00" maxLength={10} /></Form.Item></Col></Row>}
      <Form.Item name="locationMode" label="位置来源"><Select options={[{ value: 'AUTO', label: '使用文件提取值' }, { value: 'MANUAL', label: '人工修订 WGS84（留空表示清空）' }]} /></Form.Item>
      {locationMode === 'MANUAL' && <Row gutter={16}><Col xs={24} sm={12}><Form.Item name="longitude" label="经度"><InputNumber min={-180} max={180} style={{ width: '100%' }} /></Form.Item></Col><Col xs={24} sm={12}><Form.Item name="latitude" label="纬度"><InputNumber min={-90} max={90} style={{ width: '100%' }} /></Form.Item></Col></Row>}
    </Form>
  </Drawer>;
};
