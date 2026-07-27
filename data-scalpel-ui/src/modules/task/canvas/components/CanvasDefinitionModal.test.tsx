import { cleanup, render, screen } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { emptyCanvasDefinition } from '../defaultCanvas';
import type { CanvasValidationResult } from '../canvasTypes';
import { CanvasDefinitionModal } from './CanvasDefinitionModal';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('CanvasDefinitionModal', () => {
  beforeAll(() => vi.stubGlobal('ResizeObserver', ResizeObserverStub));
  afterEach(() => cleanup());
  afterAll(() => vi.unstubAllGlobals());

  it('does not label a definition valid when Task Engine has not returned', () => {
    render(
      <CanvasDefinitionModal
        open
        definition={emptyCanvasDefinition()}
        validation={null}
        validationStatus="Task Engine 不可用"
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('尚未完成 Engine 校验')).toBeInTheDocument();
    expect(screen.getByText('Task Engine 不可用')).toBeInTheDocument();
    expect(screen.queryByText('定义有效')).not.toBeInTheDocument();
  });

  it('shows validity only from the current Task Engine result', () => {
    const validation: CanvasValidationResult = {
      valid: true,
      canvasIssues: [],
      nodeResults: new Map(),
    };
    render(
      <CanvasDefinitionModal
        open
        definition={emptyCanvasDefinition()}
        validation={validation}
        validationStatus="引擎校验通过"
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText('Engine 校验有效')).toBeInTheDocument();
    expect(screen.queryByText('尚未完成 Engine 校验')).not.toBeInTheDocument();
  });
});
