import { cleanup, render } from '@testing-library/react';
import { ConfigProvider, Form } from 'antd';
import { afterEach, describe, expect, it } from 'vitest';
import { FileDatasetParsingOptionsFields } from './FileDatasetParsingOptionsForm';
import { defaultFileDatasetParsingOptions, type FileDatasetType } from '../model/fileDataset';
import { buildFileDatasetParsingOptions, parsingFormValues } from '../model/fileDatasetParsingForm';

const fieldsByType: Record<FileDatasetType, string[]> = {
  CSV: ['charset', 'fieldDelimiter', 'recordDelimiter', 'quoteCharacter', 'escapeCharacter', 'firstRowHeader'],
  TSV: ['charset', 'fieldDelimiter', 'recordDelimiter', 'quoteCharacter', 'escapeCharacter', 'firstRowHeader'],
  TXT: ['charset', 'recordDelimiter'],
  JSON: ['charset', 'rootPointer'],
  JSONL: ['charset', 'recordDelimiter'],
  GEOJSON: ['epsgCode'],
  GEOJSONL: ['epsgCode'],
  GEOPARQUET: [],
  GPKG: [],
  PARQUET: [],
  AVRO: [],
  EXCEL: ['headerRowIndex', 'dataStartRowIndex'],
  GDB: ['epsgCode'],
  SHP: ['zipEntryCharset', 'dbfCharsetOverride', 'dbfFallbackCharset', 'epsgCode'],
};

afterEach(cleanup);

describe('file dataset parsing fields', () => {
  it.each(Object.keys(fieldsByType) as FileDatasetType[])('preserves %s fields and default submission', (type) => {
    const options = defaultFileDatasetParsingOptions(type);
    const values = parsingFormValues(options);
    const { container } = render(<Form name="parsing" initialValues={values}><FileDatasetParsingOptionsFields type={type} /></Form>);
    const actualFields = Array.from(container.querySelectorAll('.ant-form-item-label > label[for]'))
      .map((label) => label.getAttribute('for')?.replace('parsing_', ''));
    expect(actualFields.sort()).toEqual([...fieldsByType[type]].sort());
    expect(buildFileDatasetParsingOptions(type, values)).toEqual(options);
    if (fieldsByType[type].length === 0) {
      expect(container.querySelector('.file-dataset-native-parsing')?.textContent).toBeTruthy();
    }
  });

  it.each(Object.keys(fieldsByType).filter((type) => fieldsByType[type as FileDatasetType].length > 0) as FileDatasetType[])(
    'keeps every %s parsing control disabled when settings are locked', (type) => {
      const { container } = render(<ConfigProvider componentDisabled><Form initialValues={parsingFormValues(defaultFileDatasetParsingOptions(type))}><FileDatasetParsingOptionsFields type={type} /></Form></ConfigProvider>);
      const controls = Array.from(container.querySelectorAll<HTMLInputElement | HTMLButtonElement>('input, button[role="switch"]'));
      expect(controls.length).toBeGreaterThan(0);
      expect(controls.every((control) => control.disabled)).toBe(true);
    },
  );
});
