import { cleanup, render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SparkJarResourceActions } from './SparkJarResourceActions';
import { writeClipboardText } from '../../../shared/browser/writeClipboardText';
import type { SparkJarCodeResource } from '../model/sparkJarCodeResource';

vi.mock('../../../shared/browser/writeClipboardText', () => ({ writeClipboardText: vi.fn() }));
beforeEach(() => vi.clearAllMocks());
afterEach(cleanup);
const resource: SparkJarCodeResource = { kind: 'MODEL', bindingName: 'source_readings', label: 'readings', accessMode: 'READ', fields: [] };
const setup = (overrides: Partial<React.ComponentProps<typeof SparkJarResourceActions>> = {}) => {
  const props = { resource, busy: false, editingAllowed: true, fieldsExpanded: false,
    onEdit: vi.fn(), onRead: vi.fn(), onWrite: vi.fn(), onToggleFields: vi.fn(), ...overrides };
  render(<SparkJarResourceActions {...props} />);
  return props;
};
describe('resource action menu', () => {
  it('keeps three top-level actions and copies the binding rather than model name', async () => {
    const props = setup();
    expect(screen.getAllByRole('button')).toHaveLength(3);
    await userEvent.click(screen.getByRole('button', { name: '复制引用名' }));
    expect(writeClipboardText).toHaveBeenCalledWith('source_readings');
    expect(await screen.findByText('代码引用名已复制')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: '编辑绑定' }));
    expect(props.onEdit).toHaveBeenCalledOnce();
    expect(props.onRead).not.toHaveBeenCalled();
  });
  it('opens by hover without inserting and inserts only after choosing print', async () => {
    const props = setup();
    await userEvent.hover(screen.getByRole('button', { name: /代码操作/ }));
    const print = await screen.findByRole('menuitem', { name: '插入读取并打印前 20 行代码' });
    expect(props.onRead).not.toHaveBeenCalled();
    expect(screen.queryByRole('menuitem', { name: '插入写入模板' })).not.toBeInTheDocument();
    await userEvent.click(print);
    await waitFor(() => expect(props.onRead).toHaveBeenCalledWith(true));
  });
  it('opens by click and retains field viewing', async () => {
    const props = setup();
    await userEvent.click(screen.getByRole('button', { name: /代码操作/ }));
    await userEvent.click(await screen.findByRole('menuitem', { name: '查看字段' }));
    expect(props.onToggleFields).toHaveBeenCalledOnce();
    expect(props.onRead).not.toHaveBeenCalled();
  });
  it('opens by keyboard and restricts write-only resources to writing', async () => {
    const props = setup({ resource: { ...resource, accessMode: 'WRITE' } });
    const button = screen.getByRole('button', { name: /代码操作/ });
    button.focus();
    fireEvent.keyDown(button, { key: 'ArrowDown' });
    const write = await screen.findByRole('menuitem', { name: '插入写入模板' });
    expect(screen.queryByRole('menuitem', { name: '插入读取代码' })).not.toBeInTheDocument();
    await userEvent.click(write);
    await waitFor(() => expect(props.onWrite).toHaveBeenCalledOnce());
  });
  it('does not offer synchronous printing or fields for Kafka streams', async () => {
    setup({ resource: { ...resource, kind: 'KAFKA_TOPIC', accessMode: 'READ_WRITE' } });
    await userEvent.click(screen.getByRole('button', { name: /代码操作/ }));
    expect(await screen.findByRole('menuitem', { name: '插入读取代码' })).toBeVisible();
    expect(screen.queryByRole('menuitem', { name: /打印/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: '查看字段' })).not.toBeInTheDocument();
  });
  it('keeps copy and field viewing available when editing is disabled', async () => {
    const props = setup({ busy: true, editingAllowed: false });
    expect(screen.getByRole('button', { name: '编辑绑定' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '复制引用名' })).toBeEnabled();
    await userEvent.click(screen.getByRole('button', { name: /代码操作/ }));
    const read = await screen.findByRole('menuitem', { name: '插入读取代码' });
    expect(read).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(read);
    expect(props.onRead).not.toHaveBeenCalled();
  });
  it('reports clipboard rejection without changing code', async () => {
    vi.mocked(writeClipboardText).mockRejectedValueOnce(new Error('denied'));
    const props = setup();
    await userEvent.click(screen.getByRole('button', { name: '复制引用名' }));
    expect(await screen.findByText('复制失败，请选中上方代码引用名手动复制')).toBeVisible();
    expect(props.onRead).not.toHaveBeenCalled();
  });
});
