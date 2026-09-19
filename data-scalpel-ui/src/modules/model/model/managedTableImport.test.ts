import { describe, expect, it } from 'vitest';
import type { DataSource, DataSourceTable } from '../../datasource';
import type { ManagedImportPreview } from './dataModel';
import {
  applyManagedDraftWarehouseLayer,
  applyManagedImportPreview,
  createManagedTableModelDrafts,
  hasManagedTableDraftIssues,
  isManagedImportSourceSelectable,
  isManagedImportTargetSelectable,
  managedTableDraftIssues,
  mergeManagedTableModelDrafts,
  runManagedImportTasks,
  toManagedDataModelDraftRequest,
} from './managedTableImport';

const table = (name: string, comment: string | null = null): DataSourceTable => ({
  identifier: { catalog: 'warehouse', schema: 'public', table: name },
  type: 'TABLE',
  comment,
});

const dataSource = (
  connectionKind: DataSource['connectionKind'],
  purposes: DataSource['purposes'],
  enabled = true,
): Pick<DataSource, 'purposes' | 'connectionKind' | 'enabled'> => ({ connectionKind, purposes, enabled });

const preview = (overrides: Partial<ManagedImportPreview> = {}): ManagedImportPreview => ({
  sourceTable: table('Fact_Order').identifier,
  suggestedCode: 'fact_order',
  suggestedName: '订单事实表',
  suggestedPhysicalTableName: 'fact_order',
  tableImportable: true,
  importable: true,
  columns: [{
    sourceName: 'Order_ID',
    nativeType: 'bigint',
    code: 'order_id',
    name: '订单ID',
    fieldType: 'LONG',
    length: null,
    precision: null,
    scale: null,
    nullable: false,
    primaryKey: true,
    sortOrder: 10,
    description: '主键',
    mappingQuality: 'EXACT',
    mappingMessage: null,
    importable: true,
    issues: [],
  }],
  tableIssues: [],
  issues: [],
  warnings: [],
  ...overrides,
});

