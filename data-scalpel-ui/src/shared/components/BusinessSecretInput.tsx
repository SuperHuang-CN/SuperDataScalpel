import { EyeInvisibleOutlined, EyeOutlined } from '@ant-design/icons';
import type { InputProps } from 'antd';
import { Button, Input } from 'antd';
import { useState } from 'react';

export interface BusinessSecretInputProps extends Omit<InputProps, 'type' | 'suffix'> {
  revealLabel?: string;
  hideLabel?: string;
}

/**
 * A masked input for API keys, tokens and other non-login credentials.
 * It intentionally avoids password-field semantics so browser credential
 * managers do not pair nearby business fields with the product login form.
 */
export const BusinessSecretInput = ({
  className,
  revealLabel = '显示敏感值',
  hideLabel = '隐藏敏感值',
  ...props
}: BusinessSecretInputProps) => {
  const [revealed, setRevealed] = useState(false);

  return (
    <Input
      {...props}
      type="text"
      autoComplete="off"
      autoCapitalize="none"
      spellCheck={false}
      className={[
        'business-secret-input',
        revealed ? 'is-revealed' : 'is-masked',
        className,
      ].filter(Boolean).join(' ')}
      suffix={(
        <Button
          type="text"
          size="small"
          shape="circle"
          className="business-secret-visibility-button"
          icon={revealed ? <EyeOutlined /> : <EyeInvisibleOutlined />}
          aria-label={revealed ? hideLabel : revealLabel}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => setRevealed((current) => !current)}
        />
      )}
    />
  );
};
