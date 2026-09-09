import { EnvironmentOutlined } from '@ant-design/icons';
import { Button, Drawer, Form, Input, InputNumber, Space, message } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../../shared/api/http';
import { useUpdateSystemConfiguration } from '../hooks/useSystemConfigurations';
import type { SystemConfiguration } from '../model/systemConfiguration';
import { parsePanoramaMapSettings, type MapSettings } from '../model/panoramaMapSettings';
export const PanoramaMapSettingsDrawer = ({ configuration, onClose }: { configuration: SystemConfiguration; onClose: () => void }) => {
  const [form] = Form.useForm<MapSettings>(); const mutation = useUpdateSystemConfiguration(); const client = useQueryClient(); const [messageApi, context] = message.useMessage();
  const submit = async (values: MapSettings) => {
    try { await mutation.mutateAsync({ id: configuration.id, request: { configValue: JSON.stringify({ url: values.url?.trim() ?? '', attribution: values.attribution?.trim() ?? '', maxZoom: values.maxZoom }) } }); await client.invalidateQueries({ queryKey: ['panorama-map-config'] }); messageApi.success('全景地图设置已保存'); onClose(); }
    catch (e) { messageApi.error(e instanceof ApiError ? e.message : '保存失败'); }
  };
  return <Drawer open size={680} rootClassName="business-overlay business-drawer-overlay" onClose={onClose} title={<Space><EnvironmentOutlined />全景地图设置</Space>}
    footer={<Space style={{ display: 'flex', justifyContent: 'flex-end' }}><Button onClick={onClose}>取消</Button><Button type="primary" loading={mutation.isPending} onClick={() => form.submit()}>保存</Button></Space>}>
    {context}<Form form={form} autoComplete="off" layout="vertical" initialValues={parsePanoramaMapSettings(configuration.configValue)} onFinish={values => void submit(values)}>
      <Form.Item name="url" label="XYZ 瓦片 URL" extra="标准 Web Mercator XYZ 地址，包含 {z}、{x}、{y}；留空关闭底图。" rules={[{ max: 3000 }, { validator: async (_, value: string | undefined) => {
        if (!value?.trim()) return;
        try { const url = new URL(value.trim().replace('{z}', '0').replace('{x}', '0').replace('{y}', '0')); if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.hash || !['{z}', '{x}', '{y}'].every(token => value.includes(token))) throw new Error(); }
        catch { throw new Error('请输入包含 {z}、{x}、{y} 的 HTTP(S) XYZ 地址'); }
      } }]}><Input placeholder="https://tiles.example.com/{z}/{x}/{y}.png" maxLength={3000} /></Form.Item>
      <Form.Item name="attribution" label="署名（纯文本）"><Input maxLength={500} /></Form.Item>
      <Form.Item name="maxZoom" label="最大缩放级别" rules={[{ required: true }]}><InputNumber min={0} max={22} precision={0} /></Form.Item>
    </Form>
  </Drawer>;
};
