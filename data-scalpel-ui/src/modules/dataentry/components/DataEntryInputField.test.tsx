import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Form } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { DataEntryField, DataEntryOptionResponse } from '../model/dataEntry';

const search = vi.hoisted(() => vi.fn());

vi.mock('../hooks/useDataEntry', () => ({
  useDataEntryOptions: () => ({ mutateAsync: search, isPending: false }),
}));

import { DataEntryInputField } from './DataEntryInputField';

const field: DataEntryField = {
  id: 'department-id', code: 'department_id', name: '部门', fieldType: 'LONG',
  length: null, precision: null, scale: null, nullable: true, primaryKey: false,
  sortOrder: 0, description: null, inputSource: 'MODEL_LOOKUP',
  standardDictionary: null, lookup: null,
};

const response: DataEntryOptionResponse = {
  content: [], pageNo: 1, pageSize: 50, hasNext: false,
};

describe('DataEntryInputField lookup search', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it('queries once after typing pauses and cancels the previous query', async () => {
    search.mockResolvedValue(response);
    const { container } = render(<Form><DataEntryInputField formId="form-id" field={field} /></Form>);
    vi.useFakeTimers();

    fireEvent.mouseDown(container.querySelector('.ant-select-content')!);
    await act(async () => { await vi.advanceTimersByTimeAsync(0); });
    expect(search).toHaveBeenCalledTimes(1);
    const initialSignal = search.mock.calls[0][0].signal as AbortSignal;

    const input = screen.getByRole('combobox');
    fireEvent.change(input, { target: { value: '中' } });
    fireEvent.change(input, { target: { value: '中心' } });
    expect(initialSignal.aborted).toBe(true);
    await act(async () => { await vi.advanceTimersByTimeAsync(349); });
    expect(search).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(search).toHaveBeenCalledTimes(2);
    expect(search.mock.calls[1][0]).toMatchObject({ keyword: '中心', pageNo: 1, pageSize: 50 });
  });
});
