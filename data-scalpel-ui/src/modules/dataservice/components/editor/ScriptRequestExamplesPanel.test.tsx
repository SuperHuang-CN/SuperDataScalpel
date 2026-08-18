import type { ScriptRequestExample } from '@superhuang/super-api-studio-script-workbench';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { ScriptRequestExamplesPanel } from './ScriptRequestExamplesPanel';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const examples: ScriptRequestExample[] = [
  {
    id: 'example-1',
    name: '默认示例',
    bodyText: '{}',
    query: [],
    headers: [{ id: 'header-1', key: 'Content-Type', value: 'application/json' }],
  },
  {
    id: 'example-2',
    name: '分页查询',
    bodyText: '{"page":1}',
    query: [],
    headers: [],
  },
];

describe('ScriptRequestExamplesPanel', () => {
  afterEach(() => cleanup());

  beforeAll(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
  });

  it('edits the active request example from the sidebar panel', () => {
    const onChange = vi.fn();

    render(
      <ScriptRequestExamplesPanel
        examples={examples}
        activeExampleId="example-1"
        readOnly={false}
        onActiveExampleChange={vi.fn()}
        onChange={onChange}
      />,
    );

    fireEvent.change(screen.getByPlaceholderText('Example 名称'), { target: { value: '基础查询' } });

    expect(onChange).toHaveBeenCalledWith([
      expect.objectContaining({ id: 'example-1', name: '基础查询' }),
      examples[1],
    ]);
  });

  it('creates a new example and makes it active', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onActiveExampleChange = vi.fn();

    render(
      <ScriptRequestExamplesPanel
        examples={examples}
        activeExampleId="example-1"
        readOnly={false}
        onActiveExampleChange={onActiveExampleChange}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole('button', { name: '新建请求示例' }));

    const nextExamples = onChange.mock.calls[0][0] as ScriptRequestExample[];
    expect(nextExamples).toHaveLength(3);
    expect(nextExamples[2]).toMatchObject({ name: 'Example', bodyText: '{\n  \n}' });
    expect(onActiveExampleChange).toHaveBeenCalledWith(nextExamples[2].id);
  });
});
