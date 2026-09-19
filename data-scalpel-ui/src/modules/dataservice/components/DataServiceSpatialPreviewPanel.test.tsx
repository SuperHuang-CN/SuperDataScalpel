import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defaultStyleDocument } from '../../cartography';
import { DataServiceSpatialPreviewPanel } from './DataServiceSpatialPreviewPanel';

const mocks = vi.hoisted(() => ({
  style: vi.fn(), metadata: vi.fn(), live: vi.fn(), draft: vi.fn(), upload: vi.fn(), legend: vi.fn(),
  updateImage: vi.fn(), removeMap: vi.fn(), mutation: vi.fn(),
}));
vi.mock('maplibre-gl', () => ({ default: {
  Map: class {
    touchZoomRotate = { disableRotation: vi.fn() };
    source: { updateImage: typeof mocks.updateImage } | undefined;
    addControl() {} fitBounds() {} resize() {}
    on(event: string, callback: () => void) { if (event === 'load') queueMicrotask(callback); }
    getBounds() { return { getWest: () => 0, getEast: () => 1, getSouth: () => 0, getNorth: () => 1 }; }
    getSource() { return this.source; }
    addSource(_id: string, input: unknown) { this.source = { updateImage: mocks.updateImage }; mocks.updateImage(input); }
    addLayer() {} remove() { mocks.removeMap(); }
  }, NavigationControl: class {}, ScaleControl: class {},
} }));
vi.mock('../api/dataServiceApi', () => ({
  fetchDataServiceSpatialPreviewMap: mocks.live,
  renderDataServiceSpatialStylePreview: mocks.draft,
  renderUploadedDataServiceSpatialStylePreview: mocks.upload,
  fetchDataServiceSpatialPreviewLegend: mocks.legend,
  queryDataServiceSpatialStyleSld: vi.fn(),
}));
vi.mock('../hooks/useDataServices', () => ({
  useDataServiceSpatialStyle: mocks.style, useDataServiceSpatialPreview: mocks.metadata,
  useApplyDataServiceSpatialStyle: () => ({ mutateAsync: mocks.mutation }),
  useUpdateDataServiceSpatialStyle: () => ({ mutateAsync: mocks.mutation }),
  useUploadDataServiceSpatialSld: () => ({ mutateAsync: mocks.mutation }),
  useProfileDataServiceSpatialStyleField: () => ({ mutateAsync: mocks.mutation }),
}));

const document = defaultStyleDocument('POINT');
const style = { mode: 'CARTOGRAPHY', geometryFamily: 'POINT', fields: [], styleDocument: document, defaultStyleDocument: document,
  sldFileName: null, sldFileSize: null, uploadedSldText: null, styleVersion: 1, appliedStyleVersion: 1,
  syncStatus: 'IN_SYNC', syncError: null, deployed: true };
const props = { serviceId: 'service-a', status: 'ENABLED' as const, deploymentStatus: 'DEPLOYED' as const,
  definitionConfigured: true, canUpdate: true, canPublish: true, enableLoading: false, onEnable: vi.fn() };
const renderButton = () => screen.getByRole('button', { name: /渲染当前视图/ });

describe('spatial preview source selection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(800);
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(600);
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:preview'), revokeObjectURL: vi.fn() }));
    mocks.style.mockReturnValue({ data: style });
    mocks.metadata.mockReturnValue({ data: { available: true, initialBounds: [0, 0, 1, 1], qualifiedLayerName: 'workspace:svc_test',
      limits: { minimumWidth: 256, maximumWidth: 1600, minimumHeight: 256, maximumHeight: 1200 } }, refetch: vi.fn() });
    mocks.live.mockResolvedValue(new Blob(['png'])); mocks.draft.mockResolvedValue(new Blob(['png']));
    mocks.upload.mockResolvedValue(new Blob(['png'])); mocks.legend.mockResolvedValue(new Blob(['png']));
  });
  afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('does not auto-render and uses the explicit source even for an in-sync saved draft', async () => {
    render(<DataServiceSpatialPreviewPanel {...props} />);
    await waitFor(() => expect(renderButton()).toBeEnabled());
    expect(mocks.draft).not.toHaveBeenCalled(); expect(mocks.live).not.toHaveBeenCalled();
    fireEvent.click(renderButton());
    await waitFor(() => expect(mocks.draft).toHaveBeenCalledOnce());
    await waitFor(() => expect(screen.getByText(/当前图片：在线制图草稿/)).toBeInTheDocument());
    fireEvent.click(screen.getByText('线上样式'));
    expect(mocks.live).not.toHaveBeenCalled();
    expect(screen.getByText(/当前图片：在线制图草稿/)).toBeInTheDocument();
    fireEvent.click(renderButton());
    await waitFor(() => expect(mocks.live).toHaveBeenCalledOnce());
    await waitFor(() => expect(screen.getByText(/当前图片：线上样式/)).toBeInTheDocument());
    expect(mocks.mutation).not.toHaveBeenCalled();
  });

  it('ignores an older request when switching preview sources', async () => {
    let finishDraft: (blob: Blob) => void = () => undefined;
    mocks.draft.mockImplementationOnce(() => new Promise<Blob>(resolve => { finishDraft = resolve; }));
    render(<DataServiceSpatialPreviewPanel {...props} />);
    await waitFor(() => expect(renderButton()).toBeEnabled());
    fireEvent.click(renderButton());
    fireEvent.click(screen.getByText('线上样式'));
    const signal: AbortSignal = mocks.draft.mock.calls[0][5];
    expect(signal.aborted).toBe(true);
    fireEvent.click(renderButton());
    await waitFor(() => expect(screen.getByText(/当前图片：线上样式/)).toBeInTheDocument());
    await act(async () => { finishDraft(new Blob(['old'])); });
    expect(mocks.updateImage).toHaveBeenCalledOnce();
    expect(screen.queryByText(/当前图片：在线制图草稿/)).not.toBeInTheDocument();
  });

  it('uses saved uploaded SLD as the draft without requiring another file selection', async () => {
    mocks.style.mockReturnValue({ data: { ...style, mode: 'UPLOADED_SLD', sldFileName: 'saved.sld', uploadedSldText: '<sld>中文</sld>' } });
    render(<DataServiceSpatialPreviewPanel {...props} />);
    await waitFor(() => expect(renderButton()).toBeEnabled());
    fireEvent.click(renderButton());
    await waitFor(() => expect(mocks.upload).toHaveBeenCalledOnce());
    const file: File = mocks.upload.mock.calls[0][1];
    expect(file.name).toBe('saved.sld');
    expect(file.size).toBe(new Blob(['<sld>中文</sld>']).size);
    expect(mocks.live).not.toHaveBeenCalled(); expect(mocks.legend).not.toHaveBeenCalled();
  });

  it('only requests live WMS for view-only users', async () => {
    render(<DataServiceSpatialPreviewPanel {...props} canUpdate={false} canPublish={false} />);
    await waitFor(() => expect(renderButton()).toBeEnabled());
    expect(screen.getByRole('radio', { name: '当前草稿' })).toBeDisabled();
    fireEvent.click(renderButton());
    await waitFor(() => expect(mocks.live).toHaveBeenCalledOnce());
    expect(mocks.draft).not.toHaveBeenCalled(); expect(mocks.upload).not.toHaveBeenCalled();
  });
});
