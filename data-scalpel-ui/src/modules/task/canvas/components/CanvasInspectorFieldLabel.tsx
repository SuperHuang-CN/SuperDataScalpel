import { InfoCircleOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';

export const CanvasInspectorFieldLabel = ({
  label,
  tooltip,
  actionLabel,
  onClick,
}: {
  label: string;
  tooltip: string;
  actionLabel?: string;
  onClick?: () => void;
}) => (
  <span className="canvas-inspector-field-label">
    <span>{label}</span>
    <Tooltip title={tooltip}>
      {onClick ? (
        <Button
          type="text"
          size="small"
          className="canvas-inspector-field-help"
          icon={<InfoCircleOutlined />}
          aria-label={actionLabel ?? `查看${label}说明`}
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            onClick();
          }}
        />
      ) : (
        <InfoCircleOutlined
          className="canvas-inspector-field-help-icon"
          aria-label={actionLabel ?? `${label}说明`}
          tabIndex={0}
        />
      )}
    </Tooltip>
  </span>
);