describe('managed table model import', () => {
  it('allows every enabled JDBC source but only STORAGE JDBC targets', () => {
    const sourceJdbc = dataSource('JDBC', ['SOURCE']);
    const distributionJdbc = dataSource('JDBC', ['DISTRIBUTION']);
    const storageJdbc = dataSource('JDBC', ['STORAGE']);
    const kafka = dataSource('KAFKA', ['SOURCE']);

    expect(isManagedImportSourceSelectable(sourceJdbc)).toBe(true);
    expect(isManagedImportSourceSelectable(distributionJdbc)).toBe(true);
    expect(isManagedImportTargetSelectable(sourceJdbc)).toBe(false);
    expect(isManagedImportTargetSelectable(storageJdbc)).toBe(true);
    expect(isManagedImportSourceSelectable(kafka)).toBe(false);
    expect(isManagedImportSourceSelectable(dataSource('JDBC', ['SOURCE'], false))).toBe(false);
  });

  it('lowercases valid suggestions and leaves invalid or colliding identifiers blank', () => {
    const drafts = createManagedTableModelDrafts([table('Fact_Order', '订单事实表'), table('fact_order')]);
    expect(drafts[0].code).toBe('');
    expect(drafts[1].code).toBe('');
    expect(drafts[0].physicalTableName).toBe('fact_order');
    expect(drafts[0].name).toBe('订单事实表');
    expect(managedTableDraftIssues(drafts).get(drafts[0].key)?.physicalTableName).toBe('本次导入中目标物理表名重复');

    const invalid = createManagedTableModelDrafts([table('Order-Detail')])[0];
    expect(invalid.code).toBe('');
    expect(invalid.physicalTableName).toBe('');

    const previewedDuplicates = drafts.map((draft) => applyManagedImportPreview(draft, preview()));
    expect(previewedDuplicates.map((draft) => draft.code)).toEqual(['', '']);
  });

  it('fills editable field candidates and leaves unsupported mappings unresolved', () => {
    const draft = createManagedTableModelDrafts([table('Fact_Order')])[0];
    const mapped = applyManagedImportPreview(draft, preview({
      columns: [
        preview().columns[0],
        {
          ...preview().columns[0],
          sourceName: 'Payload',
          nativeType: 'jsonb',
          code: 'payload',
          name: '报文',
          fieldType: null,
          primaryKey: false,
          nullable: true,
          sortOrder: 20,
          mappingQuality: 'UNSUPPORTED',
          mappingMessage: '目标数据库不支持该映射',
          importable: false,
          issues: ['请选择目标字段类型'],
        },
      ],
    }));

    expect(mapped.code).toBe('fact_order');
    expect(mapped.fields[0]).toMatchObject({ code: 'order_id', fieldType: 'LONG', primaryKey: true, nullable: false });
    expect(mapped.fields[1]).toMatchObject({ code: 'payload', fieldType: null });
    expect(managedTableDraftIssues([mapped]).get(mapped.key)?.fields.get(mapped.fields[1].key)).toContain('请选择目标字段类型');
  });

  it('does not overwrite reviewed model values when structure is refreshed for another target', () => {
    const initial = applyManagedImportPreview(
      createManagedTableModelDrafts([table('Fact_Order')])[0],
      preview(),
    );
    const reviewed = {
      ...initial,
      code: 'reviewed_order',
      name: '人工确认名称',
      physicalTableName: 'reviewed_order_table',
    };
    const refreshed = applyManagedImportPreview(reviewed, preview({
      suggestedCode: 'other_suggestion',
      suggestedName: '其他建议',
      suggestedPhysicalTableName: 'other_table',
    }));

    expect(refreshed).toMatchObject({
      code: 'reviewed_order',
      name: '人工确认名称',
      physicalTableName: 'reviewed_order_table',
    });
  });

  it('preserves reviewed drafts when returning to source selection and adds only newly selected tables', () => {
    const initial = applyManagedImportPreview(
      createManagedTableModelDrafts([table('Fact_Order')])[0],
      preview(),
    );
    const reviewed = { ...initial, code: 'reviewed_order' };
    const merged = mergeManagedTableModelDrafts([table('Fact_Order'), table('dim_customer')], [reviewed]);

    expect(merged[0]).toBe(reviewed);
    expect(merged[1]).toMatchObject({ code: 'dim_customer', previewState: 'idle' });
  });

  it('blocks a non-table source even when its columns are otherwise valid', () => {
    const draft = applyManagedImportPreview(
      createManagedTableModelDrafts([table('source_view')])[0],
      preview({
        tableImportable: false,
        importable: false,
        tableIssues: ['只能导入普通物理表，当前对象类型为：VIEW'],
        issues: ['只能导入普通物理表，当前对象类型为：VIEW'],
      }),
    );

    expect(managedTableDraftIssues([draft]).get(draft.key)?.preview).toContain('只能导入普通物理表');
  });

  it('blocks duplicate field codes after lowercase normalization', () => {
    const draft = createManagedTableModelDrafts([table('Fact_Order')])[0];
    const mapped = applyManagedImportPreview(draft, preview({
      columns: [
        { ...preview().columns[0], sourceName: 'Order_ID', code: 'Order_ID' },
        { ...preview().columns[0], sourceName: 'order_id', code: 'order_id', sortOrder: 20 },
      ],
    }));

    expect(mapped.fields.map((field) => field.code)).toEqual(['', '']);
    expect(hasManagedTableDraftIssues(managedTableDraftIssues([mapped]))).toBe(true);
  });

  it('keeps a safely mapped type when only the source field identifier needs correction', () => {
    const draft = createManagedTableModelDrafts([table('Fact_Order')])[0];
    const mapped = applyManagedImportPreview(draft, preview({
      columns: [{
        ...preview().columns[0],
        sourceName: 'Order-ID',
        code: '',
        fieldType: 'LONG',
        importable: false,
        issues: ['字段名小写化后仍不符合模型编码规则：Order-ID'],
      }],
    }));

    expect(mapped.fields[0]).toMatchObject({ code: '', fieldType: 'LONG' });
    expect(managedTableDraftIssues([mapped]).get(mapped.key)?.fields.get(mapped.fields[0].key)).toEqual([
      '请填写字段编码',
    ]);
  });

  it('creates a managed draft request with fields and no external binding mode', () => {
    const draft = applyManagedImportPreview(
      createManagedTableModelDrafts([table('Fact_Order')])[0],
      preview(),
    );
    const request = toManagedDataModelDraftRequest(draft, 'storage-id', 'directory-id');

    expect(request).toMatchObject({
      code: 'fact_order',
      name: '订单事实表',
      directoryId: 'directory-id',
      storageDataSourceId: 'storage-id',
      physicalTableName: 'fact_order',
      clickHouseOrderByColumns: [],
      fields: [{ code: 'order_id', fieldType: 'LONG', primaryKey: true }],
    });
    expect(request).not.toHaveProperty('physicalTableMode');
    expect(request).not.toHaveProperty('status');
  });

  it('initializes JDBC import drafts with the optional batch warehouse layer', () => {
    const draft = applyManagedImportPreview(
      createManagedTableModelDrafts([table('Fact_Order')], 'layer-id')[0],
      preview(),
    );

    expect(draft.warehouseLayerId).toBe('layer-id');
    expect(toManagedDataModelDraftRequest(draft, 'storage-id')).toMatchObject({
      warehouseLayerId: 'layer-id',
    });
  });

  it('uses the selected layer prefix without duplicating it and preserves manual model codes', () => {
    const prefixed = createManagedTableModelDrafts(
      [table('Fact_Order'), table('dwd_customer')],
      'dwd-layer',
      'dwd_',
    );
    expect(prefixed.map((draft) => draft.code)).toEqual(['dwd_fact_order', 'dwd_customer']);

    const switched = applyManagedDraftWarehouseLayer(prefixed[0], 'dim-layer', 'dim_', true);
    expect(switched).toMatchObject({
      code: 'dim_fact_order',
      warehouseLayerId: 'dim-layer',
      warehouseLayerOverridden: true,
    });

    const manual = {
      ...switched,
      code: 'custom_order',
      codeOverridden: true,
    };
    expect(applyManagedDraftWarehouseLayer(manual, 'ads-layer', 'ads_', true).code).toBe('custom_order');
  });

  it('preserves an exact Geometry definition and prevents a spatial primary key', () => {
    const geometry = {
      kind: 'MULTIPOLYGON' as const,
      crs: { authority: 'EPSG' as const, code: 4490 },
      dimension: 'XY' as const,
    };
    const draft = applyManagedImportPreview(
      createManagedTableModelDrafts([table('spatial_asset')])[0],
      preview({
        suggestedCode: 'spatial_asset',
        suggestedName: '空间资产',
        suggestedPhysicalTableName: 'spatial_asset',
        columns: [{
          ...preview().columns[0],
          sourceName: 'shape',
          code: 'shape',
          name: '空间范围',
          fieldType: 'GEOMETRY',
          geometry,
          nullable: true,
          primaryKey: true,
        }],
      }),
    );

    expect(draft.fields[0]).toMatchObject({ fieldType: 'GEOMETRY', geometry, primaryKey: false });
    expect(toManagedDataModelDraftRequest(draft, 'storage-id')?.fields[0])
      .toMatchObject({ fieldType: 'GEOMETRY', geometry, primaryKey: false });
  });

  it('runs batch work with at most three concurrent tasks and preserves partial results', async () => {
    let active = 0;
    let maximumActive = 0;
    const results = await runManagedImportTasks([1, 2, 3, 4, 5, 6], async (item) => {
      active += 1;
      maximumActive = Math.max(maximumActive, active);
      await Promise.resolve();
      active -= 1;
      if (item === 4) throw new Error('failed');
      return item * 10;
    });

    expect(maximumActive).toBe(3);
    expect(results.map((result) => result.result)).toEqual([10, 20, 30, undefined, 50, 60]);
    expect(results[3].error).toBeInstanceOf(Error);
  });
});
