import { describe, expect, it } from 'vitest';
import {
  canSaveFieldsDirectly,
  isMetadataOnlyFieldUpdate,
  physicalTableChangeStatusLabels,
  shouldCreatePhysicalTableChangePlan,
  tableChangeRiskLabels,
  tableChangeStrategyLabels,
} from './dataModel';

describe('physical table change presentation rules', () => {
  it('requires a change plan only for a matched managed physical table', () => {
    expect(shouldCreatePhysicalTableChangePlan('MANAGED', 'MATCHED')).toBe(true);
    expect(shouldCreatePhysicalTableChangePlan('MANAGED', 'NOT_FOUND')).toBe(false);
    expect(shouldCreatePhysicalTableChangePlan('EXTERNAL', 'MATCHED')).toBe(false);
  });

  it('allows direct field saving only before a managed physical table exists', () => {
    expect(canSaveFieldsDirectly('MANAGED', 'NOT_FOUND')).toBe(true);
    expect(canSaveFieldsDirectly('MANAGED', 'MATCHED')).toBe(false);
    expect(canSaveFieldsDirectly('MANAGED', 'DRIFTED')).toBe(false);
    expect(canSaveFieldsDirectly('EXTERNAL', 'MATCHED')).toBe(true);
  });

  it('distinguishes metadata-only field edits from physical structure changes', () => {
    const current = [{
      id: 'field-id',
      code: 'order_id',
      name: '订单ID',
      fieldType: 'LONG' as const,
      nullable: false,
      primaryKey: true,
      sortOrder: 10,
      description: '原说明',
    }];

    expect(isMetadataOnlyFieldUpdate(current, [{
      ...current[0],
      name: '订单主键',
      sortOrder: 20,
      description: '新说明',
    }])).toBe(true);
    expect(isMetadataOnlyFieldUpdate(current, [{
      ...current[0],
      nullable: true,
    }])).toBe(false);
    expect(isMetadataOnlyFieldUpdate(current, [...current, {
      ...current[0],
      id: undefined,
      code: 'remark',
      primaryKey: false,
    }])).toBe(false);
  });

  it('provides stable Chinese labels for plan status, strategy and risk', () => {
    expect(physicalTableChangeStatusLabels.PARTIAL).toBe('部分完成');
    expect(tableChangeStrategyLabels.REBUILD_REQUIRED).toBe('必须重建表');
    expect(tableChangeRiskLabels.DESTRUCTIVE).toBe('破坏性');
  });
});
