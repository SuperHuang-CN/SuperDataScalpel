import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConnectionTestResult } from '../model/dataSource';
import { ConnectionTestResultModal } from './ConnectionTestResultModal';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const failureResult: ConnectionTestResult = {
  success: false,
  code: 'NETWORK_ERROR',
  message: '连接失败，请检查主机、端口和网络',
  elapsedMs: 108,
  databaseProduct: null,
  databaseVersion: null,
  driverName: null,
  diagnostic: {
    exceptionType: 'com.mysql.cj.jdbc.exceptions.CommunicationsException',
    rawMessage: 'Communications link failure',
    sqlState: '08S01',
    vendorCode: 0,
    httpStatus: null,
    responsePreview: null,
    causes: [{ exceptionType: 'java.net.ConnectException', message: 'Connection refused' }],
  },
};

describe('ConnectionTestResultModal', () => {
  beforeAll(() => vi.stubGlobal('ResizeObserver', ResizeObserverStub));

  beforeEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false, media: query, onchange: null,
        addListener: vi.fn(), removeListener: vi.fn(),
        addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
      })),
    });
  });

  afterEach(() => cleanup());
  afterAll(() => vi.unstubAllGlobals());

  it('shows the raw driver error and cause chain and supports copying diagnostics', async () => {
    const user = userEvent.setup();
    render(
      <ConnectionTestResultModal
        open
        result={failureResult}
        targetLabel="MySQL · db.internal:3306/test"
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('连接测试失败')).toBeInTheDocument();
    expect(screen.getByText('08S01')).toBeInTheDocument();
    expect(screen.getByLabelText('原始错误')).toHaveValue('Communications link failure');
    expect(screen.getByLabelText('异常链')).toHaveValue(
      '1. java.net.ConnectException: Connection refused',
    );

    await user.click(screen.getByRole('button', { name: /复制诊断信息/ }));
    await waitFor(async () => expect(await navigator.clipboard.readText()).toContain(
      'Cause 1: java.net.ConnectException: Connection refused',
    ));
  });
});
