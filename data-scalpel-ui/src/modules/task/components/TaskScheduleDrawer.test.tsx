import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TaskScheduleDrawer } from './TaskScheduleDrawer';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('TaskScheduleDrawer', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
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

  it('uses schedule defaults, validates Cron, and submits normalized values', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <TaskScheduleDrawer
        open
        schedule={null}
        submitting={false}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    );

    const name = await screen.findByLabelText('计划名称');
    const cron = screen.getByLabelText('Quartz Cron 表达式');
    const zone = screen.getByLabelText('IANA 时区');
    expect(cron).toHaveValue('0 0 2 * * ?');
    expect(zone).toHaveValue('Asia/Shanghai');

    await user.type(name, '  每日凌晨  ');
    await user.clear(cron);
    await user.type(cron, '0 2 * * *');
    await user.click(screen.getByRole('button', { name: /保\s*存/ }));
    expect(await screen.findByText('Quartz Cron 必须包含 6 或 7 个字段')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();

    await user.clear(cron);
    await user.type(cron, '0 30 2 * * ?');
    await user.click(screen.getByLabelText('重叠策略'));
    await user.click(await screen.findByText('允许重叠：仍创建新的运行实例'));
    await user.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({
      name: '每日凌晨',
      cronExpression: '0 30 2 * * ?',
      zoneId: 'Asia/Shanghai',
      misfirePolicy: 'FIRE_ONCE_NOW',
      overlapPolicy: 'ALLOW',
    }));
  }, 30_000);
});
