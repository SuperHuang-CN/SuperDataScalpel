import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CanvasCompilationMask } from './CanvasCompilationMask';

describe('CanvasCompilationMask', () => {
  it('blocks the canvas while the current definition is waiting for Task Engine', () => {
    const { rerender } = render(
      <CanvasCompilationMask visible title="引擎校验中" detail="Task Engine 正在编译当前 Canvas 定义" />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('引擎校验中');
    expect(screen.getByRole('status')).toHaveTextContent('Task Engine 正在编译当前 Canvas 定义');

    rerender(<CanvasCompilationMask visible={false} title="引擎校验通过" detail="" />);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
