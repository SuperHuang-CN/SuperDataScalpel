import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiConsumer } from '../model/apiConsumer';

const hooks = vi.hoisted(() => ({
  createCredential: vi.fn(),
  rotateCredential: vi.fn(),
  reconcileCredential: vi.fn(),
  deleteCredential: vi.fn(),
  createSubscription: vi.fn(),
  syncSubscription: vi.fn(),
  reconcileSubscription: vi.fn(),
  revokeSubscription: vi.fn(),
  credentials: [] as Array<Record<string, unknown>>,
  subscriptions: [] as Array<Record<string, unknown>>,
}));

vi.mock('../hooks/useApiConsumerAccess', () => ({
  useApiConsumerCredentials: () => ({ data: hooks.credentials, isFetching: false }),
  useApiServiceSubscriptions: () => ({
    data: { content: hooks.subscriptions, totalElements: hooks.subscriptions.length },
    isFetching: false,
  }),
  useCreateApiConsumerCredential: () => ({ isPending: false, mutateAsync: hooks.createCredential }),
  useRotateApiConsumerCredential: () => ({ isPending: false, variables: undefined, mutateAsync: hooks.rotateCredential }),
  useReconcileApiConsumerCredentialGateway: () => ({
    isPending: false,
    variables: undefined,
    mutateAsync: hooks.reconcileCredential,
  }),
  useDeleteApiConsumerCredential: () => ({ isPending: false, variables: undefined, mutateAsync: hooks.deleteCredential }),
  useCreateApiServiceSubscription: () => ({ isPending: false, mutateAsync: hooks.createSubscription }),
  useSyncApiServiceSubscription: () => ({ isPending: false, variables: undefined, mutateAsync: hooks.syncSubscription }),
  useReconcileApiServiceSubscriptionGateway: () => ({
    isPending: false,
    variables: undefined,
    mutateAsync: hooks.reconcileSubscription,
  }),
  useRevokeApiServiceSubscription: () => ({ isPending: false, variables: undefined, mutateAsync: hooks.revokeSubscription }),
}));

vi.mock('../hooks/useDataServices', () => ({
  useDataServices: () => ({
    data: {
      content: [{
        id: 'service-1',
        code: 'orders',
        name: '订单服务',
        directoryId: null,
        type: 'STANDARD_TABLE',
        engineId: 'engine-1',
        routePath: '/open-api/v1/orders',
        accessMode: 'SUBSCRIPTION_REQUIRED',
        status: 'ENABLED',
        revision: 1,
        deploymentStatus: 'DEPLOYED',
        deploymentError: null,
        deployedAt: '2026-07-25T00:00:00Z',
        gatewayBindings: [{
          id: 'service-binding-1',
          provider: 'KONG',
          externalServiceId: 'kong-service-1',
          externalRouteId: 'kong-route-1',
          publishedRevision: 1,
          publicationStatus: 'PUBLISHED',
          gatewayUrl: 'http://gateway.test/open-api/v1/orders',
          lastError: null,
          operationStartedAt: null,
          publishedAt: '2026-07-25T00:00:00Z',
          reconciliationStatus: 'NOT_CHECKED',
          reconciliationReason: null,
          reconciliationMessage: null,
          reconciliationOperationId: null,
          reconciliationStartedAt: null,
          lastReconciledAt: null,
          createdAt: '2026-07-25T00:00:00Z',
          updatedAt: '2026-07-25T00:00:00Z',
        }],
        sourceId: 'model-1',
        sourceName: '订单模型',
        description: null,
        createdAt: '2026-07-25T00:00:00Z',
        updatedAt: '2026-07-25T00:00:00Z',
      }],
    },
    isFetching: false,
  }),
}));

