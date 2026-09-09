import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PanoramaViewer } from './PanoramaViewer';
import { fetchPanoramaImage } from '../api/panoramaApi';
vi.mock('pannellum', () => ({}));
vi.mock('../api/panoramaApi', () => ({ fetchPanoramaImage: vi.fn() }));

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

describe('PanoramaViewer resources', () => {
  it('destroys the old viewer, aborts requests and revokes blobs when version changes and on unmount', async () => {
    const destroy = vi.fn();
    const instance: PannellumViewer = { destroy, lookAt: () => instance, on: (event, callback) => { if (event === 'load') callback(); return instance; } };
    const viewer = vi.fn(() => instance); window.pannellum = { viewer };
    vi.stubGlobal('URL', class extends URL { static createObjectURL = vi.fn(() => 'blob:panorama-test'); static revokeObjectURL = vi.fn(); });
    vi.mocked(fetchPanoramaImage).mockResolvedValue(new Blob(['image'], { type: 'image/jpeg' }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const component = (version: number) => <QueryClientProvider client={client}><PanoramaViewer id="image-id" version={version} /></QueryClientProvider>;
    const rendered = render(component(1)); await waitFor(() => expect(viewer).toHaveBeenCalledTimes(1));
    const firstSignal = vi.mocked(fetchPanoramaImage).mock.calls[0][3];
    rendered.rerender(component(2)); await waitFor(() => expect(viewer).toHaveBeenCalledTimes(2));
    expect(firstSignal?.aborted).toBe(true); expect(destroy).toHaveBeenCalledTimes(1); expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1);
    rendered.unmount(); expect(destroy).toHaveBeenCalledTimes(2); expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
    expect(viewer.mock.calls[0]).toBeDefined();
    client.clear();
  });
});
