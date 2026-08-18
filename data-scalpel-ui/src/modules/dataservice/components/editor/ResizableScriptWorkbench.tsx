import {
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type PointerEvent,
  type ReactNode,
} from 'react';

interface ResizableScriptWorkbenchProps {
  children: ReactNode;
}

const RESULT_HEIGHT_STORAGE_KEY = 'data-scalpel.ui.script-workbench.result-height';
const RESULT_HEIGHT_DEFAULT = 210;
const RESULT_HEIGHT_MIN = 120;
const RESULT_HEIGHT_MAX = 600;
const SCRIPT_WORKBENCH_UPPER_MIN_HEIGHT = 260;
const KEYBOARD_RESIZE_STEP = 16;

const readResultHeight = () => {
  try {
    const stored = Number(window.localStorage.getItem(RESULT_HEIGHT_STORAGE_KEY));
    if (Number.isFinite(stored) && stored > 0) {
      return Math.min(RESULT_HEIGHT_MAX, Math.max(RESULT_HEIGHT_MIN, stored));
    }
  } catch {
    // Local storage may be unavailable in restricted browser environments.
  }
  return RESULT_HEIGHT_DEFAULT;
};

const writeResultHeight = (height: number) => {
  try {
    window.localStorage.setItem(RESULT_HEIGHT_STORAGE_KEY, String(Math.round(height)));
  } catch {
    // Local storage may be unavailable in restricted browser environments.
  }
};

export const ResizableScriptWorkbench = ({ children }: ResizableScriptWorkbenchProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const resizeStateRef = useRef<{
    pointerId: number;
    startY: number;
    startHeight: number;
    currentHeight: number;
  } | null>(null);
  const [resultHeight, setResultHeight] = useState(readResultHeight);
  const [availableMaximum, setAvailableMaximum] = useState(RESULT_HEIGHT_MAX);
  const [resizing, setResizing] = useState(false);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || typeof ResizeObserver === 'undefined') return undefined;
    const updateAvailableHeight = () => {
      const containerHeight = container.getBoundingClientRect().height;
      if (containerHeight <= 0) return;
      const nextMaximum = Math.max(
        RESULT_HEIGHT_MIN,
        Math.min(RESULT_HEIGHT_MAX, containerHeight - SCRIPT_WORKBENCH_UPPER_MIN_HEIGHT),
      );
      setAvailableMaximum(nextMaximum);
      setResultHeight((currentHeight) => Math.min(nextMaximum, Math.max(RESULT_HEIGHT_MIN, currentHeight)));
    };
    const observer = new ResizeObserver(updateAvailableHeight);
    observer.observe(container);
    updateAvailableHeight();
    return () => observer.disconnect();
  }, []);

  const maximumResultHeight = () => availableMaximum;

  const boundedHeight = (height: number) => Math.round(Math.min(
    maximumResultHeight(),
    Math.max(RESULT_HEIGHT_MIN, height),
  ));

  const updateHeight = (height: number, persist = false) => {
    const nextHeight = boundedHeight(height);
    setResultHeight(nextHeight);
    if (persist) writeResultHeight(nextHeight);
    return nextHeight;
  };

  const startResize = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    resizeStateRef.current = {
      pointerId: event.pointerId,
      startY: event.clientY,
      startHeight: resultHeight,
      currentHeight: resultHeight,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
    setResizing(true);
    event.preventDefault();
  };

  const resize = (event: PointerEvent<HTMLDivElement>) => {
    const resizeState = resizeStateRef.current;
    if (!resizeState || resizeState.pointerId !== event.pointerId) return;
    const nextHeight = updateHeight(resizeState.startHeight + resizeState.startY - event.clientY);
    resizeState.currentHeight = nextHeight;
  };

  const finishResize = (event: PointerEvent<HTMLDivElement>) => {
    const resizeState = resizeStateRef.current;
    if (!resizeState || resizeState.pointerId !== event.pointerId) return;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    writeResultHeight(resizeState.currentHeight);
    resizeStateRef.current = null;
    setResizing(false);
  };

  const resizeWithKeyboard = (event: KeyboardEvent<HTMLDivElement>) => {
    let nextHeight: number | undefined;
    if (event.key === 'ArrowUp') nextHeight = resultHeight + KEYBOARD_RESIZE_STEP;
    if (event.key === 'ArrowDown') nextHeight = resultHeight - KEYBOARD_RESIZE_STEP;
    if (event.key === 'Home') nextHeight = RESULT_HEIGHT_MIN;
    if (event.key === 'End') nextHeight = maximumResultHeight();
    if (nextHeight === undefined) return;
    event.preventDefault();
    updateHeight(nextHeight, true);
  };

  return (
    <div
      ref={containerRef}
      className={`data-service-script-resizable-workbench${resizing ? ' data-service-script-resizable-workbench-resizing' : ''}`}
      style={{ '--data-service-script-result-height': `${resultHeight}px` } as CSSProperties}
    >
      {children}
      <div
        className="data-service-script-horizontal-resizer"
        role="separator"
        aria-label="调整脚本编辑器和执行结果高度"
        aria-orientation="horizontal"
        aria-valuemin={RESULT_HEIGHT_MIN}
        aria-valuemax={availableMaximum}
        aria-valuenow={resultHeight}
        tabIndex={0}
        title="上下拖动调整高度，双击恢复默认高度"
        onDoubleClick={() => updateHeight(RESULT_HEIGHT_DEFAULT, true)}
        onKeyDown={resizeWithKeyboard}
        onPointerDown={startResize}
        onPointerMove={resize}
        onPointerUp={finishResize}
        onPointerCancel={finishResize}
      />
    </div>
  );
};
