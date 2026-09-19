import { createUuid } from '../../../shared/browser/createUuid';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { cancelTaskCompilation, compileCanvasTask } from '../api/taskCompilationApi';
import type { CanvasDefinition, CanvasExecutionMode } from './canvasTypes';
import type {
  CanvasTaskCompilationStatus,
  TaskCompilationMetadataSnapshot,
  TaskCompilationResponse,
} from './taskCompilationTypes';

const COMPILATION_DEBOUNCE_MS = 400;

interface ActiveCompilation {
  requestId: string;
  fingerprint: string;
  requestKey: string;
  controller: AbortController;
}

interface InternalCompilationState {
  status: CanvasTaskCompilationStatus;
  response: TaskCompilationResponse | null;
  error: unknown;
  fingerprint: string | null;
  requestKey: string | null;
}

export interface UseCanvasTaskCompilationOptions {
  definition: CanvasDefinition;
  metadataSnapshot: TaskCompilationMetadataSnapshot;
  metadataLoading: boolean;
  metadataError: boolean;
  validationRequestVersion?: number;
  executionMode?: CanvasExecutionMode;
}

export interface CanvasTaskCompilationState {
  status: CanvasTaskCompilationStatus;
  response: TaskCompilationResponse | null;
  error: unknown;
  retry: () => void;
}

export const isCanvasTaskCompilationBlocking = (status: CanvasTaskCompilationStatus): boolean => (
  status === 'WAITING_METADATA' || status === 'WAITING' || status === 'COMPILING'
);

export const canvasCompilationFingerprint = (
  definition: CanvasDefinition,
  metadataSnapshot: TaskCompilationMetadataSnapshot,
  executionMode: CanvasExecutionMode = 'BATCH',
): string => JSON.stringify({
  executionMode,
  schemaVersion: definition.schemaVersion,
  schemaMinorVersion: definition.schemaMinorVersion,
  nodes: definition.nodes.map((node) => ({
    id: node.id,
    type: node.type,
    name: node.name,
    configuration: node.configuration,
  })),
  edges: definition.edges.map((edge) => ({
    id: edge.id,
    sourceNodeId: edge.sourceNodeId,
    targetNodeId: edge.targetNodeId,
  })),
  metadataSnapshot,
});

const isAbortError = (error: unknown): boolean => (
  error instanceof DOMException && error.name === 'AbortError'
);

export const useCanvasTaskCompilation = ({
  definition,
  metadataSnapshot,
  metadataLoading,
  metadataError,
  validationRequestVersion = 0,
  executionMode = 'BATCH',
}: UseCanvasTaskCompilationOptions): CanvasTaskCompilationState => {
  const fingerprint = useMemo(
    () => canvasCompilationFingerprint(definition, metadataSnapshot, executionMode),
    [definition, executionMode, metadataSnapshot],
  );
  const requestKey = `${fingerprint}\u0000${validationRequestVersion}`;
  const activeRef = useRef<ActiveCompilation | null>(null);
  const inputRef = useRef({ definition, metadataSnapshot, executionMode });
  const settledRequestKeyRef = useRef<string | null>(null);
  const [initialInput] = useState(() => ({
    fingerprint,
    empty: definition.nodes.length === 0,
  }));
  const hasStartedRef = useRef(!initialInput.empty);
  const [retryVersion, setRetryVersion] = useState(0);
  const [state, setState] = useState<InternalCompilationState>({
    status: 'IDLE',
    response: null,
    error: null,
    fingerprint: null,
    requestKey: null,
  });

  useEffect(() => {
    inputRef.current = { definition, metadataSnapshot, executionMode };
  }, [definition, executionMode, metadataSnapshot]);

  useEffect(() => {
    const input = inputRef.current;
    if (fingerprint !== initialInput.fingerprint) hasStartedRef.current = true;
    const active = activeRef.current;
    if (active) {
      active.controller.abort();
      activeRef.current = null;
      void cancelTaskCompilation(active.requestId).catch(() => undefined);
    }

    if (!hasStartedRef.current) return undefined;
    if (metadataLoading || metadataError) return undefined;
    if (settledRequestKeyRef.current === requestKey) return undefined;

    const timeout = window.setTimeout(() => {
      const requestId = createUuid();
      const controller = new AbortController();
      const compilation = { requestId, fingerprint, requestKey, controller };
      activeRef.current = compilation;
      settledRequestKeyRef.current = null;
      setState({ status: 'COMPILING', response: null, error: null, fingerprint, requestKey });

      void compileCanvasTask({
        requestId,
        task: {
          type: 'CANVAS',
          definition: input.definition,
          executionMode: input.executionMode,
        },
        metadataSnapshot: input.metadataSnapshot,
      }, controller.signal).then((response) => {
        if (activeRef.current !== compilation
            || response.requestId !== requestId) return;
        settledRequestKeyRef.current = requestKey;
        setState({ status: 'SUCCESS', response, error: null, fingerprint, requestKey });
      }).catch((error: unknown) => {
        if (isAbortError(error) || activeRef.current !== compilation) return;
        settledRequestKeyRef.current = requestKey;
        setState({ status: 'UNAVAILABLE', response: null, error, fingerprint, requestKey });
      }).finally(() => {
        if (activeRef.current === compilation) activeRef.current = null;
      });
    }, COMPILATION_DEBOUNCE_MS);

    return () => window.clearTimeout(timeout);
  }, [fingerprint, initialInput, metadataError, metadataLoading, requestKey, retryVersion]);

  useEffect(() => () => {
    const active = activeRef.current;
    if (!active) return;
    active.controller.abort();
    activeRef.current = null;
    void cancelTaskCompilation(active.requestId).catch(() => undefined);
  }, []);

  const retry = useCallback(() => {
    settledRequestKeyRef.current = null;
    setState({ status: 'WAITING', response: null, error: null, fingerprint: null, requestKey: null });
    setRetryVersion((current) => current + 1);
  }, []);

  const eligible = !initialInput.empty
    || fingerprint !== initialInput.fingerprint
    || state.fingerprint !== null;
  if (!eligible) return { status: 'IDLE', response: null, error: null, retry };
  if (metadataLoading) return { status: 'WAITING_METADATA', response: null, error: null, retry };
  if (metadataError) return { status: 'METADATA_ERROR', response: null, error: null, retry };
  if (state.requestKey !== requestKey) return { status: 'WAITING', response: null, error: null, retry };
  return { status: state.status, response: state.response, error: state.error, retry };
};
