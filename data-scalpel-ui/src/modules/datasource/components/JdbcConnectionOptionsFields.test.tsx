import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button, Form } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConnectionOptionDefinition } from '../model/dataSource';
import { JdbcConnectionOptionsFields } from './JdbcConnectionOptionsFields';

interface TestFormValues {
  connection?: {
    options?: Record<string, string>;
    customOptions?: { key?: string; value?: string }[];
  };
}

const TestForm = ({
  definitions,
  onFinish,
}: {
  definitions: ConnectionOptionDefinition[];
  onFinish: (values: TestFormValues) => void;
}) => {
  const [form] = Form.useForm<TestFormValues>();
  return (
    <Form
      form={form}
      initialValues={{ connection: { options: {}, customOptions: [] } }}
      onFinish={onFinish}
    >
      <JdbcConnectionOptionsFields definitions={definitions} />
      <Button htmlType="submit">提交</Button>
    </Form>
  );
};

beforeEach(() => {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

afterEach(() => cleanup());

describe('JdbcConnectionOptionsFields', () => {
  it('always renders the advanced section and submits custom rows for dialects without predefined options', async () => {
    const user = userEvent.setup();
    const onFinish = vi.fn();
    render(<TestForm definitions={[]} onFinish={onFinish} />);

    expect(screen.getByText('高级连接参数')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加自定义参数/ }));
    await user.type(screen.getByPlaceholderText('参数名，如 tcpKeepAlive'), 'tcpKeepAlive');
    await user.type(screen.getByPlaceholderText('参数值'), 'true');
    await user.click(screen.getByRole('button', { name: /提\s*交/ }));

    await waitFor(() => expect(onFinish).toHaveBeenCalledWith(expect.objectContaining({
      connection: expect.objectContaining({
        customOptions: [{ key: 'tcpKeepAlive', value: 'true' }],
      }),
    })));
  });

  it('keeps predefined parameters in typed controls and rejects the same key in a custom row', async () => {
    const user = userEvent.setup();
    const onFinish = vi.fn();
    const definitions: ConnectionOptionDefinition[] = [{
      key: 'sslmode',
      label: 'SSL 模式',
      type: 'SELECT',
      defaultValue: 'prefer',
      choices: [{ value: 'prefer', label: '优先' }, { value: 'require', label: '必须' }],
    }];
    render(<TestForm definitions={definitions} onFinish={onFinish} />);

    expect(screen.getByLabelText('SSL 模式')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /添加自定义参数/ }));
    await user.type(screen.getByPlaceholderText('参数名，如 tcpKeepAlive'), 'SSLMODE');
    await user.type(screen.getByPlaceholderText('参数值'), 'require');
    await user.click(screen.getByRole('button', { name: /提\s*交/ }));

    expect(await screen.findAllByText(/已有专用配置项/)).not.toHaveLength(0);
    expect(onFinish).not.toHaveBeenCalled();
  });
});
