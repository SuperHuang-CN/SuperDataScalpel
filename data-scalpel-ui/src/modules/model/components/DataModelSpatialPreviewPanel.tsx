import { AimOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Select, Space, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import maplibregl, { type Coordinates, type ImageSource, type Map as MapLibreMap } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useMapConfiguration } from '../../system';
import { preparePreview, previewImage, previewMetadata, previewStatus } from '../api/spatialPreviewApi';
import { fetchDataModelReferences } from '../api/dataModelApi';
import { expandBounds, hasSpatialPreviewImage, PreviewImageCache, toLatitude, toLongitude, toX, toY, type PreviewBounds, type PreviewImage } from '../model/spatialPreview';
import './DataModelSpatialPreviewPanel.css';

const SOURCE = 'data-scalpel-spatial-preview-image';
const LAYER = 'data-scalpel-spatial-preview-layer';
const BASEMAP = 'data-scalpel-preview-basemap';
const RESTORATION_RETRY_DELAYS = [2000, 4000, 8000, 16000, 20000, 20000, 20000, 20000, 20000];
const errorText = (error: unknown) => error instanceof Error ? error.message : '地图加载失败，请重试';
const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

export const DataModelSpatialPreviewPanel = ({ modelId }: { modelId: string }) => <SpatialPreviewPanel key={modelId} modelId={modelId} basePath={`/v1/models/${modelId}`} />;

