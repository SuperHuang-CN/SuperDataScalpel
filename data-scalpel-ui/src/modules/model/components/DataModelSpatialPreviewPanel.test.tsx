import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DataModelSpatialPreviewPanel } from './DataModelSpatialPreviewPanel';
import type { SpatialPreviewStatus } from '../model/spatialPreview';

const harness = vi.hoisted(() => {
  const maps: PendingTileMap[] = [];
  class PendingTileMap {
    sources = new Map<string, unknown>();
    layers = new Set<string>();
    listeners = new Map<string, Set<() => void>>();
    touchZoomRotate = { disableRotation: vi.fn() };
    fitBounds = vi.fn();
    constructor() { maps.push(this); }
    addControl() {} resize() {} remove() { this.listeners.clear(); }
    // The style is usable, but a remote tile never finishes loading.
    loaded() { return false; }
    isStyleLoaded() { return false; }
    getBounds() { return { getWest: () => 110, getEast: () => 120, getSouth: () => 20, getNorth: () => 30 }; }
    getLayer(id: string) { return this.layers.has(id) ? { id } : undefined; }
    addLayer(layer: { id: string }) { this.layers.add(layer.id); }
    removeLayer(id: string) { this.layers.delete(id); }
    addSource(id: string, source: unknown) { this.sources.set(id, source); }
    getSource(id: string) { return this.sources.has(id) ? { updateImage: vi.fn() } : undefined; }
    removeSource(id: string) { this.sources.delete(id); }
    on(event: string, callback: () => void) {
      const listeners = this.listeners.get(event) ?? new Set();
      listeners.add(callback); this.listeners.set(event, listeners);
    }
    off(event: string, callback: () => void) { this.listeners.get(event)?.delete(callback); }
    once(event: string, callback: () => void) {
      const once = () => { this.off(event, once); callback(); };
      this.on(event, once);
    }
    loadStyle() {
      this.layers.add('background');
      this.listeners.get('load')?.forEach(callback => callback());
    }
  }
  return { maps, Map: PendingTileMap, metadata: vi.fn(), config: vi.fn(), image: vi.fn(), refetchConfig: vi.fn(), status: vi.fn(), prepare: vi.fn() };
});

vi.mock('maplibre-gl', () => ({ default: {
  Map: harness.Map, NavigationControl: class {}, ScaleControl: class {},
} }));
vi.mock('../api/spatialPreviewApi', () => ({
  previewImage: harness.image, previewMetadata: harness.metadata, previewStatus: harness.status, preparePreview: harness.prepare,
}));
vi.mock('../../system', () => ({ useMapConfiguration: harness.config }));

const NativeURL = URL;
const initialConfig = { url: 'https://tiles.example.com/{z}/{x}/{y}.png', attribution: 'Example', maxZoom: 18 };
const metadata = {
  supported: true, initialBounds: [110, 20, 120, 30],
  limits: { minimumWidth: 256, maximumWidth: 1600, minimumHeight: 256, maximumHeight: 1200 },
  geometryFields: [{ code: 'shape', name: 'SHAPE', kind: 'MULTIPOLYGON', sourceCrs: { authority: 'EPSG', code: '4549' },
    previewAllowed: true, spatialIndexAvailable: true, estimatedRowCount: 35, physicalStatisticsRefreshRequired: false }],
};
const basemapId = 'data-scalpel-preview-basemap';

const ready: SpatialPreviewStatus = { state: 'READY', generation: 'one', featureCount: 1, emptyCount: 0,
  bounds: [110, 20, 120, 30], imageBounds: [12245144, 2273030, 13358338, 3503549], message: '地图已就绪', observedAt: null, expiresAt: null };
const overviewReady: SpatialPreviewStatus = { ...ready, state: 'OVERVIEW_READY', message: '概览已就绪，正在完成地图准备' };
const statusKey = ['spatial-preview-status', '/v1/models/model-a', 'shape'];
const imageSourceId = 'data-scalpel-spatial-preview-image';
const createView = () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const element = <QueryClientProvider client={client}><DataModelSpatialPreviewPanel modelId="model-a" /></QueryClientProvider>;
  return { client, element, ...render(element) };
};

