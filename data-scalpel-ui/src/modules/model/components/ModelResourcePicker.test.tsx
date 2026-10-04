import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ModelDataSourcePicker, ModelFileDatasetPicker } from './ModelResourcePicker';

const sources = [
  { id: 'first', name: '业务库', code: 'business', type: 'POSTGRESQL', connectionKind: 'JDBC', enabled: true, purposes: ['STORAGE'], connection: { kind: 'JDBC', databaseName: 'business', host: 'localhost', port: 5432 } },
  { id: 'second', name: '归档库', code: 'archive', type: 'POSTGRESQL', connectionKind: 'JDBC', enabled: true, purposes: ['STORAGE'], connection: { kind: 'JDBC', databaseName: 'archive', host: 'localhost', port: 5432 } },
];
vi.mock('../../datasource', () => ({
  DataSourceTypeIcon: () => <span />,
  useDataSource: (id?: string) => ({ data: sources.find((source) => source.id === id) }),
  useDataSourceTypes: () => ({ data: [{ id: 'POSTGRESQL', displayName: 'PostgreSQL', connectionKind: 'JDBC' }] }),
  useDataSources: (request: { page: number }) => ({ data: { content: [sources[request.page]], totalElements: 40 } }),
}));
vi.mock('../../filedataset', () => ({
  FileDatasetTypeIcon: () => <span />,
  fileDatasetTypeLabels: { CSV: 'CSV' },
  useFileDataset: () => ({}),
  useFileDatasets: () => ({ data: { content: [
    { id: 'empty', name: '等待解析的数据集', type: 'CSV', tableCount: 1, readyTableCount: 0 },
    { id: 'ready', name: '已就绪的数据集', type: 'CSV', tableCount: 2, readyTableCount: 2 },
  ], totalElements: 2 } }),
}));

describe('model resource selection', () => {
  afterEach(cleanup);

  it('keeps a staged source across pages and only commits on confirmation', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<ModelDataSourcePicker value="first" onChange={onChange} storageOnly />);
    await user.click(screen.getByRole('button', { name: '选择目标数据存储' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.click(within(dialog).getByTitle('2'));
    await user.click(within(dialog).getByText('归档库'));
    expect(onChange).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByTitle('1'));
    expect(within(dialog).getByText('已选：归档库')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: '确认选择' }));
    expect(onChange).toHaveBeenCalledWith('second');
  });

  it('does not select unready datasets and cancellation leaves the form unchanged', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<ModelFileDatasetPicker onChange={onChange} />);
    await user.click(screen.getByRole('button', { name: '选择文件数据集' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByText('等待解析的数据集'));
    expect(within(dialog).getByRole('button', { name: '确认选择' })).toBeDisabled();
    await user.click(within(dialog).getByText('已就绪的数据集'));
    expect(within(dialog).getByRole('button', { name: '确认选择' })).toBeEnabled();
    await user.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('confirming the existing source does not reset its tables or previews', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<ModelDataSourcePicker value="first" onChange={onChange} storageOnly />);
    await user.click(screen.getByRole('button', { name: '选择目标数据存储' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '确认选择' }));
    expect(onChange).not.toHaveBeenCalled();
  });
});
