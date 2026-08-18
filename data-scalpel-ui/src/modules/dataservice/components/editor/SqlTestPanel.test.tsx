import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { SqlServiceTestResponse } from '../../model/dataService';
import { SqlTestPanel } from './SqlTestPanel';

const result = (rows: Record<string, unknown>[]): SqlServiceTestResponse => ({
  valid: true,
  problems: [],
  resultFields: [
    {
      name: 'id',
      typeDefinition: { type: 'LONG', length: null, precision: null, scale: null },
      nullable: false,
    },
    {
      name: 'name',
      typeDefinition: { type: 'STRING', length: 100, precision: null, scale: null },
      nullable: true,
    },
  ],
  preview: {
    pageNo: 0,
    pageSize: 20,
    totalCount: rows.length,
    resultList: rows,
  },
  elapsedMs: 12,
});

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('SqlTestPanel', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false, media: query, onchange: null,
        addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
      })),
    });
  });

  afterEach(() => cleanup());

  it('combines output schema and preview data in two-line column headers', () => {
    render(<SqlTestPanel result={result([{ id: 7, name: 'Alice' }])} />);

    expect(document.querySelectorAll('.data-service-preview-table')).toHaveLength(1);
    const tableHeader = document.querySelector('.data-service-preview-table .ant-table-thead');
    expect(tableHeader).not.toBeNull();
    expect(within(tableHeader as HTMLElement).getByText('id')).toBeInTheDocument();
    expect(within(tableHeader as HTMLElement).getByText('LONG · 非空')).toBeInTheDocument();
    expect(within(tableHeader as HTMLElement).getByText('name')).toBeInTheDocument();
    expect(within(tableHeader as HTMLElement).getByText('STRING(100) · 可空')).toBeInTheDocument();
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.queryByText('输出字段')).not.toBeInTheDocument();
    expect(screen.queryByText('平台类型')).not.toBeInTheDocument();
  });

  it('keeps schema headers visible when the preview has no rows', () => {
    render(<SqlTestPanel result={result([])} />);

    const tableHeader = document.querySelector('.data-service-preview-table .ant-table-thead');
    expect(tableHeader).not.toBeNull();
    expect(within(tableHeader as HTMLElement).getByText('LONG · 非空')).toBeInTheDocument();
    expect(within(tableHeader as HTMLElement).getByText('STRING(100) · 可空')).toBeInTheDocument();
    expect(screen.getByText('查询成功，暂无预览数据')).toBeInTheDocument();
  });

  it('keeps structured test problems for invalid SQL', () => {
    render(<SqlTestPanel result={{
      valid: false,
      problems: [{ code: 'INVALID_SQL', message: '仅允许只读查询', subject: 'sqlText' }],
      resultFields: [],
      preview: null,
      elapsedMs: 3,
    }} />);

    expect(screen.getByText('SQL 测试未通过')).toBeInTheDocument();
    expect(screen.getByText('INVALID_SQL')).toBeInTheDocument();
    expect(screen.getByText('仅允许只读查询')).toBeInTheDocument();
  });
});
