import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { SparkJarOnlineEditorPage } from './SparkJarOnlineEditorPage';

const state = vi.hoisted(() => ({
  check: vi.fn(), update: vi.fn(), save: vi.fn(), compile: vi.fn(), trial: vi.fn(),
  source: { taskId: 'task', definitionVersion: 1, sourceCode: 'class Job {}', sourceSha256: 'source',
    compiledSourceSha256: null, persisted: true, hasUncompiledChanges: true, currentJarOrigin: null, currentJar: null },
  definition: { taskId: 'task', authoringMode: 'ONLINE', jobMode: 'BATCH', configured: false, definitionVersion: 1,
    resourceBindings: [], parameters: [{ name: 'region', value: 'CN' }], sparkConf: [{ name: 'spark.driver.extraJavaOptions', value: '-Dregion=CN' }],
    executionResources: { driverCores: 2, driverMemoryMiB: 4096, executorInstances: 1, executorCores: 1, executorMemoryMiB: 1024 }, timeoutSeconds: 500,
  },
}));
vi.mock('@tanstack/react-query', () => ({ useQueries: () => [] }));
vi.mock('../../model', () => ({ fetchDataModel: vi.fn() }));
vi.mock('../../datasource', () => ({ fetchTableMetadata: vi.fn() }));
vi.mock('../components/TaskRunLogViewer', () => ({ TaskRunLogViewer: () => null }));
vi.mock('../components/SparkJarTrialPreviewPanel', () => ({ SparkJarTrialPreviewPanel: () => null }));
vi.mock('../components/SparkJarJavaEditor', () => ({
  SparkJarJavaEditor: ({ value, onChange }: { value: string; onChange: (value: string) => void }) => (
    <textarea aria-label="Java source" value={value} onChange={(event) => onChange(event.target.value)} />),
}));
vi.mock('../components/SparkJarResourceDrawer', () => ({
  SparkJarResourceDrawer: ({ onConfirm, error }: {
    onConfirm: (selection: unknown) => void; error?: string;
  }) => <div>{error}<button onClick={() => onConfirm({
    binding: { bindingName: 'assets', resourceId: 'model', resourceType: 'MODEL', accessMode: 'READ', topicName: null }, table: null,
  })}>保存测试资源</button></div>,
}));
vi.mock('../hooks/useTasks', () => ({
  useTask: () => ({ data: { id: 'task', name: '测试任务', type: 'SPARK_JAR', status: 'DRAFT' } }),
  useSparkJarOnlineSource: () => ({ data: state.source }),
  useSparkJarTaskDefinition: () => ({ data: state.definition }),
  useSparkJarDevelopmentKit: () => ({ data: { configuration: { samples: [], jdbcTables: [] } } }),
  useSaveSparkJarOnlineSource: () => ({ mutateAsync: state.save, isPending: false }),
  useCompileSparkJarOnlineSource: () => ({ mutateAsync: state.compile, isPending: false }),
  useCheckSparkJarOnlineSource: () => ({ mutateAsync: state.check, isPending: false }),
  useUpdateSparkJarTaskDefinition: () => ({ mutateAsync: state.update, isPending: false }),
  useTrialRunSparkJarOnlineSource: () => ({ mutateAsync: state.trial, isPending: false }),
  useTaskRuns: () => ({ data: { content: [] } }),
  useTaskRun: () => ({}), useSparkJarTrialPreview: () => ({}),
  useCancelTaskRun: () => ({}), useForceTerminateTaskRun: () => ({}), useStopTaskRun: () => ({}),
}));

const open = () => render(<RouterProvider router={createMemoryRouter([
  { path: '/task/:taskId/online', element: <SparkJarOnlineEditorPage /> },
], { initialEntries: ['/task/task/online'] })} />);
beforeEach(() => {
  vi.resetAllMocks(); localStorage.clear();
  state.save.mockImplementation(async ({ sourceCode }: { sourceCode: string }) => ({ ...state.source, sourceCode }));
});
afterEach(() => { cleanup(); vi.useRealTimers(); });

