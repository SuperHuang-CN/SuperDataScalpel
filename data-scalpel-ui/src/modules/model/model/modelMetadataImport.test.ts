import { describe, expect, it } from 'vitest';
import type { ModelMetadataImportPreview } from './dataModel';
import {
  hasModelMetadataDraftIssues,
  modelMetadataDraftIssues,
  modelMetadataDrafts,
  toManagedDraftRequest,
} from './modelMetadataImport';

const preview = (): ModelMetadataImportPreview => ({
  fileName: 'models.xlsx',
  formatVersion: 1,
  importable: true,
  issues: [],
  models: [{
    key: 'model:2', rowNumber: 2, code: 'orders', name: '订单模型', physicalTableName: 'orders',
    clickHouseOrderByColumns: [], description: '', importable: true, issues: [], warnings: [],
    fields: [{
      key: 'field:2', rowNumber: 2, code: 'order_id', name: '订单ID', fieldType: 'LONG',
      length: null, precision: null, scale: null, nullable: false, primaryKey: true,
      sortOrder: 10, description: '', importable: true, issues: [],
    }],
  }],
});

describe('model metadata Excel import', () => {
  it('creates a fixed managed draft request without environment bindings', () => {
    const draft = modelMetadataDrafts(preview())[0];
    const request = toManagedDraftRequest(draft, 'storage-id', 'directory-id');
    expect(request).toMatchObject({
      code: 'orders', storageDataSourceId: 'storage-id', directoryId: 'directory-id',
      physicalTableName: 'orders', fields: [{ code: 'order_id', fieldType: 'LONG', primaryKey: true }],
    });
    expect(request).not.toHaveProperty('physicalTableMode');
    expect(request).not.toHaveProperty('status');
  });

  it('blocks server issues, duplicate codes and invalid target fields', () => {
    const drafts = modelMetadataDrafts(preview());
    drafts[0].serverIssues = ['模型编码已存在'];
    drafts.push({ ...drafts[0], key: 'model:3', serverIssues: [], fields: drafts[0].fields.map((field) => ({ ...field, key: 'field:3' })) });
    const issues = modelMetadataDraftIssues(drafts, false);
    expect(hasModelMetadataDraftIssues(issues)).toBe(true);
    expect(issues.get('model:2')?.server).toContain('模型编码已存在');
    expect(issues.get('model:3')?.code).toContain('重复');
  });

  it('validates ClickHouse order keys against imported fields', () => {
    const draft = modelMetadataDrafts(preview())[0];
    draft.clickHouseOrderByColumns = ['missing_field'];
    expect(modelMetadataDraftIssues([draft], true).get(draft.key)?.clickHouseOrderByColumns).toContain('不存在');
  });

  it('keeps missing boolean metadata unresolved until the user chooses it', () => {
    const source = preview();
    source.models[0].fields[0].nullable = null;
    source.models[0].fields[0].primaryKey = null;

    const draft = modelMetadataDrafts(source)[0];

    expect(draft.fields[0].nullable).toBeNull();
    expect(draft.fields[0].primaryKey).toBeNull();
    expect(toManagedDraftRequest(draft, 'storage-id')).toBeUndefined();
  });

  it('preserves Geometry from a V2 metadata preview', () => {
    const source = preview();
    const geometry = {
      kind: 'POINT' as const,
      crs: { authority: 'EPSG' as const, code: 4326 },
      dimension: 'XY' as const,
    };
    source.formatVersion = 2;
    source.models[0].fields[0] = {
      ...source.models[0].fields[0],
      code: 'shape',
      name: '空间位置',
      fieldType: 'GEOMETRY',
      geometry,
      nullable: true,
      primaryKey: true,
    };

    const draft = modelMetadataDrafts(source)[0];
    expect(draft.fields[0]).toMatchObject({ fieldType: 'GEOMETRY', geometry, primaryKey: false });
    expect(toManagedDraftRequest(draft, 'storage-id')?.fields[0])
      .toMatchObject({ fieldType: 'GEOMETRY', geometry, primaryKey: false });
  });
});
