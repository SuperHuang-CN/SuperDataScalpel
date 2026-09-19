import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { DataEntryFormDetail, DataEntryImportPreview } from '../model/dataEntry';

const hookState = vi.hoisted(() => ({
  preview: vi.fn(),
  resetPreview: vi.fn(),
  importFile: vi.fn(),
  resetImport: vi.fn(),
  previewData: null as DataEntryImportPreview | null,
}));

vi.mock('../hooks/useDataEntry', () => ({
  usePreviewDataEntryImport: () => ({
    data: hookState.previewData,
    mutateAsync: hookState.preview,
    reset: hookState.resetPreview,
    isPending: false,
  }),
  useImportDataEntryFile: () => ({
    mutateAsync: hookState.importFile,
    reset: hookState.resetImport,
    isPending: false,
  }),
}));

import { DataEntryImportDrawer } from './DataEntryImportDrawer';

const detail: DataEntryFormDetail = {
  form: {
    id: 'form-id',
    modelId: 'model-id',
    modelCode: 'government_exchange',
    modelName: '政务交换数据',
    modelDescription: null,
    modelStatus: 'PUBLISHED',
    modelSchemaVersion: 3,
    status: 'PUBLISHED',
    publishedModelSchemaVersion: 3,
    healthSummary: 'HEALTHY',
    issues: [],
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-02T08:30:00Z',
  },
  fields: [],
  lookups: [],
  health: {
    canPublish: true,
    canSubmit: true,
    canDeleteEntries: true,
    canQueryEntries: true,
    issues: [],
  },
};

const preview: DataEntryImportPreview = {
  fileName: 'government_exchange.xlsx',
  format: 'XLSX',
  fileSize: 2048,
  totalRowCount: 2,
  validRowCount: 2,
  errorRowCount: 0,
  issueCount: 0,
  importable: true,
  issuesTruncated: false,
  previewDigest: 'preview-digest',
  fields: [{
    id: 'field-id',
    code: 'organization_name',
    name: '单位名称',
    fieldType: 'STRING',
    nullable: false,
    primaryKey: false,
    inputSource: 'DEFAULT',
  }],
  previewRows: [{
    rowNumber: 3,
    values: { organization_name: '示例单位' },
    displayValues: { organization_name: '示例单位' },
  }],
  issues: [],
};

describe('DataEntryImportDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    hookState.previewData = null;
  });

  it('runs the upload, validation and confirmation flow in one workspace', async () => {
    hookState.preview.mockImplementation(async () => {
      hookState.previewData = preview;
      return preview;
    });
    hookState.importFile.mockResolvedValue({
      operationLogId: 'operation-id',
      requestedCount: 2,
      affectedCount: 2,
      status: 'SUCCEEDED',
      manualVerificationRequired: false,
      warningMessage: null,
    });
    const onClose = vi.fn();
    const onImported = vi.fn();
    const user = userEvent.setup();
    const { container } = render(
      <DataEntryImportDrawer open detail={detail} onClose={onClose} onImported={onImported} />,
    );

    expect(screen.getByText('批量导入填报数据')).toBeInTheDocument();
    expect(screen.getByText('选择文件')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认导入' })).toBeDisabled();

    const input = container.ownerDocument.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).not.toBeNull();
    const uploadFile = new File(['workbook'], preview.fileName, { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    await user.upload(input!, uploadFile);

    await waitFor(() => expect(screen.getByText('校验结果')).toBeInTheDocument());
    expect(screen.getByText('全文件校验通过')).toBeInTheDocument();
    expect(screen.getByText('数据预览')).toBeInTheDocument();
    expect(screen.getByText('示例单位')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '确认导入' }));
    await waitFor(() => expect(hookState.importFile).toHaveBeenCalledWith({
      id: detail.form.id,
      file: uploadFile,
      previewDigest: preview.previewDigest,
    }));
    expect(onImported).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });
});
