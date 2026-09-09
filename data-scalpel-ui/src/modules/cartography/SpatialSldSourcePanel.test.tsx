import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SpatialSldSourcePanel } from './SpatialSldSourcePanel';
import { defaultStyleDocument } from './style';

vi.mock('../../shared/components/MonacoSqlEditor', () => ({
  MonacoSqlEditor: ({ value, readOnly, language }: { value: string; readOnly: boolean; language: string }) => (
    <textarea aria-label="SLD source" value={value} readOnly={readOnly} data-language={language} />
  ),
}));

const initialDocument = defaultStyleDocument('POINT');
const nextDocument = defaultStyleDocument('POLYGON');
const openSource = () => fireEvent.click(screen.getByText('SLD 源码（只读）'));
const tick = async (ms = 450) => { await act(async () => { await vi.advanceTimersByTimeAsync(ms); }); };
const base = {
  mode: 'CARTOGRAPHY' as const, geometryFamily: 'POINT' as const,
  document: initialDocument, file: null, uploadedSldText: null,
};

describe('SpatialSldSourcePanel', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => { cleanup(); vi.useRealTimers(); });

  it('compiles only when expanded, debounces edits and exposes a read-only XML view', async () => {
    const compile = vi.fn().mockResolvedValueOnce('<sld>first</sld>').mockResolvedValueOnce('<sld>second</sld>');
    const { rerender } = render(<SpatialSldSourcePanel {...base} onQuerySld={compile} />);
    await tick();
    expect(compile).not.toHaveBeenCalled();
    openSource();
    await tick(449);
    expect(compile).not.toHaveBeenCalled();
    await tick(1);
    expect(screen.getByLabelText('SLD source')).toHaveValue('<sld>first</sld>');
    expect(screen.getByLabelText('SLD source')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('SLD source')).toHaveAttribute('data-language', 'xml');
    rerender(<SpatialSldSourcePanel {...base} document={nextDocument} onQuerySld={compile} />);
    expect(screen.queryByLabelText('SLD source')).not.toBeInTheDocument();
    await tick();
    expect(compile).toHaveBeenLastCalledWith(nextDocument, expect.any(AbortSignal));
    expect(screen.getByLabelText('SLD source')).toHaveValue('<sld>second</sld>');
  });

  it('ignores a stale response even if the transport does not honor cancellation', async () => {
    let resolveOld: (value: string) => void = () => undefined;
    const compile = vi.fn().mockImplementationOnce(() => new Promise<string>(resolve => { resolveOld = resolve; }))
      .mockResolvedValueOnce('<sld>latest</sld>');
    const { rerender } = render(<SpatialSldSourcePanel {...base} onQuerySld={compile} />);
    openSource(); await tick();
    const signal: AbortSignal = compile.mock.calls[0][1];
    rerender(<SpatialSldSourcePanel {...base} document={nextDocument} onQuerySld={compile} />);
    expect(signal.aborted).toBe(true);
    await tick();
    await act(async () => { resolveOld('<sld>stale</sld>'); });
    expect(screen.getByLabelText('SLD source')).toHaveValue('<sld>latest</sld>');
  });

  it('reads uploaded UTF-8 immediately and displays the saved original after reopening', async () => {
    const original = '<?xml version="1.0"?>\n<样式 name="中文 &amp; XML" />\r\n';
    const file = new File([original], 'example.sld');
    const read = vi.fn().mockResolvedValue(new TextEncoder().encode(original).buffer);
    Object.defineProperty(file, 'arrayBuffer', { value: read });
    const compile = vi.fn();
    const { rerender } = render(<SpatialSldSourcePanel {...base} mode="UPLOADED_SLD" file={file} onQuerySld={compile} />);
    await tick(0);
    expect(read).toHaveBeenCalledOnce();
    expect(compile).not.toHaveBeenCalled();
    openSource(); await tick(0);
    // A textarea normalizes CRLF; the source prop must retain the raw text.
    expect(screen.getByLabelText('SLD source')).toHaveTextContent('中文 &amp; XML');
    expect(screen.getByText('待保存校验')).toBeInTheDocument();
    rerender(<SpatialSldSourcePanel {...base} mode="UPLOADED_SLD" uploadedSldText={original} onQuerySld={compile} />);
    await tick(0);
    expect(screen.getByLabelText('SLD source')).toHaveValue(original.replaceAll('\r\n', '\n'));
    expect(screen.getByText('已保存上传原文')).toBeInTheDocument();
  });

  it('shows errors without old source and can retry compilation', async () => {
    const compile = vi.fn().mockRejectedValueOnce(new Error('字段已失效')).mockResolvedValueOnce('<sld>recovered</sld>');
    render(<SpatialSldSourcePanel {...base} onQuerySld={compile} />);
    openSource(); await tick();
    expect(screen.getByText('SLD 源码读取失败')).toBeInTheDocument();
    expect(screen.queryByLabelText('SLD source')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ })); await tick();
    expect(screen.getByLabelText('SLD source')).toHaveValue('<sld>recovered</sld>');
  });

  it('does not fabricate or compile generic styles', async () => {
    const compile = vi.fn();
    render(<SpatialSldSourcePanel {...base} geometryFamily="GENERIC" document={null} onQuerySld={compile} />);
    openSource(); await tick();
    expect(screen.getByText('使用 GeoServer 内置 generic 样式')).toBeInTheDocument();
    expect(compile).not.toHaveBeenCalled();
    expect(screen.queryByLabelText('SLD source')).not.toBeInTheDocument();
  });
});
