import type { KeyboardEvent, PointerEvent as ReactPointerEvent } from 'react';
import { useEffect, useRef, useState } from 'react';
import { Tooltip } from 'antd';
import { dataModelFieldTypeLabels, type PlatformDataType } from '../model/dataModel';
import { normalizeDataModelPreviewManualWidth } from '../model/dataModelPreviewColumnSizing';

interface DataModelPreviewColumnTitleProps {
  code: string;
  name: string;
  fieldType: PlatformDataType;
  width?: number;
  onWidthChange?: (width: number) => void;
  onWidthReset?: () => void;
}

export const DataModelPreviewColumnTitle = ({
  code,
  name,
  fieldType,
  width,
  onWidthChange,
  onWidthReset,
}: DataModelPreviewColumnTitleProps) => {
  const typeLabel = dataModelFieldTypeLabels[fieldType];
  const [resizing, setResizing] = useState(false);
  const dragStart = useRef<{ pointerId: number; clientX: number; width: number } | undefined>(undefined);
  const resizable = width !== undefined && onWidthChange !== undefined;

  const finishResize = () => {
    dragStart.current = undefined;
    setResizing(false);
    document.body.classList.remove('model-preview-column-resizing');
  };

  useEffect(() => () => {
    document.body.classList.remove('model-preview-column-resizing');
  }, []);

  const startResize = (event: ReactPointerEvent<HTMLSpanElement>) => {
    if (width === undefined || !onWidthChange || event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();
    dragStart.current = { pointerId: event.pointerId, clientX: event.clientX, width };
    event.currentTarget.setPointerCapture?.(event.pointerId);
    document.body.classList.add('model-preview-column-resizing');
    setResizing(true);
  };

  const resize = (event: ReactPointerEvent<HTMLSpanElement>) => {
    const start = dragStart.current;
    if (!start || start.pointerId !== event.pointerId || !onWidthChange) return;
    onWidthChange(normalizeDataModelPreviewManualWidth(start.width + event.clientX - start.clientX));
  };

  const stopResize = (event: ReactPointerEvent<HTMLSpanElement>) => {
    if (dragStart.current?.pointerId !== event.pointerId) return;
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture?.(event.pointerId);
    }
    finishResize();
  };

  const resizeWithKeyboard = (event: KeyboardEvent<HTMLSpanElement>) => {
    if (width === undefined || !onWidthChange || (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight')) return;
    event.preventDefault();
    event.stopPropagation();
    const step = event.shiftKey ? 32 : 8;
    onWidthChange(normalizeDataModelPreviewManualWidth(width + (event.key === 'ArrowRight' ? step : -step)));
  };

  return (
    <Tooltip
      title={(
        <div>
          <div>{name}</div>
          <div>{code} · {typeLabel}</div>
        </div>
      )}
    >
      <div className="model-preview-column-title" aria-label={`${name}，${typeLabel}`}>
        <span className="model-preview-column-name">{name}</span>
        <span className="model-preview-column-type">{typeLabel}</span>
        {resizable && (
          <span
            className={`model-preview-column-resize-handle${resizing ? ' is-resizing' : ''}`}
            role="separator"
            aria-label={`调整字段 ${name} 的列宽`}
            aria-orientation="vertical"
            aria-valuemin={80}
            aria-valuenow={Math.round(width)}
            tabIndex={0}
            title="拖动调整列宽，双击恢复自动宽度"
            onDoubleClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              onWidthReset?.();
            }}
            onKeyDown={resizeWithKeyboard}
            onLostPointerCapture={stopResize}
            onPointerCancel={stopResize}
            onPointerDown={startResize}
            onPointerMove={resize}
            onPointerUp={stopResize}
          />
        )}
      </div>
    </Tooltip>
  );
};