import { ApiConsumerAccessDrawer } from './ApiConsumerAccessDrawer';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const consumer: ApiConsumer = {
  id: 'consumer-1',
  code: 'client-a',
  name: '客户系统',
  description: null,
  revision: 1,
  gatewayBindings: [{
    id: 'consumer-binding-1',
    provider: 'KONG',
    externalId: 'kong-consumer-1',
    syncedRevision: 1,
    syncStatus: 'SYNCED',
    lastError: null,
    operationStartedAt: null,
    lastSyncedAt: '2026-07-25T00:00:00Z',
    reconciliationStatus: 'NOT_CHECKED',
    reconciliationReason: null,
    reconciliationMessage: null,
    reconciliationOperationId: null,
    reconciliationStartedAt: null,
    lastReconciledAt: null,
    createdAt: '2026-07-25T00:00:00Z',
    updatedAt: '2026-07-25T00:00:00Z',
  }],
  createdAt: '2026-07-25T00:00:00Z',
  updatedAt: '2026-07-25T00:00:00Z',
};

describe('ApiConsumerAccessDrawer', () => {
  beforeEach(() => {
    hooks.credentials = [];
    hooks.subscriptions = [];
    hooks.reconcileCredential.mockReset();
    hooks.rotateCredential.mockReset();
    hooks.createCredential.mockReset().mockResolvedValue({
      credential: {
        id: 'credential-1',
        consumerId: consumer.id,
        name: '生产密钥',
        secretHint: 'dsk_…123456',
        revision: 1,
        gatewayBindings: [],
        createdAt: '2026-07-25T00:00:00Z',
        updatedAt: '2026-07-25T00:00:00Z',
      },
      secret: 'dsk_one_time_secret',
    });
    hooks.createSubscription.mockReset().mockResolvedValue({
      id: 'subscription-1',
      consumerId: consumer.id,
      consumerCode: consumer.code,
      consumerName: consumer.name,
      dataServiceId: 'service-1',
      dataServiceCode: 'orders',
      dataServiceName: '订单服务',
      routePath: '/open-api/v1/orders',
      accessMode: 'SUBSCRIPTION_REQUIRED',
      desiredState: 'GRANTED',
      gatewayBindings: [],
      createdAt: '2026-07-25T00:00:00Z',
      updatedAt: '2026-07-25T00:00:00Z',
    });
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

  afterEach(() => cleanup());

  it('shows a created API Key once and creates a service subscription', async () => {
    const user = userEvent.setup();
    render(
      <ApiConsumerAccessDrawer
        open
        consumer={consumer}
        canManage
        onClose={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: /新建 API Key/ }));
    const credentialNameInput = await screen.findByLabelText('用途名称');
    const createDialog = credentialNameInput.closest('[role="dialog"]');
    expect(createDialog).not.toBeNull();
    await user.type(credentialNameInput, '生产密钥');
    await user.click(within(createDialog as HTMLElement).getByRole('button', { name: /创\s*建/ }));

    await waitFor(() => expect(hooks.createCredential).toHaveBeenCalledWith({
      consumerId: consumer.id,
      request: { name: '生产密钥' },
    }));
    expect(await screen.findByText('dsk_one_time_secret')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '我已保存' }));
    await waitFor(() => expect(screen.queryByText('dsk_one_time_secret')).not.toBeInTheDocument());

    await user.click(screen.getByRole('tab', { name: '服务订阅' }));
    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByText('订单服务（orders）'));
    await user.click(screen.getByRole('button', { name: /新增订阅/ }));

    await waitFor(() => expect(hooks.createSubscription).toHaveBeenCalledWith({
      consumerId: consumer.id,
      dataServiceId: 'service-1',
    }));
  });

  it('shows business intent separately from a failed gateway revoke', async () => {
    hooks.subscriptions = [{
      id: 'subscription-1',
      consumerId: consumer.id,
      consumerCode: consumer.code,
      consumerName: consumer.name,
      dataServiceId: 'service-1',
      dataServiceCode: 'orders',
      dataServiceName: '订单服务',
      routePath: '/open-api/v1/orders',
      accessMode: 'SUBSCRIPTION_REQUIRED',
      desiredState: 'REVOKED',
      gatewayBindings: [{
        id: 'subscription-binding-1',
        provider: 'KONG',
        externalMembershipId: 'acl-1',
        status: 'REVOKE_FAILED',
        lastError: 'Kong 暂时不可用',
        operationId: null,
        operationStartedAt: null,
        grantedAt: '2026-07-25T00:00:00Z',
        reconciliationStatus: 'DRIFTED',
        reconciliationReason: 'UNEXPECTED_REMOTE',
        reconciliationMessage: 'Kong ACL 授权仍然存在',
        reconciliationOperationId: null,
        reconciliationStartedAt: null,
        lastReconciledAt: '2026-07-25T01:00:00Z',
        createdAt: '2026-07-25T00:00:00Z',
        updatedAt: '2026-07-25T00:00:00Z',
      }],
      createdAt: '2026-07-25T00:00:00Z',
      updatedAt: '2026-07-25T00:00:00Z',
    }];

    const user = userEvent.setup();
    render(
      <ApiConsumerAccessDrawer
        open
        consumer={consumer}
        canManage
        onClose={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('tab', { name: '服务订阅' }));

    expect(await screen.findByText('正在撤回')).toBeInTheDocument();
    expect(screen.getByText(/Kong · 撤回失败/)).toBeInTheDocument();
  });

  it('shows credential drift and maps a missing remote key to explicit rotation', async () => {
    const credential = {
      id: 'credential-1',
      consumerId: consumer.id,
      name: '生产密钥',
      secretHint: 'dsk_…123456',
      revision: 1,
      gatewayBindings: [{
        id: 'credential-binding-1',
        provider: 'KONG',
        externalId: 'missing',
        syncedRevision: 1,
        status: 'ACTIVE',
        lastError: null,
        operationId: null,
        operationStartedAt: null,
        lastSyncedAt: '2026-07-25T00:00:00Z',
        reconciliationStatus: 'DRIFTED',
        reconciliationReason: 'REMOTE_MISSING',
        reconciliationMessage: 'Kong API Key 不存在；必须轮换后才能恢复',
        reconciliationOperationId: null,
        reconciliationStartedAt: null,
        lastReconciledAt: '2026-07-25T01:00:00Z',
        createdAt: '2026-07-25T00:00:00Z',
        updatedAt: '2026-07-25T01:00:00Z',
      }],
      createdAt: '2026-07-25T00:00:00Z',
      updatedAt: '2026-07-25T00:00:00Z',
    };
    hooks.credentials = [credential];
    hooks.reconcileCredential.mockResolvedValue(credential);
    hooks.rotateCredential.mockResolvedValue({ credential, secret: 'dsk_rotated' });

    const user = userEvent.setup();
    render(
      <ApiConsumerAccessDrawer
        open
        consumer={consumer}
        canManage
        onClose={vi.fn()}
      />,
    );

    expect(await screen.findByText('对账 · 有漂移')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '对账生产密钥的网关状态' }));
    await waitFor(() => expect(hooks.reconcileCredential).toHaveBeenCalledWith({
      consumerId: consumer.id,
      credentialId: 'credential-1',
    }));

    const rotateButton = screen.getByRole('button', { name: '轮换生产密钥' });
    await user.hover(rotateButton);
    expect(await screen.findByText('轮换并重新同步（无法恢复原密钥）')).toBeInTheDocument();
    await user.click(rotateButton);
    const warning = await screen.findByText('旧密钥会立即失效，请先确认调用方可以同步更新。');
    const popover = warning.closest('.ant-popover');
    expect(popover).not.toBeNull();
    await user.click(within(popover as HTMLElement).getByRole('button', { name: /轮\s*换/ }));
    await waitFor(() => expect(hooks.rotateCredential).toHaveBeenCalledWith({
      consumerId: consumer.id,
      credentialId: 'credential-1',
    }));
  });
});
