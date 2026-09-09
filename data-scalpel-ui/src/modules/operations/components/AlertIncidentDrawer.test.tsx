import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AlertIncidentDrawer } from './AlertIncidentDrawer';
import type { AlertRuleType } from '../model/operations';

const state = vi.hoisted(() => ({ type: 'RUN_FAILED' as AlertRuleType, canHandle: true, mutate: vi.fn(async () => ({})) }));
vi.mock('../../system', () => ({ useCurrentUser: () => ({ data: { permissions: state.canHandle ? ['task.view', 'alert.handle'] : ['task.view'] } }) }));
vi.mock('../hooks/useOperations', () => ({
  useAlert: () => ({ data: { id: 'incident-1', subjectName: '测试任务', ruleType: state.type, severity: 'CRITICAL', status: 'OPEN', conditionState: 'TRIGGERED', summary: '运行失败', sourceExists: false }, isPending: false, error: null }),
  useAlertHistory: () => ({ data: { content: [], totalElements: 0 }, error: null }),
  useOperationsMutation: () => ({ mutateAsync: state.mutate, isPending: false }),
}));
vi.mock('./RuntimeRunDrawer', () => ({ RuntimeRunDrawer: () => null }));
vi.mock('./AlertDeliveriesPanel', () => ({ AlertDeliveriesPanel: () => null }));
beforeEach(() => { state.type = 'RUN_FAILED'; state.canHandle = true; state.mutate.mockClear(); });
afterEach(cleanup);
const setup = () => render(<MemoryRouter><AlertIncidentDrawer incidentId="incident-1" onClose={() => undefined} /></MemoryRouter>);

describe('alert handling', () => {
  it('requires a written resolution before closing a failure', async () => {
    const user = userEvent.setup(); setup();
    await user.click(screen.getByRole('button', { name: '关闭告警' }));
    await user.click(screen.getByRole('button', { name: '确认关闭' }));
    await screen.findByText('请填写说明'); expect(state.mutate).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: '处理说明' }), { target: { value: '已核对并补数' } });
    await user.click(screen.getByRole('button', { name: '确认关闭' }));
    await waitFor(() => expect(state.mutate).toHaveBeenCalledWith({ id: 'incident-1', action: 'close', reason: '已核对并补数' }));
  });
  it('limits continuous conditions to acknowledgement and silence, with a required reason', async () => {
    state.type = 'QUEUE_TOO_LONG'; const user = userEvent.setup(); setup();
    expect(screen.queryByRole('button', { name: '关闭告警' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '静 默' }));
    await user.click(screen.getByRole('button', { name: '设置静默' }));
    await screen.findByText('请填写说明'); expect(state.mutate).not.toHaveBeenCalled();
  });
  it('hides shared handling commands without alert.handle', () => {
    state.canHandle = false; setup();
    expect(screen.queryByRole('button', { name: '确认接手' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '关闭告警' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '静 默' })).not.toBeInTheDocument();
  });
});
