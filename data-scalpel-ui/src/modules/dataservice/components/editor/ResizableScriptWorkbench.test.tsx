import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ResizableScriptWorkbench } from './ResizableScriptWorkbench';

const STORAGE_KEY = 'data-scalpel.ui.script-workbench.result-height';

describe('ResizableScriptWorkbench', () => {
  beforeEach(() => window.localStorage.removeItem(STORAGE_KEY));
  afterEach(() => cleanup());

  it('adjusts and persists the result height with the separator keyboard controls', () => {
    render(
      <ResizableScriptWorkbench>
        <div>脚本工作台</div>
      </ResizableScriptWorkbench>,
    );

    const separator = screen.getByRole('separator', { name: '调整脚本编辑器和执行结果高度' });
    expect(separator).toHaveAttribute('aria-valuenow', '210');

    fireEvent.keyDown(separator, { key: 'ArrowUp' });

    expect(separator).toHaveAttribute('aria-valuenow', '226');
    expect(separator.parentElement).toHaveStyle({ '--data-service-script-result-height': '226px' });
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('226');
  });

  it('restores the default height on double click', () => {
    window.localStorage.setItem(STORAGE_KEY, '360');
    render(
      <ResizableScriptWorkbench>
        <div>脚本工作台</div>
      </ResizableScriptWorkbench>,
    );

    const separator = screen.getByRole('separator', { name: '调整脚本编辑器和执行结果高度' });
    expect(separator).toHaveAttribute('aria-valuenow', '360');

    fireEvent.doubleClick(separator);

    expect(separator).toHaveAttribute('aria-valuenow', '210');
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('210');
  });
});
