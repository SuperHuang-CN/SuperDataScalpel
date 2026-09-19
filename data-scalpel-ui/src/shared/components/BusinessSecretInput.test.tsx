import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { BusinessSecretInput } from './BusinessSecretInput';

describe('BusinessSecretInput', () => {
  afterEach(cleanup);

  it('keeps business credentials outside browser password semantics while preserving a mask toggle', async () => {
    const user = userEvent.setup();
    render(<BusinessSecretInput aria-label="访问 Token" name="dispatcher-access-token" defaultValue="secret-token" />);

    const input = screen.getByRole('textbox', { name: '访问 Token' });
    expect(input).toHaveAttribute('type', 'text');
    expect(input).toHaveAttribute('autocomplete', 'off');
    expect(input).toHaveAttribute('name', 'dispatcher-access-token');
    expect(input.closest('.business-secret-input')).toHaveClass('is-masked');

    await user.click(screen.getByRole('button', { name: '显示敏感值' }));
    expect(input.closest('.business-secret-input')).toHaveClass('is-revealed');
    expect(screen.getByRole('button', { name: '隐藏敏感值' })).toBeInTheDocument();
  });
});
