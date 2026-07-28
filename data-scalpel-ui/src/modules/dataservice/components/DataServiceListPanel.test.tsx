import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation, MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataServiceSummary } from '../model/dataService';

const dataServiceMocks = vi.hoisted(() => ({
  content: [] as DataServiceSummary[],
  unpublish: vi.fn(),
  disable: vi.fn(),
}));

vi.mock('../../directory', () => ({
  DirectoryTreePanel: () => null,
  findDirectoryDescendantIds: () => [],
  useDirectoryTree: () => ({ data: [], isFetching: false }),
}));

vi.mock('../../serviceengine', () => ({
  useServiceEngines: () => ({ data: { content: [] }, isFetching: false }),
}));

vi.mock('../api/dataServiceApi', () => ({ fetchDataService: vi.fn() }));

vi.mock('../hooks/useDataServices', () => ({
  useDataServices: () => ({
    data: { content: dataServiceMocks.content, totalElements: dataServiceMocks.content.length },
    isFetching: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useCleanupDataServiceDeployment: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useDeleteDataService: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useDisableDataService: () => ({ isPending: false, variables: undefined, mutateAsync: dataServiceMocks.disable }),
  useEnableDataService: () => ({ isPending: false, mutateAsync: vi.fn() }),
  usePublishDataService: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useReconcileDataServiceGateway: () => ({ isPending: false, variables: undefined, mutateAsync: vi.fn() }),
  useUnpublishDataService: () => ({
    isPending: false,
    variables: undefined,
    mutateAsync: dataServiceMocks.unpublish,
  }),
}));

vi.mock('./DataServiceSubscriptionsDrawer', () => ({
  DataServiceSubscriptionsDrawer: () => null,
}));

import { DataServiceListPanel } from './DataServiceListPanel';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const Location = () => {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
};

const renderPanel = (canViewDataSources = true, canPublish = false) => render(
  <MemoryRouter initialEntries={['/dataservice?type=SQL_QUERY&page=2']}>
    <DataServiceListPanel
      canCreate
      canDelete={false}
      canPublish={canPublish}
      canViewDirectories={false}
      canManageDirectories={false}
      canViewModels
      canViewDataSources={canViewDataSources}
      canViewEngines
    />
    <Location />
  </MemoryRouter>,
);

describe('DataServiceListPanel creation menu', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    dataServiceMocks.content = [];
    dataServiceMocks.unpublish.mockReset();
    dataServiceMocks.disable.mockReset();
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false, media: query, onchange: null,
        addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
      })),
    });
  });

  it('shows standard, SQL and disabled script choices and opens the SQL page', async () => {
    const user = userEvent.setup();
    renderPanel();
    await user.click(screen.getByRole('button', { name: /新建服务/ }));
    expect(await screen.findByText('标准单表服务')).toBeInTheDocument();
    expect(screen.getByText('SQL 查询服务')).toBeInTheDocument();
    const script = screen.getByText('脚本服务').closest('.ant-dropdown-menu-item');
    expect(script).toHaveClass('ant-dropdown-menu-item-disabled');
    await user.click(screen.getByText('SQL 查询服务'));
    expect(screen.getByTestId('location')).toHaveTextContent('/dataservice/new/sql');
  });

  it('disables SQL creation when data-source view permission is missing', async () => {
    const user = userEvent.setup();
    renderPanel(false);
    await user.click(screen.getByRole('button', { name: /新建服务/ }));
    const sql = (await screen.findByText('SQL 查询服务')).closest('.ant-dropdown-menu-item');
    expect(sql).toHaveClass('ant-dropdown-menu-item-disabled');
  });

  it('confirms gateway unpublish separately and keeps disable as a distinct destructive action', async () => {
    const user = userEvent.setup();
    const publishedService: DataServiceSummary = {
      id: 'service-1',
      code: 'user',
      name: '用户服务',
      directoryId: null,
      type: 'STANDARD_TABLE',
      engineId: 'engine-1',
      routePath: '/open-api/v1/users',
      accessMode: 'PUBLIC',
      status: 'ENABLED',
      revision: 3,
      deploymentStatus: 'DEPLOYED',
      deploymentError: null,
      deployedAt: '2026-07-27T10:00:00Z',
      gatewayBindings: [{
        id: 'binding-1',
        provider: 'KONG',
        externalServiceId: 'kong-service-1',
        externalRouteId: 'kong-route-1',
        publishedRevision: 3,
        publicationStatus: 'PUBLISHED',
        gatewayUrl: 'http://gateway.test/open-api/v1/users',
        lastError: null,
        operationStartedAt: null,
        publishedAt: '2026-07-27T10:01:00Z',
        reconciliationStatus: 'IN_SYNC',
        reconciliationReason: null,
        reconciliationMessage: null,
        reconciliationOperationId: null,
        reconciliationStartedAt: null,
        lastReconciledAt: '2026-07-27T10:02:00Z',
        createdAt: '2026-07-27T10:01:00Z',
        updatedAt: '2026-07-27T10:02:00Z',
      }],
      description: null,
      sourceId: 'model-1',
      sourceName: '用户模型',
      createdAt: '2026-07-27T09:00:00Z',
      updatedAt: '2026-07-27T10:02:00Z',
    };
    dataServiceMocks.content = [publishedService];
    dataServiceMocks.unpublish.mockResolvedValue({
      ...publishedService,
      gatewayBindings: [],
    });

    renderPanel(true, true);

    await user.click(screen.getByRole('button', { name: '取消发布用户服务' }));
    expect(await screen.findByText(/Service Engine 保持运行/)).toBeInTheDocument();
    expect(dataServiceMocks.unpublish).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: /^取消发布$/ }));
    expect(dataServiceMocks.unpublish).toHaveBeenCalledWith('service-1');

    await user.click(screen.getByRole('button', { name: '停用用户服务' }));
    expect(await screen.findByText(/再从 Service Engine 移除/)).toBeInTheDocument();
    expect(dataServiceMocks.disable).not.toHaveBeenCalled();
  });
});
