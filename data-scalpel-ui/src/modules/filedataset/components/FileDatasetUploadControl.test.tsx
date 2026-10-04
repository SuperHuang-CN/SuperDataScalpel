import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fileDatasetAccept, fileDatasetTypeOptions, type FileDataset, type FileDatasetType } from '../model/fileDataset';

const mocks = vi.hoisted(() => ({ files: vi.fn(), refetch: vi.fn(), upload: vi.fn() }));
vi.mock('../hooks/useFileDatasets', () => ({
  useFileDatasetFiles: () => mocks.files(),
  useUploadFileDatasetFiles: () => ({ mutateAsync: mocks.upload }),
}));
import { FileDatasetUploadControl } from './FileDatasetUploadControl';

const dataset = (type: FileDatasetType) => ({ id: 'dataset-1', name: '测试', type, fileCount: 0 } as FileDataset);
const chooseFile = (container: HTMLElement, name = 'roads.csv') => {
  const file = new File(['data'], name);
  fireEvent.change(container.querySelector('input[type="file"]')!, { target: { files: [file] } });
  return file;
};
beforeEach(() => {
  mocks.upload.mockReset().mockResolvedValue({ jobIds: ['job-1'] });
  mocks.refetch.mockReset().mockResolvedValue({ data: { totalElements: 0 }, isError: false });
  mocks.files.mockReset().mockReturnValue({ data: { totalElements: 0 }, isSuccess: true, isFetching: false, refetch: mocks.refetch });
});
afterEach(cleanup);

describe('shared dataset upload control', () => {
  it.each(fileDatasetTypeOptions.map((option) => option.value))('preserves accepted extensions and upload limits for %s', (type) => {
    mocks.files.mockReturnValue({ data: { totalElements: 1 }, isSuccess: true, isFetching: false, refetch: mocks.refetch });
    const { container } = render(<FileDatasetUploadControl dataset={dataset(type)} />);
    const input = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    expect(input.accept).toBe(fileDatasetAccept(type));
    expect(input.multiple).toBe(false);
    expect(input.disabled).toBe(['EXCEL', 'GDB', 'GPKG'].includes(type));
  });

  it.each(['EXCEL', 'GDB', 'GPKG'] as const)('blocks %s when another upload filled the dataset after file selection', async (type) => {
    const { container } = render(<FileDatasetUploadControl dataset={dataset(type)} />);
    chooseFile(container, type === 'EXCEL' ? 'book.xlsx' : type === 'GPKG' ? 'map.gpkg' : 'map.zip');
    const button = await screen.findByRole('button', { name: '上传并解析' });
    mocks.refetch.mockResolvedValue({ data: { totalElements: 1 }, isError: false });
    fireEvent.click(button);
    expect(await screen.findByText(/只允许一个物理文件/)).toBeInTheDocument();
    expect(mocks.upload).not.toHaveBeenCalled();
  });

  it('retains the selected file and error after an upload failure, then allows retry', async () => {
    mocks.upload.mockRejectedValueOnce(new Error('文件编码不正确'));
    const onUploaded = vi.fn();
    const { container } = render(<FileDatasetUploadControl dataset={dataset('CSV')} onUploaded={onUploaded} />);
    const file = chooseFile(container);
    fireEvent.click(await screen.findByRole('button', { name: '上传并解析' }));
    expect(await screen.findByText('文件编码不正确')).toBeInTheDocument();
    expect(screen.getByText('roads.csv')).toBeInTheDocument();
    expect(onUploaded).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '上传并解析' }));
    await waitFor(() => expect(onUploaded).toHaveBeenCalledOnce());
    expect(mocks.upload).toHaveBeenLastCalledWith({ id: 'dataset-1', files: [file] });
    expect(screen.getByText(/已提交后台解析/)).toBeInTheDocument();
  });

  it('does not upload when the final file-count check fails', async () => {
    const { container } = render(<FileDatasetUploadControl dataset={dataset('CSV')} />);
    chooseFile(container);
    mocks.refetch.mockResolvedValue({ isError: true });
    fireEvent.click(await screen.findByRole('button', { name: '上传并解析' }));
    expect(await screen.findByText(/无法确认当前文件数量/)).toBeInTheDocument();
    expect(mocks.upload).not.toHaveBeenCalled();
  });
});