/** The file and model entries use the same loading, viewport and version lifecycle. */
export const SpatialPreviewPanel = ({ basePath, modelId }: { basePath: string; modelId?: string }) => {
  const client = useQueryClient();
  const metadataQuery = useQuery({ queryKey: ['spatial-preview-metadata', basePath], queryFn: ({ signal }) => previewMetadata(basePath, signal), staleTime: 30_000 });
  const metadata = metadataQuery.data;
  const availableFields = metadata?.geometryFields.filter((field) => field.previewAllowed) ?? [];
  const [requestedField, setRequestedField] = useState<string>();
  const field = availableFields.find((candidate) => candidate.code === requestedField)?.code ?? availableFields[0]?.code;
  const statusKey = ['spatial-preview-status', basePath, field];
  const statusQuery = useQuery({ queryKey: statusKey, queryFn: ({ signal }) => previewStatus(basePath, field!, signal), enabled: Boolean(field), staleTime: 0,
    refetchInterval: (query) => query.state.data?.state === 'READY' ? 15_000 : 2000, retry: 1 });
  const status = statusQuery.data;
  const displayable = hasSpatialPreviewImage(status);
  const references = useQuery({ queryKey: ['spatial-preview-services', modelId], queryFn: () => fetchDataModelReferences(modelId!), enabled: Boolean(modelId) && status?.state === 'LIMIT_EXCEEDED' });
  const services = references.data?.services.filter((service) => service.type === 'SPATIAL_SERVICE' && service.status === 'ENABLED') ?? [];
  const basemapQuery = useMapConfiguration();
  const [basemapError, setBasemapError] = useState(false);
  const [mapReady, setMapReady] = useState(false);
  const [loading, setLoading] = useState(false);
  const [imageError, setImageError] = useState<string>();
  const [degraded, setDegraded] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | undefined>(undefined);
  const requestRef = useRef<() => void>(() => undefined);
  const abortRef = useRef<AbortController | undefined>(undefined);
  const sequence = useRef(0);
  const detailRetries = useRef(0);
  const restorationRetries = useRef(0);
  const clientId = useRef(crypto.randomUUID());
  const validity = useRef<string | undefined>(undefined);
  const interacted = useRef(false);
  const fitted = useRef(false);
  const timer = useRef<number | undefined>(undefined);
  const urls = useRef(new Set<string>());
  const cache = useRef(new PreviewImageCache());
  const automaticAttempt = useRef(0);
  const scope = `${basePath}:${field}:${displayable && !statusQuery.isError ? status.generation : ''}`;

  useEffect(() => {
    if (status?.generation) void client.invalidateQueries({ queryKey: ['spatial-preview-metadata', basePath] });
  }, [client, basePath, status?.generation]);

  const clearImage = useCallback(() => {
    detailRetries.current = 0;
    restorationRetries.current = 0;
    sequence.current += 1; abortRef.current?.abort();
    window.clearTimeout(timer.current); cache.current.clear();
    const map = mapRef.current;
    if (map?.getLayer(LAYER)) map.removeLayer(LAYER);
    if (map?.getSource(SOURCE)) map.removeSource(SOURCE);
    urls.current.forEach((url) => URL.revokeObjectURL(url)); urls.current.clear();
  }, []);
  const preparation = useMutation({ mutationFn: (input: { path: string; field: string; force: boolean }) => preparePreview(input.path, input.field, input.force),
    onSuccess: (result, input) => { client.setQueryData(['spatial-preview-status', input.path, input.field], result); },
  });
  const prepare = preparation.mutate;
  const preparing = preparation.isPending;
  useEffect(() => {
    if (field && status?.state === 'NOT_PREPARED' && !preparing && Date.now() - automaticAttempt.current > 2500) {
      automaticAttempt.current = Date.now(); prepare({ path: basePath, field, force: false });
    }
  }, [basePath, field, status?.state, statusQuery.dataUpdatedAt, preparing, prepare]);

  useLayoutEffect(() => {
    validity.current = scope; clearImage();
    return () => { validity.current = undefined; clearImage(); };
  }, [scope, clearImage]);

  const showImage = useCallback((image: PreviewImage) => {
    const map = mapRef.current;
    if (!map?.getLayer('background')) return;
    const [x1, y1, x2, y2] = image.bounds;
    const coordinates: Coordinates = [[toLongitude(x1), toLatitude(y2)], [toLongitude(x2), toLatitude(y2)], [toLongitude(x2), toLatitude(y1)], [toLongitude(x1), toLatitude(y1)]];
    const url = URL.createObjectURL(image.blob); urls.current.add(url);
    // A stalled basemap must not retain an unbounded number of obsolete image URLs.
    while (urls.current.size > 3) {
      const oldest = urls.current.values().next().value!;
      URL.revokeObjectURL(oldest); urls.current.delete(oldest);
    }
    const source = map.getSource(SOURCE) as ImageSource | undefined;
    if (source) source.updateImage({ url, coordinates });
    else {
      map.addSource(SOURCE, { type: 'image', url, coordinates });
      map.addLayer({ id: LAYER, type: 'raster', source: SOURCE, paint: { 'raster-fade-duration': 0 } });
    }
    setDegraded(image.degraded);
  }, []);

  const requestImage = useCallback(async () => {
    const map = mapRef.current, container = containerRef.current;
    if (!mapReady || !map || !container || !field || !hasSpatialPreviewImage(status) || validity.current !== scope || preparing) return;
    const viewport = map.getBounds();
    const bounds: PreviewBounds = [toX(viewport.getWest()), toY(viewport.getSouth()), toX(viewport.getEast()), toY(viewport.getNorth())];
    if (bounds[0] >= bounds[2] || bounds[1] >= bounds[3]) return;
    const width = clamp(Math.round(container.clientWidth * 1.3), 256, 1600), height = clamp(Math.round(container.clientHeight * 1.3), 256, 1200);
    const expanded = expandBounds(bounds);
    const resolution = (expanded[2] - expanded[0]) / width;
    window.clearTimeout(timer.current);
    abortRef.current?.abort(); const controller = new AbortController(); abortRef.current = controller;
    const current = ++sequence.current;
    const valid = () => !controller.signal.aborted && current === sequence.current && validity.current === scope;
    const cached = cache.current.find(status.generation, bounds, resolution);
    if (cached) { showImage(cached); setImageError(undefined); setLoading(false); return; }
    setLoading(true); setImageError(undefined);
    try {
      let overview = cache.current.overview(status.generation);
      if (!overview) {
        overview = await previewImage(basePath, field, status.generation, expanded, width, height, true, controller.signal, { clientId: clientId.current, sequence: current });
        if (!valid()) return; cache.current.put(overview); showImage(overview);
      }
      const full = status.imageBounds;
      if (full.length === 4 && (bounds[2] - bounds[0]) >= (full[2] - full[0]) * 0.75 && (bounds[3] - bounds[1]) >= (full[3] - full[1]) * 0.75) { showImage(overview); return; }
      const result = await previewImage(basePath, field, status.generation, expanded, width, height, false, controller.signal, { clientId: clientId.current, sequence: current });
      if (!valid()) return;
      cache.current.put(result); showImage(result);
      if (result.fallbackReason === 'RESTORING') {
        const delay = RESTORATION_RETRY_DELAYS[restorationRetries.current];
        if (delay !== undefined) {
          restorationRetries.current += 1;
          timer.current = window.setTimeout(() => { if (valid()) requestRef.current(); }, delay);
        } else setImageError('当前范围细节加载超时，已保留完整概览');
      } else if (['BUSY', 'SHARING'].includes(result.fallbackReason ?? '') && detailRetries.current < 4) {
        detailRetries.current += 1;
        timer.current = window.setTimeout(() => { if (valid()) requestRef.current(); }, 600 * detailRetries.current);
      }
    } catch (error) {
      if (!valid()) return;
      if (error instanceof ApiError && [403, 404, 409].includes(error.status ?? 0)) {
        validity.current = undefined; clearImage();
        void client.invalidateQueries({ queryKey: ['spatial-preview-status', basePath, field] });
      } else setImageError(errorText(error));
    } finally { if (current === sequence.current) setLoading(false); }
  }, [basePath, field, status, scope, preparing, mapReady, showImage, clearImage, client]);
  useLayoutEffect(() => { requestRef.current = () => void requestImage(); }, [requestImage]);

  useEffect(() => {
    if (!containerRef.current) return;
    const map = new maplibregl.Map({ container: containerRef.current,
      style: { version: 8, sources: {}, layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#f3f7f9' } }] },
      center: [105, 35], zoom: 3, dragRotate: false, pitchWithRotate: false, touchPitch: false, maxPitch: 0, renderWorldCopies: false, trackResize: false, attributionControl: false });
    mapRef.current = map; map.touchZoomRotate.disableRotation();
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
    map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left');
    map.on('sourcedata', (event) => {
      if (event.sourceId !== SOURCE || !event.isSourceLoaded) return;
      const latest = Array.from(urls.current).at(-1);
      urls.current.forEach((url) => { if (url !== latest) { URL.revokeObjectURL(url); urls.current.delete(url); } });
    });
    map.on('movestart', (event) => {
      detailRetries.current = 0;
      restorationRetries.current = 0;
      if (event.originalEvent) interacted.current = true;
      sequence.current += 1; abortRef.current?.abort(); window.clearTimeout(timer.current);
    });
    const schedule = () => { window.clearTimeout(timer.current); timer.current = window.setTimeout(() => requestRef.current(), 200); };
    map.on('moveend', schedule);
    map.on('load', () => { setMapReady(true); schedule(); });
    const observer = new ResizeObserver(() => { if (containerRef.current?.clientWidth && containerRef.current.clientHeight) map.resize(); });
    observer.observe(containerRef.current);
    return () => { observer.disconnect(); clearImage(); map.remove(); mapRef.current = undefined; };
  }, [clearImage]);

  useEffect(() => {
    const map = mapRef.current;
    if (!mapReady || !map || !displayable) return;
    if (status.state === 'READY') { detailRetries.current = 0; restorationRetries.current = 0; }
    if (!fitted.current && !interacted.current && status.bounds.length === 4) {
      fitted.current = true;
      map.fitBounds([[status.bounds[0], status.bounds[1]], [status.bounds[2], status.bounds[3]]], { padding: 32, duration: 0, maxZoom: 17 });
    }
    requestRef.current();
  }, [status?.generation, status?.state, status?.bounds, displayable, mapReady]);

  const basemap = basemapQuery.data;
  useEffect(() => {
    const map = mapRef.current;
    if (!mapReady || !map) return;
    if (map.getLayer(BASEMAP)) map.removeLayer(BASEMAP);
    if (map.getSource(BASEMAP)) map.removeSource(BASEMAP);
    if (basemap?.url) {
      map.addSource(BASEMAP, { type: 'raster', tiles: [basemap.url], tileSize: 256, maxzoom: basemap.maxZoom });
      map.addLayer({ id: BASEMAP, type: 'raster', source: BASEMAP }, map.getLayer(LAYER) ? LAYER : undefined);
    }
    const onError = (event: maplibregl.ErrorEvent) => { if ('sourceId' in event && event.sourceId === BASEMAP) setBasemapError(true); };
    map.on('error', onError);
    return () => { map.off('error', onError); };
  }, [mapReady, basemap?.url, basemap?.maxZoom]);

  const reload = () => {
    if (!field) return;
    // A preparation still sharing its complete snapshot is merged by the server.
    if (status?.state !== 'OVERVIEW_READY') { validity.current = undefined; clearImage(); }
    setImageError(undefined); prepare({ path: basePath, field, force: true });
  };
  const locate = () => {
    if (status?.bounds.length !== 4) return;
    mapRef.current?.fitBounds([[status.bounds[0], status.bounds[1]], [status.bounds[2], status.bounds[3]]], { padding: 32, duration: 0, maxZoom: 17 });
  };
  const error = metadataQuery.error ?? statusQuery.error ?? preparation.error;
  const failed = status?.state === 'FAILED' || status?.state === 'LIMIT_EXCEEDED' || status?.state === 'UNSUPPORTED';
  const waiting = preparing || status?.state === 'PREPARING' || status?.state === 'UPDATING' || status?.state === 'NOT_PREPARED';
  const fieldInfo = metadata?.geometryFields.find((item) => item.code === field);
  const unavailable = !metadataQuery.isPending && (!metadata?.supported || !field);
  return <section className="model-spatial-preview spatial-preview-workspace" aria-label="空间数据预览">
    <div className="spatial-preview-toolbar">
      <Space size={8} wrap>
        {metadata && metadata.geometryFields.length > 1 ? <Select aria-label="空间字段" value={field} onChange={(value) => { fitted.current = false; setRequestedField(value); }} options={metadata.geometryFields.map((item) => ({ value: item.code, label: item.name, disabled: !item.previewAllowed }))} />
          : <span>{fieldInfo?.name ?? '空间预览'}</span>}
        {fieldInfo && <span className="spatial-preview-caption">{fieldInfo.sourceCrs.authority}:{fieldInfo.sourceCrs.code}</span>}
        {displayable && <span className="spatial-preview-caption">{status.featureCount.toLocaleString('zh-CN')} 个要素</span>}
      </Space>
      <Space size={8} wrap>
        <Button size="small" icon={<AimOutlined />} onClick={locate} disabled={!displayable || status.bounds.length !== 4}>定位数据</Button>
        <Button size="small" icon={<ReloadOutlined />} onClick={reload} loading={preparing} disabled={!field || status?.state === 'PREPARING' || status?.state === 'UPDATING'}>重新加载数据</Button>
      </Space>
    </div>
    <div className="spatial-preview-stage">
      <div className="spatial-preview-map" ref={containerRef} aria-label="可拖动和缩放的地图" />
      <div className="spatial-preview-feedback" role="status" aria-live="polite">
        {(metadataQuery.isPending || waiting || loading) && <span><Spin size="small" /> {waiting ? status?.message ?? '正在准备地图' : loading ? '正在加载当前范围' : '正在检查空间数据'}</span>}
        {status?.state === 'OVERVIEW_READY' && !waiting && <span>{status.message || '概览已就绪，正在完成地图准备'}</span>}
        {error && <InlineFeedback tone="error" label={errorText(error)} action={<Button size="small" onClick={() => { void metadataQuery.refetch(); void statusQuery.refetch(); }}>重试</Button>} />}
        {unavailable && !error && <InlineFeedback tone="info" label={metadata?.message ?? metadata?.geometryFields[0]?.message ?? '没有可预览的空间字段'} />}
        {failed && !error && <InlineFeedback tone="warning" label={status.message} action={status.state === 'LIMIT_EXCEEDED' ? <Space wrap>{services.map((service) => <Button key={service.id} size="small" href={`/dataservice/${service.id}`}>{service.name}</Button>)}<Button size="small" href="/dataservice">查看空间服务</Button></Space> : undefined} />}
        {imageError && <InlineFeedback tone="error" label={imageError} action={<Button size="small" onClick={() => { detailRetries.current = 0; restorationRetries.current = 0; requestRef.current(); }}>重试</Button>} />}
        {!loading && !imageError && degraded && status?.state === 'READY' && <span>当前显示完整概览，放大可查看细节</span>}
        {displayable && status.featureCount === 0 && <span>没有可显示的空间要素</span>}
        {(basemapError || basemapQuery.error) && <span>底图暂不可用，仍可查看空间数据</span>}
      </div>
    </div>
    <div className="spatial-preview-footer">
      <span>{displayable && status.observedAt ? `数据读取于 ${new Date(status.observedAt).toLocaleTimeString('zh-CN')}` : waiting ? '准备期间可继续拖动、缩放或查看属性' : '拖动地图浏览，滚轮缩放'}</span>
      <span>{basemap?.attribution}</span>
    </div>
  </section>;
};
