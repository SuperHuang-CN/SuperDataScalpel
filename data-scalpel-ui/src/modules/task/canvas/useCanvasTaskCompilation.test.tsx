import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cancelTaskCompilation, compileCanvasTask } from '../api/taskCompilationApi';
import { emptyCanvasDefinition } from './defaultCanvas';
import type { CanvasDefinition } from './canvasTypes';
import type { TaskCompilationMetadataSnapshot, TaskCompilationResponse } from './taskCompilationTypes';
import {
  canvasCompilationFingerprint,
  isCanvasTaskCompilationBlocking,
  useCanvasTaskCompilation,
} from './useCanvasTaskCompilation';

vi.mock('../api/taskCompilationApi', () => ({
  compileCanvasTask: vi.fn(),
  cancelTaskCompilation: vi.fn(),
}));

const emptyMetadata: TaskCompilationMetadataSnapshot = {
  dataSources: [],
  models: [],
  fileDatasetTables: [],
};

const inputDefinition = (tableName = ''): CanvasDefinition => ({
  schemaVersion: 1,
  schemaMinorVersion: 6,
  nodes: [{
    id: '4add70a7-4948-42a5-af66-e56dbaccad3e',
    type: 'JDBC_INPUT',
    name: '订单输入',
    layout: { x: 10, y: 20, width: 240, height: 120 },
    configuration: { dataSourceId: '', tableName },
  }],
  edges: [],
});

const outputDefinition = (columnMappingMode: 'BY_NAME' | 'EXPLICIT'): CanvasDefinition => ({
  schemaVersion: 1,
  schemaMinorVersion: 6,
  nodes: [{
    id: 'd35adbfb-9a83-4d92-b229-d4af1a5049cf',
    type: 'JDBC_OUTPUT',
    name: 'JDBC 输出',
    layout: { x: 550, y: 110, width: 240, height: 120 },
    configuration: {
      sourceTableName: 'sys_user',
      dataSourceId: 'd050e292-1f48-43b9-9309-2980d8f92bc6',
      targetTableName: 'sys_user_copy',
      writeMode: 'OVERWRITE',
      columnMappingMode,
      columnMappings: columnMappingMode === 'EXPLICIT'
        ? [{ sourceColumnName: 'id', targetColumnName: 'user_id' }]
        : [],
    },
  }],
  edges: [],
});

const response = (requestId: string): TaskCompilationResponse => ({
  requestId,
  taskType: 'CANVAS',
  valid: false,
  durationMs: 3,
  sparkApplicationId: 'local-test',
  canvasIssues: [],
  nodeResults: [],
});

describe('canvasCompilationFingerprint', () => {
  it('ignores layout changes but includes semantic configuration and metadata', () => {
    const original = inputDefinition('orders');
    const moved = structuredClone(original);
    moved.nodes[0].layout.x = 900;
    const reconfigured = inputDefinition('customers');

    expect(canvasCompilationFingerprint(original, emptyMetadata))
      .toBe(canvasCompilationFingerprint(moved, emptyMetadata));
    expect(canvasCompilationFingerprint(original, emptyMetadata))
      .not.toBe(canvasCompilationFingerprint(reconfigured, emptyMetadata));
    expect(canvasCompilationFingerprint(original, emptyMetadata))
      .not.toBe(canvasCompilationFingerprint(original, {
        dataSources: [{
          id: '55859069-6387-4390-b850-104845ee5370',
          enabled: true,
          connectionKind: 'JDBC',
          purposes: ['SOURCE'],
          tables: [],
        }],
        models: [],
        fileDatasetTables: [],
      }));
  });
});

describe('isCanvasTaskCompilationBlocking', () => {
  it('blocks editing only while metadata or the current engine result is pending', () => {
    expect(isCanvasTaskCompilationBlocking('WAITING_METADATA')).toBe(true);
    expect(isCanvasTaskCompilationBlocking('WAITING')).toBe(true);
    expect(isCanvasTaskCompilationBlocking('COMPILING')).toBe(true);
    expect(isCanvasTaskCompilationBlocking('SUCCESS')).toBe(false);
    expect(isCanvasTaskCompilationBlocking('UNAVAILABLE')).toBe(false);
  });
});

