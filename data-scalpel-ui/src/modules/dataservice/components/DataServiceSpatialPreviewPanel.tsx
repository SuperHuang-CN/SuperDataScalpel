import { EditOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Drawer, Empty, Grid, Segmented, Space, Spin, Tag, Tooltip, message } from 'antd';
import maplibregl, { type Coordinates, type ImageSource, type Map as MapLibreMap } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import {
  SpatialStyleLegend,
  SpatialStyleWorkbench,
  spatialStylePreviewReadiness,
  buildWmsViewport, isScaleVisible, intersectScale,
  type FieldProfileRequest,
  type SpatialStyleDocument,
  type SpatialStyleMode,
} from '../../cartography';
import { ApiError } from '../../../shared/api/http';
import { CompactAlert, FloatingFeedback } from '../../../shared/components/ContextualFeedback';
import {
  fetchDataServiceSpatialPreviewMap,
  fetchDataServiceSpatialPreviewLegend,
  renderDataServiceSpatialStylePreview,
  renderUploadedDataServiceSpatialStylePreview,
  queryDataServiceSpatialStyleSld,
} from '../api/dataServiceApi';
import {
  useApplyDataServiceSpatialStyle, useDataServiceSpatialPreview, useDataServiceSpatialStyle,
  useProfileDataServiceSpatialStyleField, useUpdateDataServiceSpatialStyle, useUploadDataServiceSpatialSld,
} from '../hooks/useDataServices';
import type { DataServiceDeploymentStatus, DataServiceStatus } from '../model/dataService';

interface DataServiceSpatialPreviewPanelProps {
  serviceId: string;
  status: DataServiceStatus;
  deploymentStatus: DataServiceDeploymentStatus | null;
  definitionConfigured: boolean;
  canUpdate: boolean;
  canPublish: boolean;
  enableLoading: boolean;
  onEnable: () => void;
  onDirtyChange?: (dirty: boolean) => void;
}

interface SpatialStyleDraft {
  mode: SpatialStyleMode;
  document: SpatialStyleDocument | null;
  sldFile: File | null;
}

const SOURCE_ID = 'data-scalpel-service-wms-preview-source';
const LAYER_ID = 'data-scalpel-service-wms-preview-layer';
const WEB_MERCATOR_MAX_LATITUDE = 85.05112878;
const WEB_MERCATOR_RADIUS = 6_378_137;
const clamp = (value: number, minimum: number, maximum: number) => Math.min(maximum, Math.max(minimum, value));
const longitudeToMercator = (longitude: number) => WEB_MERCATOR_RADIUS * longitude * Math.PI / 180;
const latitudeToMercator = (latitude: number) => {
  const safe = clamp(latitude, -WEB_MERCATOR_MAX_LATITUDE, WEB_MERCATOR_MAX_LATITUDE);
  return WEB_MERCATOR_RADIUS * Math.log(Math.tan(Math.PI / 4 + safe * Math.PI / 360));
};
const mercatorToLongitude = (x: number) => x / WEB_MERCATOR_RADIUS * 180 / Math.PI;
const mercatorToLatitude = (y: number) => (2 * Math.atan(Math.exp(y / WEB_MERCATOR_RADIUS)) - Math.PI / 2) * 180 / Math.PI;

export const DataServiceSpatialPreviewPanel = (props: DataServiceSpatialPreviewPanelProps) => (
  <SpatialPreviewWorkspace key={props.serviceId} {...props} />
);

