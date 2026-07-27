import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation, MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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
  useDataServices: () => ({ data: { content: [], totalElements: 0 }, isFetching: false, isError: false, refetch: vi.fn() }),
  useCleanupDataServiceDeployment: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useDeleteDataService: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useDisableDataService: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useEnableDataService: () => ({ isPending: false, mutateAsync: vi.fn() }),
  usePublishDataService: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useReconcileDataServiceGateway: () => ({ isPending: false, variables: undefined, mutateAsync: vi.fn() }),
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

const renderPanel = (canViewDataSources = true) => render(
  <MemoryRouter initialEntries={['/dataservice?type=SQL_QUERY&page=2']}>
    <DataServiceListPanel
      canCreate
      canDelete={false}
      canPublish={false}
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
});
