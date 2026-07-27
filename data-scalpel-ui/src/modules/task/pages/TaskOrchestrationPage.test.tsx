import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryRouter, Link, RouterProvider } from 'react-router-dom';
import { TaskOrchestrationPage } from './TaskOrchestrationPage';

vi.mock('../canvas/CanvasDesigner', () => ({
  CanvasDesigner: ({
    onInspectorDirtyChange,
  }: {
    onInspectorDirtyChange?: (dirty: boolean) => void;
  }) => (
    <button type="button" onClick={() => onInspectorDirtyChange?.(true)}>
      修改节点草稿
    </button>
  ),
}));

const renderPage = () => {
  const router = createMemoryRouter([
    {
      path: '/canvas',
      element: (
        <>
          <TaskOrchestrationPage />
          <Link to="/other">离开编排页</Link>
        </>
      ),
    },
    { path: '/other', element: <div>其他页面</div> },
  ], { initialEntries: ['/canvas'] });
  render(<RouterProvider router={router} />);
};

describe('TaskOrchestrationPage', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query) => ({
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

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('blocks route changes until the user explicitly abandons the inspector draft', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole('button', { name: '修改节点草稿' }));

    await user.click(screen.getByRole('link', { name: '离开编排页' }));
    expect(await screen.findByText('当前节点的配置尚未应用，离开页面后这些修改会丢失。')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '继续编辑' }));
    expect(screen.getByRole('button', { name: '修改节点草稿' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: '离开编排页' }));
    await user.click(await screen.findByRole('button', { name: '放弃并离开' }));
    expect(await screen.findByText('其他页面')).toBeInTheDocument();
  });

  it('only prevents browser unload while the inspector draft is dirty', async () => {
    const user = userEvent.setup();
    renderPage();
    const cleanEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(cleanEvent);
    expect(cleanEvent.defaultPrevented).toBe(false);

    await user.click(screen.getByRole('button', { name: '修改节点草稿' }));
    const dirtyEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(dirtyEvent);
    expect(dirtyEvent.defaultPrevented).toBe(true);
  });
});