describe('spatial preview interaction', () => {
  beforeEach(() => {
    vi.clearAllMocks(); harness.maps.length = 0;
    vi.stubGlobal('URL', class extends NativeURL {
      static createObjectURL() { return 'blob:model-preview'; }
      static revokeObjectURL() {}
    });
    harness.metadata.mockResolvedValue(metadata);
    harness.status.mockResolvedValue(ready);
    harness.config.mockReturnValue({ data: initialConfig });
    harness.image.mockResolvedValue({ blob: new Blob(['png']), generation: 'one', bounds: ready.imageBounds, resolution: 1000, overview: true, degraded: false });
    harness.prepare.mockResolvedValue({ ...ready, state: 'PREPARING', generation: 'two' });
  });
  afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); });

  it('shows data without waiting for remote basemap tiles', async () => {
    createView();
    await act(async () => harness.maps[0].loadStyle());
    await waitFor(() => expect(harness.image).toHaveBeenCalled());
    expect(harness.maps[0].sources.get(basemapId)).toEqual(expect.objectContaining({ tiles: [initialConfig.url] }));
    expect(screen.getByText('1 个要素')).toBeInTheDocument();
  });

  it('shows the complete overview and allows navigation while sharing the snapshot', async () => {
    harness.status.mockResolvedValue(overviewReady);
    const view = createView();
    const map = harness.maps[0];
    await act(async () => map.loadStyle());
    await waitFor(() => expect(map.sources.has(imageSourceId)).toBe(true));
    expect(screen.getByText(overviewReady.message)).toBeInTheDocument();
    expect(screen.getByText('1 个要素')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /定位数据/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /重新加载数据/ })).toBeEnabled();
    expect(map.fitBounds).toHaveBeenCalledOnce();

    const removeSource = vi.spyOn(map, 'removeSource');
    await act(async () => { view.client.setQueryData(statusKey, ready); });
    await waitFor(() => expect(screen.queryByText(overviewReady.message)).not.toBeInTheDocument());
    expect(map.sources.has(imageSourceId)).toBe(true);
    expect(removeSource).not.toHaveBeenCalledWith(imageSourceId);
    expect(harness.maps).toHaveLength(1);
    expect(map.fitBounds).toHaveBeenCalledOnce();
  });

  it('keeps the verified overview when force reload joins the same preparation', async () => {
    harness.status.mockResolvedValue(overviewReady);
    harness.prepare.mockResolvedValue(overviewReady);
    createView();
    const map = harness.maps[0];
    await act(async () => map.loadStyle());
    await waitFor(() => expect(map.sources.has(imageSourceId)).toBe(true));
    const removeSource = vi.spyOn(map, 'removeSource');
    fireEvent.click(screen.getByRole('button', { name: /重新加载数据/ }));
    await waitFor(() => expect(harness.prepare).toHaveBeenCalledWith('/v1/models/model-a', 'shape', true));
    expect(map.sources.has(imageSourceId)).toBe(true);
    expect(removeSource).not.toHaveBeenCalledWith(imageSourceId);
    expect(harness.maps).toHaveLength(1);
  });

  it.each(['UPDATING', 'NOT_PREPARED'] as const)('revokes an early overview as soon as status becomes %s', async (state) => {
    harness.status.mockResolvedValue(overviewReady);
    const view = createView();
    const map = harness.maps[0];
    await act(async () => map.loadStyle());
    await waitFor(() => expect(map.sources.has(imageSourceId)).toBe(true));
    await act(async () => { view.client.setQueryData(statusKey, { ...overviewReady, state }); });
    await waitFor(() => expect(map.sources.has(imageSourceId)).toBe(false));
    expect(screen.getByRole('button', { name: /定位数据/ })).toBeDisabled();
    expect(harness.maps).toHaveLength(1);
  });

  it('changes the basemap without reconstructing the map or moving the viewport', async () => {
    const view = createView();
    const map = harness.maps[0];
    await act(async () => map.loadStyle());
    await waitFor(() => expect(map.sources.has('data-scalpel-spatial-preview-image')).toBe(true));
    const replacement = { ...initialConfig, url: 'https://another.example.com/{z}/{x}/{y}.png', maxZoom: 12 };
    harness.config.mockReturnValue({ data: replacement });
    view.rerender(<QueryClientProvider client={view.client}><DataModelSpatialPreviewPanel modelId="model-a" /></QueryClientProvider>);
    expect(map.sources.get(basemapId)).toEqual(expect.objectContaining({ tiles: [replacement.url], maxzoom: 12 }));
    harness.config.mockReturnValue({ data: { ...replacement, url: '' } });
    view.rerender(<QueryClientProvider client={view.client}><DataModelSpatialPreviewPanel modelId="model-a" /></QueryClientProvider>);
    expect(map.sources.has(basemapId)).toBe(false);
    expect(map.sources.has('data-scalpel-spatial-preview-image')).toBe(true);
    expect(harness.maps).toHaveLength(1);
    expect(map.fitBounds).toHaveBeenCalledOnce();
  });

  it('force reload removes old pixels immediately and keeps the map interactive', async () => {
    createView();
    const map=harness.maps[0];
    await act(async () => map.loadStyle());
    await waitFor(() => expect(map.sources.has('data-scalpel-spatial-preview-image')).toBe(true));
    fireEvent.click(screen.getByRole('button', { name: /重新加载数据/ }));
    expect(map.sources.has('data-scalpel-spatial-preview-image')).toBe(false);
    await waitFor(() => expect(harness.prepare).toHaveBeenCalledWith('/v1/models/model-a','shape',true));
    expect(harness.maps).toHaveLength(1);
  });

  it.each([ready, overviewReady])('does not display an obsolete response after movement starts in $state', async (status) => {
    let resolve!: (value: unknown) => void;
    harness.status.mockResolvedValue(status);
    harness.image.mockReturnValue(new Promise(result => { resolve=result; }));
    createView();
    const map=harness.maps[0];
    await act(async () => map.loadStyle());
    await waitFor(() => expect(harness.image).toHaveBeenCalled());
    await act(async () => {
      map.listeners.get('movestart')?.forEach(callback => (callback as (event: object) => void)({originalEvent:{}}));
      resolve({ blob: new Blob(['old']), generation:'one', bounds:ready.imageBounds, overview:true });
    });
    expect(map.sources.has('data-scalpel-spatial-preview-image')).toBe(false);
    expect(harness.image.mock.calls[0][7].aborted).toBe(true);
  });

  it('automatically fills detail after a temporary busy fallback', async () => {
    const overview = { blob: new Blob(['overview']), generation:'one', bounds:ready.imageBounds, resolution:1000, overview:true, degraded:false };
    harness.image.mockResolvedValueOnce(overview)
      .mockResolvedValueOnce({ ...overview, degraded:true, fallbackReason:'BUSY' })
      .mockResolvedValue({ ...overview, overview:false, resolution:1 });
    createView();
    const map=harness.maps[0];
    vi.spyOn(map,'getBounds').mockReturnValue({ getWest:()=>116, getEast:()=>116.1, getSouth:()=>25, getNorth:()=>25.1 });
    await act(async () => map.loadStyle());
    await waitFor(() => expect(harness.image.mock.calls.length).toBeGreaterThanOrEqual(3), { timeout:3000 });
    expect(harness.maps).toHaveLength(1);
  });

  it('fills detail when sharing completes without removing the visible overview', async () => {
    harness.status.mockResolvedValue(overviewReady);
    const overview = { blob: new Blob(['overview']), generation: 'one', bounds: ready.imageBounds, resolution: 1000, overview: true, degraded: false };
    harness.image.mockResolvedValueOnce(overview)
      .mockResolvedValueOnce({ ...overview, degraded: true, fallbackReason: 'SHARING' })
      .mockResolvedValue({ ...overview, overview: false, resolution: 1 });
    const view = createView();
    const map = harness.maps[0];
    vi.spyOn(map, 'getBounds').mockReturnValue({ getWest: () => 116, getEast: () => 116.1, getSouth: () => 25, getNorth: () => 25.1 });
    await act(async () => map.loadStyle());
    await waitFor(() => expect(harness.image).toHaveBeenCalledTimes(2));
    const removeSource = vi.spyOn(map, 'removeSource');
    await act(async () => { view.client.setQueryData(statusKey, ready); });
    await waitFor(() => expect(harness.image).toHaveBeenCalledTimes(3));
    expect(harness.image.mock.calls.filter(call => call[6])).toHaveLength(1);
    expect(map.sources.has(imageSourceId)).toBe(true);
    expect(removeSource).not.toHaveBeenCalledWith(imageSourceId);
    expect(harness.maps).toHaveLength(1);
    expect(map.fitBounds).toHaveBeenCalledOnce();
  });

  it('waits for a slow cold restore while the viewport remains unchanged', async () => {
    vi.useFakeTimers();
    const startedAt = Date.now();
    const overview = { blob: new Blob(['overview']), generation: 'one', bounds: ready.imageBounds, resolution: 1000, overview: true, degraded: false };
    harness.image.mockImplementation(async (...args) => args[6] ? overview
      : Date.now() - startedAt < 25_000 ? { ...overview, degraded: true, fallbackReason: 'RESTORING' }
        : { ...overview, overview: false, resolution: 1 });
    createView();
    const map = harness.maps[0];
    vi.spyOn(map, 'getBounds').mockReturnValue({ getWest: () => 116, getEast: () => 116.1, getSouth: () => 25, getNorth: () => 25.1 });
    await act(async () => { map.loadStyle(); await vi.advanceTimersByTimeAsync(100); });
    expect(harness.image).toHaveBeenCalledTimes(2);
    for (const delay of [2000, 4000, 8000, 16000]) await act(async () => { await vi.advanceTimersByTimeAsync(delay); });
    expect(harness.image).toHaveBeenCalledTimes(6);
    expect(harness.status.mock.calls.length).toBeGreaterThanOrEqual(2);
    expect(map.sources.has(imageSourceId)).toBe(true);
    expect(screen.queryByText('当前范围细节加载超时，已保留完整概览')).not.toBeInTheDocument();
    await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
    expect(harness.image).toHaveBeenCalledTimes(6);
    expect(harness.maps).toHaveLength(1);
  });

  it('bounds restoration retries to 130 seconds and requires an explicit retry afterwards', async () => {
    vi.useFakeTimers();
    const overview = { blob: new Blob(['overview']), generation: 'one', bounds: ready.imageBounds, resolution: 1000, overview: true, degraded: false };
    harness.image.mockImplementation(async (...args) => args[6] ? overview : { ...overview, degraded: true, fallbackReason: 'RESTORING' });
    createView();
    const map = harness.maps[0];
    vi.spyOn(map, 'getBounds').mockReturnValue({ getWest: () => 116, getEast: () => 116.1, getSouth: () => 25, getNorth: () => 25.1 });
    await act(async () => { map.loadStyle(); await vi.advanceTimersByTimeAsync(100); });
    for (const delay of [2000, 4000, 8000, 16000, 20000, 20000, 20000, 20000, 20000]) await act(async () => { await vi.advanceTimersByTimeAsync(delay); });
    expect(harness.image).toHaveBeenCalledTimes(11);
    expect(screen.getByText('当前范围细节加载超时，已保留完整概览')).toBeInTheDocument();
    expect(map.sources.has(imageSourceId)).toBe(true);
    await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
    expect(harness.image).toHaveBeenCalledTimes(11);
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    await act(async () => { await vi.advanceTimersByTimeAsync(2100); });
    expect(harness.image).toHaveBeenCalledTimes(13);
  });

  it('cancels a scheduled cold restore retry when the viewport starts moving', async () => {
    vi.useFakeTimers();
    const overview = { blob: new Blob(['overview']), generation: 'one', bounds: ready.imageBounds, resolution: 1000, overview: true, degraded: false };
    harness.image.mockImplementation(async (...args) => args[6] ? overview : { ...overview, degraded: true, fallbackReason: 'RESTORING' });
    createView();
    const map = harness.maps[0];
    vi.spyOn(map, 'getBounds').mockReturnValue({ getWest: () => 116, getEast: () => 116.1, getSouth: () => 25, getNorth: () => 25.1 });
    await act(async () => { map.loadStyle(); await vi.advanceTimersByTimeAsync(100); });
    expect(harness.image).toHaveBeenCalledTimes(2);
    await act(async () => {
      map.listeners.get('movestart')?.forEach(callback => (callback as (event: object) => void)({ originalEvent: {} }));
      await vi.advanceTimersByTimeAsync(4000);
    });
    expect(harness.image).toHaveBeenCalledTimes(2);
    expect(map.sources.has(imageSourceId)).toBe(true);
  });
});
