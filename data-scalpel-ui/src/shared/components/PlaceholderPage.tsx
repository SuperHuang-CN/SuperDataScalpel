import { ToolOutlined } from '@ant-design/icons';
import { Empty, Typography } from 'antd';

export interface PlaceholderPageProps {
  title: string;
  description: string;
}

export const PlaceholderPage = ({ title, description }: PlaceholderPageProps) => (
  <div className="placeholder-page">
    <Typography.Title level={2}>{title}</Typography.Title>
    <Empty image={<ToolOutlined className="placeholder-icon" />} description={description} />
  </div>
);
