import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../shared/api/http';
import type { DataEntryFormDetail, DataEntryMutationResponse } from '../model/dataEntry';

const hookState = vi.hoisted(() => ({ submit: vi.fn() }));

vi.mock('../hooks/useDataEntry', () => ({
  useSubmitDataEntry: () => ({ mutateAsync: hookState.submit, isPending: false }),
  useDataEntryOptions: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

import { DataEntryCreateDrawer } from './DataEntryCreateDrawer';

const detail: DataEntryFormDetail = {
  form: {
    id: 'form-id', modelId: 'model-id', modelCode: 'staff', modelName: '人员档案',
    modelDescription: null, modelStatus: 'PUBLISHED', modelSchemaVersion: 1,
    status: 'PUBLISHED', publishedModelSchemaVersion: 1, healthSummary: 'HEALTHY',
    issues: [], createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
  },
  fields: [{
    id: 'name-id', code: 'name', name: '姓名', fieldType: 'STRING', length: 50,
    precision: null, scale: null, nullable: false, primaryKey: false, sortOrder: 0,
    description: null, inputSource: 'DEFAULT', standardDictionary: null, lookup: null,
  }],
  lookups: [],
  health: { canPublish: true, canSubmit: true, canDeleteEntries: true, canQueryEntries: true, issues: [] },
};

const result: DataEntryMutationResponse = {
  operationLogId: 'log-id', requestedCount: 1, affectedCount: 1,
  status: 'SUCCEEDED', manualVerificationRequired: false, warningMessage: null,
};

describe('DataEntryCreateDrawer', () => {
  afterEach(() => { cleanup(); vi.clearAllMocks(); });

  it('validates fields and submits one record before closing', async () => {
    hookState.submit.mockResolvedValue(result);
    const onClose = vi.fn();
    const onSubmitted = vi.fn();
    const user = userEvent.setup();
    render(<DataEntryCreateDrawer detail={detail} onClose={onClose} onSubmitted={onSubmitted} />);

    await user.click(screen.getByRole('button', { name: '提交并立即生效' }));
    expect(hookState.submit).not.toHaveBeenCalled();

    await user.type(screen.getByRole('textbox', { name: '姓名（name）' }), '张三');
    await user.click(screen.getByRole('button', { name: '提交并立即生效' }));
    await waitFor(() => expect(hookState.submit).toHaveBeenCalledWith({ id: 'form-id', values: { name: '张三' } }));
    expect(onSubmitted).toHaveBeenCalledWith(result);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('keeps entered values available after a write error', async () => {
    hookState.submit.mockRejectedValue(new ApiError('业务主键已存在', 409));
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<DataEntryCreateDrawer detail={detail} onClose={onClose} onSubmitted={vi.fn()} />);

    const nameInput = screen.getByRole('textbox', { name: '姓名（name）' });
    await user.type(nameInput, '张三');
    await user.click(screen.getByRole('button', { name: '提交并立即生效' }));

    expect(await screen.findByText('业务主键已存在')).toBeInTheDocument();
    expect(nameInput).toHaveValue('张三');
    expect(onClose).not.toHaveBeenCalled();
  });
});