describe('SparkJarOnlineEditorPage', () => {
  it('shows a successful check without saving or applying code', async () => {
    state.check.mockResolvedValue({ status: 'SUCCEEDED', diagnostics: [], source: state.source });
    open();
    await userEvent.click(screen.getByRole('button', { name: /检查代码/ }));
    expect(await screen.findByText('检查通过')).toBeVisible();
    expect(state.check).toHaveBeenCalledWith({ id: 'task', sourceCode: 'class Job {}' });
    expect(state.save).not.toHaveBeenCalled();
    expect(state.compile).not.toHaveBeenCalled();
  });

  it('discards diagnostics if the source was edited while the check was pending', async () => {
    let finish: (value: unknown) => void = () => undefined;
    state.check.mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    open();
    await userEvent.click(screen.getByRole('button', { name: /检查代码/ }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Java source' }), ' ');
    finish({ status: 'FAILED', source: state.source, diagnostics: [{ message: 'old diagnostic', severity: 'ERROR' }] });
    await waitFor(() => expect(screen.getByText('检查期间代码已修改，请重新检查')).toBeVisible());
    expect(screen.queryByText('old diagnostic')).not.toBeInTheDocument();
    expect(screen.getByText('当前代码尚未检查')).toBeVisible();
  });

  it.each([true, false])('preserves resource inheritance (%s) when saving editor resource bindings', async (inheritEngineResources) => {
    Object.assign(state.definition, { inheritEngineResources });
    localStorage.setItem('datascalpel.spark-jar.auto-save', 'false');
    state.update.mockResolvedValue(state.definition);
    open();
    await userEvent.type(screen.getByRole('textbox', { name: 'Java source' }), ' // unsaved');
    await userEvent.click(screen.getByRole('button', { name: '添加任务资源' }));
    await userEvent.click(screen.getByRole('button', { name: '保存测试资源' }));
    await waitFor(() => expect(state.update).toHaveBeenCalledOnce());
    expect(state.update.mock.calls[0][0]).toEqual({ id: 'task', request: {
      parameters: state.definition.parameters, sparkConf: state.definition.sparkConf,
      inheritEngineResources,
      executionResources: inheritEngineResources ? undefined : state.definition.executionResources, timeoutSeconds: 500,
      resourceBindings: [{ bindingName: 'assets', resourceId: 'model', resourceType: 'MODEL', accessMode: 'READ', topicName: null }],
      developmentConfiguration: { samples: [{ bindingName: 'assets', mode: 'ROW_COUNT', rowCount: 1000 }], jdbcTables: [] },
    } });
    expect(screen.getByRole('textbox', { name: 'Java source' })).toHaveValue('class Job {} // unsaved');
    expect(state.save).not.toHaveBeenCalled();
    expect(state.compile).not.toHaveBeenCalled();
  });

  it('keeps the resource drawer and unsaved code on save failure', async () => {
    state.update.mockRejectedValue(new Error('network'));
    open();
    await userEvent.click(screen.getByRole('button', { name: '添加任务资源' }));
    await userEvent.click(screen.getByRole('button', { name: '保存测试资源' }));
    expect(await screen.findByText('资源保存失败，请重试')).toBeVisible();
    expect(screen.getByRole('button', { name: '保存测试资源' })).toBeVisible();
  });
});

describe('online Java draft autosave', () => {
  const change = (text: string) => fireEvent.change(screen.getByRole('textbox', { name: 'Java source' }), { target: { value: text } });
  const advance = async (ms: number) => { await act(async () => { await vi.advanceTimersByTimeAsync(ms); }); };
  beforeEach(() => { vi.useFakeTimers(); });

  it('defaults on, never saves on entry, and debounces actual edits without executing code', async () => {
    open();
    expect(screen.getByRole('switch', { name: '自动保存草稿' })).toBeChecked();
    expect(screen.queryByRole('button', { name: /保存草稿/ })).not.toBeInTheDocument();
    await advance(2000); expect(state.save).not.toHaveBeenCalled();
    change('class First {}'); await advance(1000);
    change('class Latest {}'); await advance(1499); expect(state.save).not.toHaveBeenCalled();
    await advance(1);
    expect(state.save).toHaveBeenCalledExactlyOnceWith({ id: 'task', sourceCode: 'class Latest {}' });
    expect(screen.getByText('草稿已保存')).toBeVisible();
    expect(state.compile).not.toHaveBeenCalled(); expect(state.trial).not.toHaveBeenCalled(); expect(state.check).not.toHaveBeenCalled();
  });

  it('cancels queued autosave when disabled, remembers the switch, and allows manual saving', async () => {
    const view = open(); change('class Manual {}'); await advance(1000);
    fireEvent.click(screen.getByRole('switch', { name: '自动保存草稿' }));
    await advance(3000); expect(state.save).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /保存草稿/ })); await advance(0);
    expect(state.save).toHaveBeenCalledOnce();
    view.unmount(); open();
    expect(screen.getByRole('switch', { name: '自动保存草稿' })).not.toBeChecked();
  });

  it('serializes saves and never replays an old response over newer editor content', async () => {
    let finish!: (value: unknown) => void;
    state.save.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    open(); change('class First {}'); await advance(1500);
    expect(screen.getByText('正在保存草稿…')).toBeVisible();
    change('class Latest {}'); await advance(3000);
    expect(state.save).toHaveBeenCalledOnce();
    await act(async () => { finish({ ...state.source, sourceCode: 'class First {}' }); });
    expect(screen.getByRole('textbox', { name: 'Java source' })).toHaveValue('class Latest {}');
    expect(screen.getByText('等待自动保存')).toBeVisible();
    await advance(1500);
    expect(state.save).toHaveBeenLastCalledWith({ id: 'task', sourceCode: 'class Latest {}' });
    expect(screen.getByText('草稿已保存')).toBeVisible();
  });

  it('keeps failed drafts and pauses automatic retries until explicitly retried', async () => {
    state.save.mockRejectedValueOnce(new Error('offline'));
    open(); change('class Pending {}'); await advance(1500);
    expect(screen.getByText('草稿保存失败')).toBeVisible();
    expect(screen.getByRole('textbox', { name: 'Java source' })).toHaveValue('class Pending {}');
    await advance(10000); expect(state.save).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole('button', { name: '重试保存' })); await advance(0);
    expect(state.save).toHaveBeenCalledTimes(2);
    expect(screen.queryByText('草稿保存失败')).not.toBeInTheDocument();
  });

  it('resumes dirty drafts when switched on, and cancels unsent work when unmounted', async () => {
    localStorage.setItem('datascalpel.spark-jar.auto-save', 'false');
    const view = open(); change('class Resumed {}'); await advance(2000);
    expect(state.save).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('switch', { name: '自动保存草稿' })); await advance(1500);
    expect(state.save).toHaveBeenCalledOnce();
    change('class NotSubmitted {}'); view.unmount(); await advance(2000);
    expect(state.save).toHaveBeenCalledOnce();
  });

  it('blocks oversized source without calling the save API', async () => {
    open(); change('x'.repeat(256 * 1024 + 1)); await advance(1500);
    expect(state.save).not.toHaveBeenCalled(); expect(screen.getByText('草稿保存失败')).toBeVisible();
  });

  it('does not repeatedly save when the backend normalizes CRLF to LF', async () => {
    state.save.mockImplementation(async ({ sourceCode }: { sourceCode: string }) => ({ ...state.source, sourceCode: sourceCode.replace(/\r\n?/g, '\n') }));
    open(); change('class Normalized {\r\n}\r\n'); await advance(1500); await advance(5000);
    expect(state.save).toHaveBeenCalledOnce();
    expect(screen.getByText('草稿已保存')).toBeVisible();
  });
});
