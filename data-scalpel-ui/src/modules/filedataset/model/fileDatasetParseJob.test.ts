import { describe, expect, it } from 'vitest';
import {
  FILE_DATASET_PARSE_JOB_MONITOR_INTERVAL_MS,
  buildFileDatasetParseJobSearch,
  fileDatasetParseJobMonitorInterval,
} from './fileDatasetParseJob';

describe('file dataset parse job monitoring model', () => {
  it('builds the stable status filter expected by SearchEngine', () => {
    expect(buildFileDatasetParseJobSearch({ status: 'RUNNING' })).toBe('status:"RUNNING"');
    expect(buildFileDatasetParseJobSearch({})).toBeUndefined();
  });

  it('polls only while the monitor is open', () => {
    expect(fileDatasetParseJobMonitorInterval(true)).toBe(FILE_DATASET_PARSE_JOB_MONITOR_INTERVAL_MS);
    expect(fileDatasetParseJobMonitorInterval(false)).toBe(false);
  });
});
