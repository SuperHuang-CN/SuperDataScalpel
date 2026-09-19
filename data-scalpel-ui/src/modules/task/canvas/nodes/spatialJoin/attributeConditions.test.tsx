import { act, cleanup, render, screen } from '@testing-library/react';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasNodeValidationResult,
  type SpatialJoinConfiguration,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';

afterEach(cleanup);

const nodeId = '11111111-1111-4111-8111-111111111111';
const layout = { x: 0, y: 0, width: 368, height: 224 };
const scalar = (name: string): CanvasColumnSchema => ({
  name,
  fieldType: 'STRING',
  length: 64,
  precision: null,
  scale: null,
  nullable: true,
  defaultValue: null,
  autoIncrement: false,
  generated: false,
  comment: null,
  geometry: null,
});
const geometry = (name: string): CanvasColumnSchema => ({
  ...scalar(name),
  fieldType: 'GEOMETRY',
  length: null,
  geometry: {
    kind: 'POINT',
    crs: { authority: 'EPSG', code: 4326 },
    dimension: 'XY',
  },
});

describe('Spatial Join attribute conditions', () => {
  it('shows compact attribute matches and preserves them when applying the draft', async () => {
    const configuration: SpatialJoinConfiguration = {
      leftTableName: 'orders',
      rightTableName: 'districts',
      outputTableName: 'orders_with_district',
      joinType: 'LEFT',
      conditions: [{
        leftGeometryColumnName: 'shape',
        predicate: 'WITHIN',
        rightGeometryColumnName: 'boundary',
      }],
      attributeConditions: [{
        leftColumnName: 'tenant_id',
        operator: 'EQUALS',
        rightColumnName: 'tenant_id',
      }],
      outputColumns: null,
      joinOperation: 'JOIN_ONE_TO_MANY',
    };
    const validation: CanvasNodeValidationResult = {
      nodeId,
      issues: [],
      outputTables: [],
      inputTables: [
        {
          name: 'orders',
          origin: null,
          columns: [scalar('tenant_id'), geometry('shape')],
          datasetKind: 'BOUNDED',
          eventTimeColumn: null,
          watermarkDelay: null,
        },
        {
          name: 'districts',
          origin: null,
          columns: [scalar('tenant_id'), geometry('boundary')],
          datasetKind: 'BOUNDED',
          eventTimeColumn: null,
          watermarkDelay: null,
        },
      ],
    };
    const apply = vi.fn();
    const inspectorRef = createRef<CanvasNodeInspectorHandle>();

    render(<Inspector
      node={{ id: nodeId, type: CanvasNodeType.SpatialJoin, name: '空间连接', layout, configuration }}
      executionMode="BATCH"
      validation={validation}
      validationUnavailableMessage={null}
      onApply={apply}
      onDirtyChange={vi.fn()}
      inspectorRef={inspectorRef}
    />);

    expect(screen.getByText('属性匹配')).toBeTruthy();
    expect(screen.getByText('1 项')).toBeTruthy();
    expect(screen.getByText('保留全部目标要素（LEFT）')).toBeTruthy();
    expect(screen.getByText('一对多 · 保留全部匹配组合')).toBeTruthy();
    expect(screen.getByRole('button', { name: '删除属性匹配条件 1' })).toBeTruthy();
    await act(async () => {
      expect(await inspectorRef.current?.apply()).toBe(true);
    });
    expect(apply.mock.calls[0][0].configuration.attributeConditions).toEqual(
      configuration.attributeConditions,
    );
    expect(apply.mock.calls[0][0].configuration.joinType).toBe('LEFT');
    expect(apply.mock.calls[0][0].configuration.joinOperation).toBe('JOIN_ONE_TO_MANY');
  });
});
