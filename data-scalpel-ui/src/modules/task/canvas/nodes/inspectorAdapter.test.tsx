import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createRef } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  CanvasNodeType,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
} from '../canvasTypes';
import { RenameInspector } from './rename/inspector';
import { TypeCastProcessorInspector } from '../components/processors/TypeCastProcessorInspector';
import { adaptCanvasNodeInspector } from './inspectorAdapter';
import type { CanvasNodeInspectorHandle } from './nodeSpec';

vi.mock('../../../model', () => ({}));

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', ResizeObserverStub);
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
afterEach(cleanup);

describe('multi-table processor inspector adapter', () => {
  it('keeps operation edits in the per-table modal until the outer inspector is applied', async () => {
    const Inspector = adaptCanvasNodeInspector(CanvasNodeType.Rename, RenameInspector);
    const operationId = 'f07be0e2-71f8-41dc-afc4-3f2816f30650';
    const node: Extract<CanvasNodeDefinition, { type: 'RENAME' }> = {
      id: '877e03b6-1e76-4712-8adb-2fd13e181265',
      type: CanvasNodeType.Rename,
      name: '字段重命名',
      layout: { x: 0, y: 0, width: 240, height: 120 },
      configuration: {
        operations: [{
          operationId,
          sourceTableName: 'orders',
          output: { mode: 'REPLACE_SOURCE', outputTableName: 'orders' },
          columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'source_order_id' }],
        }],
        sourceTableName: 'orders',
        outputTableName: 'orders',
        columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'source_order_id' }],
      },
    };
    const validation: CanvasNodeValidationResult = {
      nodeId: node.id,
      issues: [],
      inputTables: [{
        name: 'orders',
        origin: null,
        columns: [{
          name: 'order_id',
          fieldType: 'LONG',
          length: null,
          precision: null,
          scale: null,
          nullable: false,
          defaultValue: null,
          autoIncrement: false,
          generated: false,
          comment: null,
          geometry: null,
        }, {
          name: 'login_name',
          fieldType: 'STRING',
          length: 50,
          precision: null,
          scale: null,
          nullable: true,
          defaultValue: null,
          autoIncrement: false,
          generated: false,
          comment: '登录账号',
          geometry: null,
        }],
        datasetKind: 'BOUNDED',
        eventTimeColumn: null,
        watermarkDelay: null,
      }],
      outputTables: [],
    };
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();
    const onApply = vi.fn();

    render(<Inspector
      inspectorRef={inspectorRef}
      node={node}
      validation={validation}
      validationUnavailableMessage={null}
      executionMode="BATCH"
      onApply={onApply}
      onDirtyChange={vi.fn()}
    />);

    await waitFor(() => expect(screen.getByRole('button', { name: '配置 orders' })).toBeInTheDocument());
    expect(screen.queryByText('所有映射同时生效，支持 a→b、b→a 交换名称。')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '配置 orders' }));
    expect(await screen.findByText('配置处理表 · orders')).toBeInTheDocument();
    expect(screen.getAllByText('源字段名').length).toBeGreaterThan(0);
    expect(screen.getByDisplayValue('source_order_id')).toBeInTheDocument();
    expect(screen.getByDisplayValue('login_name')).toBeInTheDocument();
    expect(screen.getByText('登录账号')).toBeInTheDocument();

    fireEvent.change(screen.getByDisplayValue('source_order_id'), { target: { value: 'renamed_order_id' } });
    fireEvent.click(screen.getByRole('button', { name: '保存此项' }));
    await waitFor(() => expect(screen.queryByText('配置处理表 · orders')).not.toBeInTheDocument());
    expect(onApply).not.toHaveBeenCalled();

    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(onApply).toHaveBeenCalledWith({
      id: node.id,
      type: CanvasNodeType.Rename,
      configuration: {
        operations: [{
          operationId,
          sourceTableName: 'orders',
          output: { mode: 'REPLACE_SOURCE', outputTableName: 'orders' },
          columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'renamed_order_id' }],
        }],
      },
    });
  });

  it('keeps only the selected type-cast rule visible after reopening and child rerenders', async () => {
    const Inspector = adaptCanvasNodeInspector(CanvasNodeType.TypeCast, TypeCastProcessorInspector);
    const node = {
      id: '5fb4b33e-804f-47cb-87a5-3ab901dc3868',
      type: CanvasNodeType.TypeCast,
      name: '类型转换',
      layout: { x: 0, y: 0, width: 240, height: 120 },
      configuration: {
        operations: [{
          operationId: '70ebf41c-680e-4939-aad0-077f97be2fe8',
          sourceTableName: 'orders',
          output: { mode: 'REPLACE_SOURCE', outputTableName: null },
          casts: [{
            columnName: 'order_id',
            targetType: { type: 'STRING', length: 32, precision: null, scale: null },
            failureStrategy: 'FAIL',
          }, {
            columnName: 'customer_id',
            targetType: { type: 'STRING', length: 32, precision: null, scale: null },
            failureStrategy: 'FAIL',
          }],
        }],
      },
    } as Extract<CanvasNodeDefinition, { type: 'TYPE_CAST' }>;
    const validation: CanvasNodeValidationResult = {
      nodeId: node.id,
      issues: [],
      inputTables: [{
        name: 'orders',
        origin: null,
        columns: ['order_id', 'customer_id'].map((name) => ({
          name,
          fieldType: 'LONG' as const,
          length: null,
          precision: null,
          scale: null,
          nullable: false,
          defaultValue: null,
          autoIncrement: false,
          generated: false,
          comment: null,
          geometry: null,
        })),
        datasetKind: 'BOUNDED',
        eventTimeColumn: null,
        watermarkDelay: null,
      }],
      outputTables: [],
    };
    render(<Inspector
      inspectorRef={createRef<CanvasNodeInspectorHandle>()}
      node={node}
      validation={validation}
      validationUnavailableMessage={null}
      executionMode="BATCH"
      onApply={vi.fn()}
      onDirtyChange={vi.fn()}
    />);

    const openEditor = async () => {
      fireEvent.click(screen.getByRole('button', { name: '配置 orders' }));
      await screen.findByText('配置处理表 · orders');
      await waitFor(() => expect(document.querySelectorAll('.canvas-type-cast-item')).toHaveLength(2));
      const items = document.querySelectorAll<HTMLElement>('.canvas-type-cast-item');
      expect(items[0]).not.toHaveClass('is-rule-detail-hidden');
      expect(items[1]).toHaveClass('is-rule-detail-hidden');
      return items;
    };

    const firstOpenItems = await openEditor();
    firstOpenItems[1].classList.remove('is-rule-detail-hidden');
    await waitFor(() => expect(firstOpenItems[1]).toHaveClass('is-rule-detail-hidden'));
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    await waitFor(() => expect(screen.queryByText('配置处理表 · orders')).not.toBeInTheDocument());
    await openEditor();
  });
});
