import { Spin, Typography } from 'antd';

interface CanvasCompilationMaskProps {
  visible: boolean;
  title: string;
  detail: string;
}

export const CanvasCompilationMask = ({
  visible,
  title,
  detail,
}: CanvasCompilationMaskProps) => {
  if (!visible) return null;
  return (
    <div className="canvas-compilation-mask" role="status" aria-live="polite">
      <Spin size="large" />
      <Typography.Text strong>{title}</Typography.Text>
      <Typography.Text type="secondary">{detail}</Typography.Text>
    </div>
  );
};
