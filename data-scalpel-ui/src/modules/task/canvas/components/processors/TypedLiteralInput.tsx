import { Input, Select, Space, Tag } from 'antd';
import type { CanvasLiteral, PlatformDataType } from '../../canvasTypes';

interface TypedLiteralInputProps {
  dataType: PlatformDataType;
  value: CanvasLiteral;
  onChange: (value: CanvasLiteral) => void;
  disabled?: boolean;
  status?: 'error' | 'warning';
  placeholder?: string;
  showTypeLabel?: boolean;
}

const literalPlaceholder = (dataType: PlatformDataType) => {
  switch (dataType) {
    case 'BOOLEAN': return 'true / false';
    case 'DATE': return 'yyyy-MM-dd';
    case 'TIMESTAMP': return 'ISO-8601，需时区';
    case 'TIMESTAMP_NTZ': return 'ISO-8601，无时区';
    case 'BINARY': return 'Base64';
    case 'DECIMAL': return '例如 0.00';
    default: return '输入固定值';
  }
};

export const TypedLiteralInput = ({
  dataType,
  value,
  onChange,
  disabled = false,
  status,
  placeholder,
  showTypeLabel = true,
}: TypedLiteralInputProps) => {
  const effectiveType = dataType === 'GEOMETRY' ? 'STRING' : dataType;
  const effectiveValue = value.dataType === effectiveType
    ? value
    : { dataType: effectiveType, value: value.value ?? '' };
  const editor = effectiveType === 'BOOLEAN' ? (
    <Select
      className={`canvas-typed-literal-value${showTypeLabel ? '' : ' is-type-hidden'}`}
      disabled={disabled}
      status={status}
      value={effectiveValue.value ?? undefined}
      placeholder="true / false"
      options={[
        { value: 'true', label: 'true' },
        { value: 'false', label: 'false' },
      ]}
      onChange={(next) => onChange({ dataType: effectiveType, value: next })}
    />
  ) : (
    <Input
      className={`canvas-typed-literal-value${showTypeLabel ? '' : ' is-type-hidden'}`}
      disabled={disabled}
      status={status}
      value={effectiveValue.value ?? ''}
      placeholder={placeholder ?? literalPlaceholder(effectiveType)}
      onChange={(event) => onChange({
        dataType: effectiveType,
        value: event.target.value,
      })}
    />
  );
  if (!showTypeLabel) return editor;
  return (
    <Space.Compact block className="canvas-typed-literal">
      <Tag className="canvas-typed-literal-type">{effectiveType}</Tag>
      {editor}
    </Space.Compact>
  );
};