const SpatialPreviewWorkspace = ({
  serviceId, status, deploymentStatus, definitionConfigured, canUpdate, canPublish,
  enableLoading, onEnable, onDirtyChange,
}: DataServiceSpatialPreviewPanelProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const screens = Grid.useBreakpoint();
  const metadataQuery = useDataServiceSpatialPreview(serviceId, true);
  const styleQuery = useDataServiceSpatialStyle(serviceId, true);
  const updateMutation = useUpdateDataServiceSpatialStyle();
  const uploadMutation = useUploadDataServiceSpatialSld();
  const applyMutation = useApplyDataServiceSpatialStyle();
  const profileMutation = useProfileDataServiceSpatialStyleField();
  const metadata = metadataQuery.data;
  const storedStyle = styleQuery.data;
  const [styleDraft, setStyleDraft] = useState<SpatialStyleDraft | null>(null);
  const storedDocument = storedStyle?.styleDocument ?? storedStyle?.defaultStyleDocument ?? null;
  const mode = styleDraft?.mode ?? storedStyle?.mode ?? 'CARTOGRAPHY';
  const document = styleDraft?.document ?? storedDocument;
  const sldFile = styleDraft?.sldFile ?? null;
  const dirty = styleDraft !== null;
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [mapError, setMapError] = useState<string>();
  const [legendState, setLegendState] = useState<{
    inputVersion: string;
    url?: string;
    error: boolean;
  }>();
  const [mapReady, setMapReady] = useState(false);
  const [hasRendered, setHasRendered] = useState(false);
  const [viewportVersion, setViewportVersion] = useState(0);
  const [renderedInputVersion, setRenderedInputVersion] = useState<string>();
  const [previewSource, setPreviewSource] = useState<'LIVE' | 'DRAFT'>(canUpdate ? 'DRAFT' : 'LIVE');
  const effectiveSource = canUpdate ? previewSource : 'LIVE';
  const [renderedSource, setRenderedSource] = useState<string>();
  const [currentScale, setCurrentScale] = useState<number>();
  const [viewportError, setViewportError] = useState<string>();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const currentUrlRef = useRef<string | null>(null);
  const requestVersionRef = useRef(0);
  const renderInputVersionRef = useRef('');
  const resizeTimeoutRef = useRef<number | undefined>(undefined);
  const previewReadiness = spatialStylePreviewReadiness(document);
  const wantsCartographyDraft = effectiveSource === 'DRAFT' && mode === 'CARTOGRAPHY';
  const wantsUploadedDraft = effectiveSource === 'DRAFT' && mode === 'UPLOADED_SLD';
  const previewCartography = wantsCartographyDraft && Boolean(document);
  const hasUploadedDraft = Boolean(sldFile || storedStyle?.uploadedSldText);
  const renderInputVersion = JSON.stringify({
    viewportVersion,
    effectiveSource,
    mode,
    document,
    localSldFile: sldFile
      ? [sldFile.name, sldFile.size, sldFile.lastModified]
      : null,
    uploadedSldText: storedStyle?.uploadedSldText ?? null,
    sldFileName: storedStyle?.sldFileName ?? null,
    appliedStyleVersion: storedStyle?.appliedStyleVersion ?? null,
    appliedAt: storedStyle?.appliedAt ?? null,
  });
  const renderPending = renderedInputVersion !== renderInputVersion;
  const legendInputVersion = JSON.stringify({
    serviceId,
    available: metadata?.available ?? false,
    effectiveSource,
    appliedStyleVersion: storedStyle?.appliedStyleVersion ?? null,
    appliedAt: storedStyle?.appliedAt ?? null,
  });
  const legendUrl = legendState?.inputVersion === legendInputVersion
    ? legendState.url
    : undefined;
  const legendError = legendState?.inputVersion === legendInputVersion
    ? legendState.error
    : false;
  const legendEnabled = Boolean(metadata?.available && effectiveSource === 'LIVE');
  const renderDisabledReason = !metadata?.available
    ? '当前空间服务暂不可预览'
    : !mapReady
      ? '地图工作区正在初始化'
      : wantsCartographyDraft && !previewReadiness.ready
        ? previewReadiness.reason ?? '请先完成符号化配置'
        : wantsUploadedDraft && !hasUploadedDraft
          ? '请先上传 SLD 文件，再渲染草稿'
          : viewportError;
  const minimumWidth = metadata?.limits.minimumWidth ?? 256;
  const maximumWidth = metadata?.limits.maximumWidth ?? 1600;
  const minimumHeight = metadata?.limits.minimumHeight ?? 256;
  const maximumHeight = metadata?.limits.maximumHeight ?? 1200;
  const getViewport = useCallback(() => {
    const map = mapRef.current, container = containerRef.current;
    if (!map || !container) throw new Error('地图视口尚未就绪');
    const bounds = map.getBounds();
    if (bounds.getEast() <= bounds.getWest() || bounds.getEast() - bounds.getWest() >= 360) throw new Error('服务预览暂不支持跨日期变更线的视口');
    return buildWmsViewport([longitudeToMercator(bounds.getWest()), latitudeToMercator(bounds.getSouth()),
      longitudeToMercator(bounds.getEast()), latitudeToMercator(bounds.getNorth())], container.clientWidth, container.clientHeight,
    { minimumWidth, maximumWidth, minimumHeight, maximumHeight });
  }, [minimumWidth, maximumWidth, minimumHeight, maximumHeight]);
  useLayoutEffect(() => {
    renderInputVersionRef.current = renderInputVersion;
  }, [renderInputVersion]);
  const invalidateActiveRequest = useCallback(() => {
    requestVersionRef.current += 1;
    abortRef.current?.abort();
    setLoading(false);
    try { setCurrentScale(getViewport().scaleDenominator); setViewportError(undefined); }
    catch (error: unknown) { setCurrentScale(undefined); setViewportError(error instanceof Error ? error.message : '地图视口不可用'); }
  }, [getViewport]);
  const markViewportChanged = useCallback(() => {
    setViewportVersion((current) => current + 1);
    invalidateActiveRequest();
  }, [invalidateActiveRequest]);

  const querySld = useCallback(async (styleDocument: SpatialStyleDocument, signal: AbortSignal) => (
    await queryDataServiceSpatialStyleSld(serviceId, styleDocument, signal)
  ).sldText, [serviceId]);

  useEffect(() => {
    onDirtyChange?.(dirty);
    const warn = (event: BeforeUnloadEvent) => { if (dirty) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', warn);
    return () => { window.removeEventListener('beforeunload', warn); onDirtyChange?.(false); };
  }, [dirty, onDirtyChange]);

  const saveDraft = async () => {
    if (mode === 'CARTOGRAPHY' && !previewReadiness.ready) throw new Error(previewReadiness.reason ?? '样式配置不完整');
    if (mode === 'CARTOGRAPHY' && document) await updateMutation.mutateAsync({ id: serviceId, styleDocument: document, mode });
    else if (mode === 'UPLOADED_SLD' && sldFile) await uploadMutation.mutateAsync({ id: serviceId, file: sldFile });
    else if (mode === 'UPLOADED_SLD' && storedStyle?.sldFileName) await updateMutation.mutateAsync({ id: serviceId, styleDocument: null, mode });
    else throw new Error('当前样式草稿不完整');
    setStyleDraft(null); void messageApi.success('样式草稿已保存');
  };
  const applyStyle = async () => {
    await applyMutation.mutateAsync(serviceId);
    void messageApi.success('样式已应用到 GeoServer');
    await metadataQuery.refetch();
    invalidateActiveRequest();
  };
  const saveAndApply = async () => { await saveDraft(); await applyStyle(); };

  const requestImage = useCallback(async () => {
    const map = mapRef.current; const container = containerRef.current;
    if (!map || !container || !metadata?.available || !mapReady || renderDisabledReason) return;
    abortRef.current?.abort(); const controller = new AbortController(); abortRef.current = controller;
    const version = ++requestVersionRef.current;
    const requestedInputVersion = renderInputVersion;
    setLoading(true); setMapError(undefined);
    try {
      const viewport = getViewport();
      const { bbox, width, height } = viewport;
      const west = mercatorToLongitude(bbox[0]), south = mercatorToLatitude(bbox[1]);
      const east = mercatorToLongitude(bbox[2]), north = mercatorToLatitude(bbox[3]);
      const coordinates: Coordinates = [[west, north], [east, north], [east, south], [west, south]];
      const blob = effectiveSource === 'LIVE'
        ? await fetchDataServiceSpatialPreviewMap(serviceId, bbox, width, height, controller.signal)
        : mode === 'CARTOGRAPHY' && document
          ? await renderDataServiceSpatialStylePreview(serviceId, document, bbox, width, height, controller.signal)
          : await renderUploadedDataServiceSpatialStylePreview(serviceId,
            sldFile ?? new File([storedStyle?.uploadedSldText ?? ''], storedStyle?.sldFileName ?? 'draft.sld', { type: 'application/xml' }),
            bbox, width, height, controller.signal);
      if (controller.signal.aborted || version !== requestVersionRef.current
        || requestedInputVersion !== renderInputVersionRef.current
        || JSON.stringify(getViewport()) !== JSON.stringify(viewport)) return;
      const nextUrl = URL.createObjectURL(blob); const source = map.getSource(SOURCE_ID) as ImageSource | undefined;
      if (source) source.updateImage({ url: nextUrl, coordinates });
      else { map.addSource(SOURCE_ID, { type: 'image', url: nextUrl, coordinates }); map.addLayer({ id: LAYER_ID, type: 'raster', source: SOURCE_ID, paint: { 'raster-fade-duration': 0 } }); }
      const previous = currentUrlRef.current; currentUrlRef.current = nextUrl;
      if (previous) window.setTimeout(() => URL.revokeObjectURL(previous), 0);
      setHasRendered(true);
      setRenderedSource(`${effectiveSource === 'LIVE' ? '线上样式' : mode === 'CARTOGRAPHY' ? '在线制图草稿' : '上传 SLD 草稿'} · 1:${Math.round(viewport.scaleDenominator).toLocaleString()}`);
      if (requestedInputVersion === renderInputVersionRef.current) {
        setRenderedInputVersion(requestedInputVersion);
      }
    } catch (error: unknown) {
      if (!controller.signal.aborted) setMapError(error instanceof ApiError ? error.message : '加载 GeoServer WMS 预览失败');
    } finally { if (version === requestVersionRef.current) setLoading(false); }
  }, [document, mapReady, metadata?.available, effectiveSource, mode, renderDisabledReason,
    getViewport, renderInputVersion, serviceId, sldFile, storedStyle]);

  useEffect(() => {
    if (!legendEnabled) return;
    const controller = new AbortController();
    let url: string | undefined;
    void fetchDataServiceSpatialPreviewLegend(serviceId, controller.signal).then((blob) => {
      if (!controller.signal.aborted) {
        url = URL.createObjectURL(blob);
        setLegendState({ inputVersion: legendInputVersion, url, error: false });
      }
    }).catch(() => {
      if (!controller.signal.aborted) {
        setLegendState({ inputVersion: legendInputVersion, error: true });
      }
    });
    return () => { controller.abort(); if (url) URL.revokeObjectURL(url); };
  }, [legendEnabled, legendInputVersion, serviceId]);

  const [initialWest, initialSouth, initialEast, initialNorth] = metadata?.initialBounds ?? [];
  useEffect(() => {
    if (!containerRef.current || !metadata?.available || initialWest == null || initialSouth == null || initialEast == null || initialNorth == null || mapRef.current) return;
    setMapReady(false); setHasRendered(false); setRenderedSource(undefined);
    const map = new maplibregl.Map({ container: containerRef.current, style: { version: 8, sources: {}, layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#f3f5f7' } }] }, center: [0, 0], zoom: 1, pitch: 0, bearing: 0, dragRotate: false, pitchWithRotate: false, touchPitch: false, maxPitch: 0, renderWorldCopies: false, trackResize: false, attributionControl: false });
    map.touchZoomRotate.disableRotation(); map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right'); map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left');
    map.fitBounds([[initialWest, initialSouth], [initialEast, initialNorth]], { padding: 32, duration: 0 });
    map.on('load', () => { setMapReady(true); markViewportChanged(); }); map.on('movestart', markViewportChanged); map.on('moveend', markViewportChanged); mapRef.current = map;
    const observer = new ResizeObserver(() => { window.clearTimeout(resizeTimeoutRef.current); resizeTimeoutRef.current = window.setTimeout(() => { if (mapRef.current === map && containerRef.current?.clientWidth && containerRef.current.clientHeight) { map.resize(); markViewportChanged(); } }, 250); });
    observer.observe(containerRef.current);
    return () => { observer.disconnect(); window.clearTimeout(resizeTimeoutRef.current); abortRef.current?.abort(); map.remove(); mapRef.current = null; if (currentUrlRef.current) URL.revokeObjectURL(currentUrlRef.current); currentUrlRef.current = null; };
  }, [markViewportChanged, metadata?.available, initialWest, initialSouth, initialEast, initialNorth]);

  const resetView = () => {
    if (!metadata || !mapRef.current) return;
    markViewportChanged();
    mapRef.current.fitBounds([[metadata.initialBounds[0], metadata.initialBounds[1]], [metadata.initialBounds[2], metadata.initialBounds[3]]], { padding: 32, duration: 0 });
  };
  const canEnable = canPublish && definitionConfigured && status !== 'ENABLED';
  const retrying = deploymentStatus === 'FAILED' || deploymentStatus === 'PENDING';
  const mapState = metadataQuery.isPending ? <Spin tip="正在检查 GeoServer 图层…" /> : metadataQuery.error
    ? <CompactAlert type="error" title="空间服务预览检查失败" description={metadataQuery.error.message} action={<Button size="small" onClick={() => void metadataQuery.refetch()}>重试</Button>} />
    : !metadata?.available ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={metadata?.message ?? '当前空间服务暂不可预览'}><Space>{canEnable && <Button type="primary" loading={enableLoading} onClick={onEnable}>{retrying ? '重试启用' : '启用服务'}</Button>}<Button onClick={() => void metadataQuery.refetch()}>刷新状态</Button></Space></Empty> : null;
  const handleError = (error: unknown, fallback: string) => void messageApi.error(error instanceof Error ? error.message : fallback);
  const updateStyleDraft = (
    nextMode: SpatialStyleMode,
    nextDocument: SpatialStyleDocument | null,
    nextSldFile: File | null,
  ) => {
    invalidateActiveRequest();
    const documentChanged = JSON.stringify(nextDocument) !== JSON.stringify(storedDocument);
    const nextDirty = nextMode !== storedStyle?.mode
      || (nextMode === 'CARTOGRAPHY' && documentChanged)
      || (nextMode === 'UPLOADED_SLD' && Boolean(nextSldFile));
    setStyleDraft(nextDirty ? {
      mode: nextMode,
      document: nextDocument,
      sldFile: nextSldFile,
    } : null);
  };
  const changeMode = (next: SpatialStyleMode) => {
    updateStyleDraft(next, document, sldFile);
  };
  const editor = storedStyle ? <SpatialStyleWorkbench
    key={serviceId}
    mode={mode} geometryFamily={storedStyle.geometryFamily} fields={storedStyle.fields} value={document} defaultDocument={storedStyle.defaultStyleDocument}
    file={sldFile} fileName={storedStyle.sldFileName} fileSize={storedStyle.sldFileSize} styleVersion={storedStyle.styleVersion} appliedStyleVersion={storedStyle.appliedStyleVersion}
    syncStatus={storedStyle.syncStatus} syncError={storedStyle.syncError} deployed={storedStyle.deployed} dirty={dirty} editable={canUpdate} applicable={canPublish}
    saving={updateMutation.isPending || uploadMutation.isPending} applying={applyMutation.isPending}
    uploadedSldText={storedStyle.uploadedSldText} onQuerySld={querySld}
    currentScale={currentScale}
    onModeChange={changeMode} onChange={(next) => updateStyleDraft(mode, next, sldFile)}
    onFileChange={(file) => {
      if (!/\.(sld|xml)$/i.test(file.name)) { void messageApi.error('仅支持 .sld 或 .xml 文件'); return; }
      if (file.size === 0 || file.size > 512 * 1024) { void messageApi.error('SLD 文件不能为空且不能超过 512KB'); return; }
      updateStyleDraft('UPLOADED_SLD', document, file);
    }}
    onProfileField={(request: FieldProfileRequest) => profileMutation.mutateAsync({ id: serviceId, request })}
    onRestoreDefault={() => updateStyleDraft('CARTOGRAPHY', storedStyle.defaultStyleDocument, sldFile)}
    onSave={() => void saveDraft().catch((error) => handleError(error, '保存样式失败'))} onSaveAndApply={() => void saveAndApply().catch((error) => handleError(error, '保存并应用样式失败'))} onApply={() => void applyStyle().catch((error) => handleError(error, '应用样式失败'))}
  /> : styleQuery.error ? <CompactAlert type="error" title="读取空间样式失败" description={styleQuery.error.message} action={<Button size="small" onClick={() => void styleQuery.refetch()}>重试</Button>} /> : <Spin tip="正在加载样式…" />;

  const outsideSymbols = previewCartography && document && currentScale != null && !isScaleVisible(document.scaleRange, currentScale);
  const outsideLabels = previewCartography && document?.labeling.enabled && currentScale != null
    && !isScaleVisible(intersectScale(document.scaleRange, document.labeling.scaleRange), currentScale);

  return <div className="data-service-detail-tab-panel data-service-spatial-preview-panel">{messageContext}
    <div className="data-service-spatial-preview-main"><div className="data-service-spatial-preview-map-column">
      <div className="data-service-spatial-preview-toolbar">
        <Space size={8} wrap><Tag color="blue">WMS</Tag><code>{metadata?.qualifiedLayerName ?? '空间服务尚未发布'}</code>
          {currentScale != null && <Tooltip title="根据实际 GetMap 范围、图片尺寸和 OGC 0.28mm 像元计算"><span>请求比例尺 1:{Math.round(currentScale).toLocaleString()}</span></Tooltip>}
          {renderedSource && <Tag>当前图片：{renderedSource}</Tag>}
          {renderPending && metadata?.available && <Tag color="gold">当前选择待渲染</Tag>}
          {outsideSymbols && <Tag color="warning">草稿符号超出可见比例尺</Tag>}
          {outsideLabels && <Tag color="warning">草稿标注超出可见比例尺</Tag>}
        </Space>
        <Space size={6} wrap>
          <Segmented<'LIVE' | 'DRAFT'> value={effectiveSource} disabled={!canUpdate} options={[{ value: 'LIVE', label: '线上样式' }, { value: 'DRAFT', label: '当前草稿' }]}
            onChange={next => { invalidateActiveRequest(); setPreviewSource(next); }} />
          {!screens.lg && <Button size="small" icon={<EditOutlined />} onClick={() => setDrawerOpen(true)}>在线配图</Button>}
          <Button size="small" disabled={!metadata?.available} onClick={resetView}>复位</Button>
          <Tooltip title={renderDisabledReason ?? '仅在点击后请求 GeoServer 渲染当前地图视图'}><span><Button size="small" icon={<ReloadOutlined />}
            disabled={Boolean(renderDisabledReason)} loading={loading} onClick={() => void requestImage()}>渲染当前视图</Button></span></Tooltip>
        </Space>
      </div>
      <div className="data-service-spatial-preview-map-wrap">
        {mapState && <div className="data-service-spatial-preview-map-state">{mapState}</div>}
        <div ref={containerRef} className="data-service-spatial-preview-map" />
        {mapError && <FloatingFeedback type="warning" title="WMS 预览加载失败" description={mapError}
          action={<Button size="small" disabled={Boolean(renderDisabledReason)} onClick={() => void requestImage()}>重试</Button>} closable onClose={() => setMapError(undefined)} />}
        {metadata?.available && renderPending && !loading && !mapError && <div className="data-service-spatial-preview-manual-hint">
          {renderDisabledReason ?? (hasRendered ? '当前视图或样式已变化，请手动渲染' : '点击“渲染当前视图”加载地图')}
        </div>}
        {metadata?.available && <div className="data-service-spatial-preview-legend">
          {effectiveSource === 'LIVE' ? legendUrl ? <img src={legendUrl} alt="GeoServer 线上图例" /> : <span>{legendError ? '线上图例暂不可用' : '正在加载线上图例…'}</span>
            : mode === 'CARTOGRAPHY' ? <><span>当前草稿图例（示意）</span><SpatialStyleLegend document={document} /></>
              : <span>上传草稿以 WMS 渲染为准，不展示线上图例</span>}
        </div>}
        {loading && <div className="data-service-spatial-preview-loading"><Spin size="small" /> 正在渲染当前视图…</div>}
      </div>
    </div>{screens.lg && <aside className="data-service-spatial-style-column">{editor}</aside>}</div>
    <Drawer open={!screens.lg && drawerOpen} width={440} title="空间服务在线配图" className="business-drawer" onClose={() => setDrawerOpen(false)}>{editor}</Drawer>
  </div>;
};
