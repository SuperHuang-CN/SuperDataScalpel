import type { FileOutputConfiguration } from "../../canvasTypes";

export const createFileOutputConfiguration = (): FileOutputConfiguration => ({
  dataSourceId: '',
  writes: [],
  sourceTableName: '', targetPath: '', conflictPolicy: 'FAIL_IF_EXISTS',
  formatOptions: { type: 'CSV', header: true, delimiter: ',', quote: '"', escape: '\\', nullValue: '' },
});
