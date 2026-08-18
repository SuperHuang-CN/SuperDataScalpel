import type { FileDatasetType } from '../model/fileDataset';

export type FileDatasetTypeIconTone = 'blue' | 'violet' | 'cyan' | 'green' | 'orange' | 'rose' | 'slate';

export const fileDatasetTypeIconTones = {
  CSV: 'green',
  TSV: 'cyan',
  TXT: 'slate',
  JSON: 'violet',
  JSONL: 'violet',
  PARQUET: 'orange',
  AVRO: 'rose',
  EXCEL: 'green',
  GDB: 'blue',
  SHP: 'orange',
} satisfies Record<FileDatasetType, FileDatasetTypeIconTone>;
