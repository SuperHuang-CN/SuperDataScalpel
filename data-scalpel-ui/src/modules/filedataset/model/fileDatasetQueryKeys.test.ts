import { describe, expect, it } from 'vitest';
import { fileDatasetQueryKeys } from './fileDatasetQueryKeys';

describe('file dataset query keys', () => {
  it('separates datasets, files, tables, schemas and previews by their real owner IDs', () => {
    const request = { page: 0, size: 20, sort: '-updatedAt' };
    expect(fileDatasetQueryKeys.list(request)).toEqual(['file-datasets', 'list', request]);
    expect(fileDatasetQueryKeys.dataset('dataset-a')).toEqual(['file-datasets', 'dataset-a']);
    expect(fileDatasetQueryKeys.files('dataset-a')).toEqual(['file-datasets', 'dataset-a', 'files']);
    expect(fileDatasetQueryKeys.tables('dataset-a')).toEqual(['file-datasets', 'dataset-a', 'tables']);
    expect(fileDatasetQueryKeys.table('dataset-a', 'table-1')).toEqual([
      'file-datasets', 'dataset-a', 'tables', 'table-1',
    ]);
    expect(fileDatasetQueryKeys.schema('dataset-a', 'table-1')).not.toEqual(
      fileDatasetQueryKeys.schema('dataset-a', 'table-2'),
    );
    expect(fileDatasetQueryKeys.preview('dataset-a', 'table-1', 50)).not.toEqual(
      fileDatasetQueryKeys.preview('dataset-b', 'table-1', 50),
    );
    expect(fileDatasetQueryKeys.parseJobSummary()).toEqual([
      'file-datasets', 'parse-jobs', 'summary',
    ]);
    expect(fileDatasetQueryKeys.parseJobs(request)).toEqual([
      'file-datasets', 'parse-jobs', 'list', request,
    ]);
  });
});
