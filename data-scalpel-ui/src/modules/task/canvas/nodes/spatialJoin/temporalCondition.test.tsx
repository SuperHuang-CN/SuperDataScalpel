import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  CanvasColumnSchema,
  CanvasNodeValidationResult,
  SpatialJoinTemporalCondition,
} from '../../canvasTypes';
import { TemporalConditionModal } from './TemporalConditionModal';
import {
  createSpatialJoinTemporalCondition,
  isSpatialJoinTemporalNear,
  spatialJoinTemporalSummary,
} from './temporalCondition';

afterEach(() => {
  Modal.destroyAll();
  cleanup();
});

type CanvasTable = CanvasNodeValidationResult['inputTables'][number];

const column = (
  name: string,
  fieldType: CanvasColumnSchema['fieldType'],
): CanvasColumnSchema => ({
  name,
  fieldType,
  length: null,
  precision: null,
  scale: null,
  nullable: true,
  defaultValue: null,
  autoIncrement: false,
  generated: false,
  comment: null,
  geometry: null,
});

const table = (name: string, columns: CanvasColumnSchema[]): CanvasTable => ({
  name,
  origin: null,
  columns,
  datasetKind: 'BOUNDED',
  eventTimeColumn: null,
  watermarkDelay: null,
});

const leftTable = table('targets', [
  column('target_start', 'TIMESTAMP'),
  column('target_end', 'TIMESTAMP'),
  column('not_time', 'STRING'),
]);
const rightTable = table('joins', [
  column('join_time', 'TIMESTAMP'),
  column('join_end', 'TIMESTAMP'),
]);

const configured: SpatialJoinTemporalCondition = {
  relationship: 'NEAR_BEFORE',
  leftStartColumnName: 'stale_target_start',
  leftEndColumnName: null,
  rightStartColumnName: 'join_time',
  rightEndColumnName: 'join_end',
  nearDistance: 15,
  nearDistanceUnit: 'MINUTES',
};

describe('Spatial Join temporal condition', () => {
  it('creates a compact instant default and summarizes directional ranges', () => {
    expect(createSpatialJoinTemporalCondition('target_start', 'join_time')).toEqual({
      relationship: 'INTERSECTS',
      leftStartColumnName: 'target_start',
      leftEndColumnName: null,
      rightStartColumnName: 'join_time',
      rightEndColumnName: null,
      nearDistance: 1,
      nearDistanceUnit: 'MINUTES',
    });
    expect(spatialJoinTemporalSummary(configured)).toBe(
      'NEAR_BEFORE · stale_target_start ↔ join_time～join_end · 15 MINUTES',
    );
    expect(['NEAR', 'NEAR_BEFORE', 'NEAR_AFTER'].every(isSpatialJoinTemporalNear)).toBe(true);
    expect(isSpatialJoinTemporalNear('INTERSECTS')).toBe(false);
  });

  it('keeps stale fields visible and saves an invalid near draft without blocking', async () => {
    const onSave = vi.fn();
    render(<TemporalConditionModal
      open
      value={configured}
      leftTable={leftTable}
      rightTable={rightTable}
      onCancel={vi.fn()}
      onRemove={vi.fn()}
      onSave={onSave}
    />);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('button', {
      name: '时间关系与空间、属性条件按 AND 组合',
    })).toBeTruthy();
    expect(within(dialog).getByText(/stale_target_start/)).toBeTruthy();
    expect(within(dialog).queryByText('not_time')).toBeNull();

    const distance = within(dialog).getByRole('spinbutton');
    await userEvent.clear(distance);
    await userEvent.click(within(dialog).getByRole('button', { name: '保存配置' }));

    expect(onSave).toHaveBeenCalledOnce();
    expect(onSave.mock.calls[0][0]).toMatchObject({
      relationship: 'NEAR_BEFORE',
      leftStartColumnName: 'stale_target_start',
      nearDistance: null,
      nearDistanceUnit: 'MINUTES',
    });
  });

  it('can remove an existing temporal condition', async () => {
    const onRemove = vi.fn();
    render(<TemporalConditionModal
      open
      value={configured}
      leftTable={leftTable}
      rightTable={rightTable}
      onCancel={vi.fn()}
      onRemove={onRemove}
      onSave={vi.fn()}
    />);

    await userEvent.click(await screen.findByRole('button', { name: '移除时间关系' }));

    expect(onRemove).toHaveBeenCalledOnce();
  });
});