describe('useCanvasTaskCompilation', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(cancelTaskCompilation).mockResolvedValue({
      requestId: '00000000-0000-0000-0000-000000000000',
      state: 'CANCEL_REQUESTED',
    });
    vi.mocked(compileCanvasTask).mockImplementation(async (request) => response(request.requestId));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('keeps an initially empty canvas local until its first semantic change', async () => {
    const { result, rerender } = renderHook(
      ({ definition }) => useCanvasTaskCompilation({
        definition,
        metadataSnapshot: emptyMetadata,
        metadataLoading: false,
        metadataError: false,
      }),
      { initialProps: { definition: emptyCanvasDefinition() } },
    );

    await act(async () => vi.advanceTimersByTimeAsync(800));
    expect(result.current.status).toBe('IDLE');
    expect(compileCanvasTask).not.toHaveBeenCalled();

    rerender({ definition: inputDefinition() });
    expect(result.current.status).toBe('WAITING');
    await act(async () => vi.advanceTimersByTimeAsync(400));

    expect(compileCanvasTask).toHaveBeenCalledTimes(1);
    expect(result.current.status).toBe('SUCCESS');
  });

  it('debounces rapid semantic changes and submits only the latest definition', async () => {
    const { rerender } = renderHook(
      ({ definition }) => useCanvasTaskCompilation({
        definition,
        metadataSnapshot: emptyMetadata,
        metadataLoading: false,
        metadataError: false,
      }),
      { initialProps: { definition: inputDefinition('orders') } },
    );

    await act(async () => vi.advanceTimersByTimeAsync(250));
    rerender({ definition: inputDefinition('customers') });
    await act(async () => vi.advanceTimersByTimeAsync(399));
    expect(compileCanvasTask).not.toHaveBeenCalled();

    await act(async () => vi.advanceTimersByTimeAsync(1));
    expect(compileCanvasTask).toHaveBeenCalledTimes(1);
    expect(vi.mocked(compileCanvasTask).mock.calls[0][0].task.definition.nodes[0].configuration)
      .toMatchObject({ tableName: 'customers' });
  });

  it('revalidates an explicitly applied configuration even when its semantic fingerprint is unchanged', async () => {
    const definition = outputDefinition('EXPLICIT');
    const { rerender } = renderHook(
      ({ requestVersion }) => useCanvasTaskCompilation({
        definition,
        metadataSnapshot: emptyMetadata,
        metadataLoading: false,
        metadataError: false,
        validationRequestVersion: requestVersion,
      }),
      { initialProps: { requestVersion: 0 } },
    );

    await act(async () => vi.advanceTimersByTimeAsync(400));
    expect(compileCanvasTask).toHaveBeenCalledTimes(1);

    rerender({ requestVersion: 1 });
    await act(async () => vi.advanceTimersByTimeAsync(400));

    expect(compileCanvasTask).toHaveBeenCalledTimes(2);
    expect(vi.mocked(compileCanvasTask).mock.calls[1][0].task.definition.nodes[0].configuration)
      .toEqual({
        sourceTableName: 'sys_user',
        dataSourceId: 'd050e292-1f48-43b9-9309-2980d8f92bc6',
        targetTableName: 'sys_user_copy',
        writeMode: 'OVERWRITE',
        columnMappingMode: 'EXPLICIT',
        columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'user_id' }],
      });
  });

  it('does not compile again when only node layout changes', async () => {
    const original = inputDefinition('orders');
    const { rerender } = renderHook(
      ({ definition }) => useCanvasTaskCompilation({
        definition,
        metadataSnapshot: emptyMetadata,
        metadataLoading: false,
        metadataError: false,
      }),
      { initialProps: { definition: original } },
    );

    await act(async () => vi.advanceTimersByTimeAsync(400));
    const moved = structuredClone(original);
    moved.nodes[0].layout.y = 500;
    rerender({ definition: moved });
    await act(async () => vi.advanceTimersByTimeAsync(800));

    expect(compileCanvasTask).toHaveBeenCalledTimes(1);
  });

  it('aborts and best-effort cancels an active request when the fingerprint changes', async () => {
    const requestIds = [
      'a39bb068-bbb9-40b0-8136-1b1adecc3953',
      'b30a90b3-dd8b-450e-a428-fb27aa079e65',
    ];
    vi.spyOn(globalThis.crypto, 'randomUUID').mockImplementation(() => requestIds.shift() as `${string}-${string}-${string}-${string}-${string}`);
    let firstSignal: AbortSignal | undefined;
    vi.mocked(compileCanvasTask)
      .mockImplementationOnce((_request, signal) => {
        firstSignal = signal;
        return new Promise(() => undefined);
      })
      .mockImplementationOnce(async (request) => response(request.requestId));
    const { rerender } = renderHook(
      ({ definition }) => useCanvasTaskCompilation({
        definition,
        metadataSnapshot: emptyMetadata,
        metadataLoading: false,
        metadataError: false,
      }),
      { initialProps: { definition: inputDefinition('orders') } },
    );

    await act(async () => vi.advanceTimersByTimeAsync(400));
    rerender({ definition: inputDefinition('customers') });

    expect(firstSignal?.aborted).toBe(true);
    expect(cancelTaskCompilation).toHaveBeenCalledWith('a39bb068-bbb9-40b0-8136-1b1adecc3953');
    await act(async () => vi.advanceTimersByTimeAsync(400));
    expect(compileCanvasTask).toHaveBeenCalledTimes(2);
  });

  it('ignores a response from an older aborted request', async () => {
    const requestIds = [
      'a39bb068-bbb9-40b0-8136-1b1adecc3953',
      'b30a90b3-dd8b-450e-a428-fb27aa079e65',
    ];
    vi.spyOn(globalThis.crypto, 'randomUUID').mockImplementation(() => requestIds.shift() as `${string}-${string}-${string}-${string}-${string}`);
    let resolveFirst: ((value: TaskCompilationResponse) => void) | undefined;
    let resolveSecond: ((value: TaskCompilationResponse) => void) | undefined;
    vi.mocked(compileCanvasTask)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveSecond = resolve; }));
    const { result, rerender } = renderHook(
      ({ definition }) => useCanvasTaskCompilation({
        definition,
        metadataSnapshot: emptyMetadata,
        metadataLoading: false,
        metadataError: false,
      }),
      { initialProps: { definition: inputDefinition('orders') } },
    );

    await act(async () => vi.advanceTimersByTimeAsync(400));
    rerender({ definition: inputDefinition('customers') });
    await act(async () => vi.advanceTimersByTimeAsync(400));
    await act(async () => resolveFirst?.(response('a39bb068-bbb9-40b0-8136-1b1adecc3953')));

    expect(result.current.status).toBe('COMPILING');
    expect(result.current.response).toBeNull();

    await act(async () => resolveSecond?.(response('b30a90b3-dd8b-450e-a428-fb27aa079e65')));
    expect(result.current.status).toBe('SUCCESS');
    expect(result.current.response?.requestId).toBe('b30a90b3-dd8b-450e-a428-fb27aa079e65');
  });

  it('waits for complete metadata and reports metadata failures without calling the engine', async () => {
    const { result, rerender } = renderHook(
      ({ loading, error }) => useCanvasTaskCompilation({
        definition: inputDefinition('orders'),
        metadataSnapshot: emptyMetadata,
        metadataLoading: loading,
        metadataError: error,
      }),
      { initialProps: { loading: true, error: false } },
    );

    expect(result.current.status).toBe('WAITING_METADATA');
    rerender({ loading: false, error: true });
    expect(result.current.status).toBe('METADATA_ERROR');
    await act(async () => vi.advanceTimersByTimeAsync(800));
    expect(compileCanvasTask).not.toHaveBeenCalled();
  });

  it('retries the same fingerprint after Task Engine becomes unavailable', async () => {
    vi.mocked(compileCanvasTask)
      .mockRejectedValueOnce(new Error('engine unavailable'))
      .mockImplementationOnce(async (request) => response(request.requestId));
    const { result } = renderHook(() => useCanvasTaskCompilation({
      definition: inputDefinition('orders'),
      metadataSnapshot: emptyMetadata,
      metadataLoading: false,
      metadataError: false,
    }));

    await act(async () => vi.advanceTimersByTimeAsync(400));
    expect(result.current.status).toBe('UNAVAILABLE');

    act(() => result.current.retry());
    expect(result.current.status).toBe('WAITING');
    await act(async () => vi.advanceTimersByTimeAsync(400));

    expect(compileCanvasTask).toHaveBeenCalledTimes(2);
    expect(result.current.status).toBe('SUCCESS');
  });
});
