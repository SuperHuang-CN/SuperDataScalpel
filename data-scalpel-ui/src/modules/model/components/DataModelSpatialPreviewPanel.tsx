import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { ReloadOutlined } from '@ant-design/icons';
import { Button, Select, Space, Spin, Tag } from 'antd';
import maplibregl, { type Coordinates, type ImageSource, type Map as MapLibreMap } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { fetchDataModelSpatialPreviewMap } from '../api/dataModelApi';
import {
  useDataModelSpatialPreview,
  useRefreshDataModelPhysicalStatistics,
} from '../hooks/useDataModels';
import type { DataModelSpatialPreviewMap } from '../model/dataModel';

interface DataModelSpatialPreviewPanelProps {
  modelId: string;
}

const SOURCE_ID = 'data-scalpel-spatial-preview-image';
const LAYER_ID = 'data-scalpel-spatial-preview-layer';
const WEB_MERCATOR_MAX_LATITUDE = 85.05112878;
const WEB_MERCATOR_RADIUS = 6_378_137;

const clamp = (value: number, minimum: number, maximum: number) => Math.min(maximum, Math.max(minimum, value));

const longitudeToMercator = (longitude: number) => WEB_MERCATOR_RADIUS * longitude * Math.PI / 180;

const latitudeToMercator = (latitude: number) => {
  const safeLatitude = clamp(latitude, -WEB_MERCATOR_MAX_LATITUDE, WEB_MERCATOR_MAX_LATITUDE);
  return WEB_MERCATOR_RADIUS * Math.log(Math.tan(Math.PI / 4 + safeLatitude * Math.PI / 360));
};

export const DataModelSpatialPreviewPanel = ({ modelId }: DataModelSpatialPreviewPanelProps) => {
  const metadataQuery = useDataModelSpatialPreview(modelId, true);
  const refreshStatisticsMutation = useRefreshDataModelPhysicalStatistics();
  const metadata = metadataQuery.data;
  const availableFields = useMemo(
    () => metadata?.geometryFields.filter((field) => field.previewAllowed) ?? [],
    [metadata],
  );
  const [requestedGeometryField, setGeometryField] = useState<string>();
  const geometryField = availableFields.some((field) => field.code === requestedGeometryField)
    ? requestedGeometryField
    : availableFields[0]?.code;
  const [loading, setLoading] = useState(false);
  const [mapError, setMapError] = useState<string>();
  const [mapResult, setMapResult] = useState<DataModelSpatialPreviewMap>();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | undefined>(undefined);
  const abortRef = useRef<AbortController | undefined>(undefined);
  const currentUrlRef = useRef<string | undefined>(undefined);
  const requestVersionRef = useRef(0);
  const resizeTimeoutRef = useRef<number | undefined>(undefined);
  const requestImageRef = useRef<() => Promise<void>>(async () => undefined);

  const requestImage = useCallback(async () => {
    const map = mapRef.current;
    const container = containerRef.current;
    if (!map || !container || !geometryField || !metadata) return;
    const bounds = map.getBounds();
    const west = bounds.getWest();
    const east = bounds.getEast();
    if (east < west || east - west >= 360) {
      setMapError('空间预览暂不支持跨日期变更线的视口');
      return;
    }
    const north = clamp(bounds.getNorth(), -WEB_MERCATOR_MAX_LATITUDE, WEB_MERCATOR_MAX_LATITUDE);
    const south = clamp(bounds.getSouth(), -WEB_MERCATOR_MAX_LATITUDE, WEB_MERCATOR_MAX_LATITUDE);
    const width = clamp(Math.round(container.clientWidth), metadata.limits.minimumWidth, metadata.limits.maximumWidth);
    const height = clamp(Math.round(container.clientHeight), metadata.limits.minimumHeight, metadata.limits.maximumHeight);
    const bbox: [number, number, number, number] = [
      longitudeToMercator(west), latitudeToMercator(south),
      longitudeToMercator(east), latitudeToMercator(north),
    ];
    const coordinates: Coordinates = [[west, north], [east, north], [east, south], [west, south]];
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const version = ++requestVersionRef.current;
    setLoading(true);
    setMapError(undefined);
    try {
      const result = await fetchDataModelSpatialPreviewMap(
        modelId, geometryField, bbox, width, height, controller.signal,
      );
      if (version !== requestVersionRef.current || controller.signal.aborted) return;
      const nextUrl = URL.createObjectURL(result.blob);
      const source = map.getSource(SOURCE_ID) as ImageSource | undefined;
      if (source) {
        source.updateImage({ url: nextUrl, coordinates });
      } else {
        map.addSource(SOURCE_ID, { type: 'image', url: nextUrl, coordinates });
        map.addLayer({
          id: LAYER_ID,
          type: 'raster',
          source: SOURCE_ID,
          paint: { 'raster-fade-duration': 0 },
        });
      }
      const previousUrl = currentUrlRef.current;
      currentUrlRef.current = nextUrl;
      if (previousUrl) window.setTimeout(() => URL.revokeObjectURL(previousUrl), 0);
      setMapResult(result);
    } catch (error: unknown) {
      if (controller.signal.aborted) return;
      setMapError(error instanceof ApiError ? error.message : '加载空间预览图片失败');
    } finally {
      if (version === requestVersionRef.current) setLoading(false);
    }
  }, [geometryField, metadata, modelId]);

  useEffect(() => {
    requestImageRef.current = requestImage;
  }, [requestImage]);

  useEffect(() => {
    if (!containerRef.current || !metadata?.supported || availableFields.length === 0 || mapRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: {
        version: 8,
        sources: {},
        layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#f3f5f7' } }],
      },
      center: [116.05, 27.3],
      zoom: 6,
      pitch: 0,
      bearing: 0,
      dragRotate: false,
      pitchWithRotate: false,
      touchPitch: false,
      maxPitch: 0,
      renderWorldCopies: false,
      trackResize: false,
      attributionControl: false,
    });
    map.touchZoomRotate.disableRotation();
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
    map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left');
    map.fitBounds(
      [[metadata.initialBounds[0], metadata.initialBounds[1]], [metadata.initialBounds[2], metadata.initialBounds[3]]],
      { padding: 24, duration: 0 },
    );
    const refreshImage = () => void requestImageRef.current();
    map.on('moveend', refreshImage);
    map.on('load', refreshImage);
    mapRef.current = map;
    const observer = new ResizeObserver(() => {
      window.clearTimeout(resizeTimeoutRef.current);
      resizeTimeoutRef.current = window.setTimeout(() => {
        if (
          mapRef.current !== map
          || !containerRef.current
          || containerRef.current.clientWidth <= 0
          || containerRef.current.clientHeight <= 0
        ) return;
        map.resize();
      }, 250);
    });
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      window.clearTimeout(resizeTimeoutRef.current);
      abortRef.current?.abort();
      map.remove();
      mapRef.current = undefined;
      if (currentUrlRef.current) URL.revokeObjectURL(currentUrlRef.current);
      currentUrlRef.current = undefined;
    };
  }, [availableFields.length, metadata]);

  useEffect(() => {
    if (mapRef.current?.loaded() && geometryField) void requestImage();
  }, [geometryField, requestImage]);

  const selectedField = metadata?.geometryFields.find((field) => field.code === geometryField);
  const statisticsRefreshRequired = metadata?.geometryFields.some(
    (field) => field.physicalStatisticsRefreshRequired,
  ) ?? false;

  const refreshPhysicalStatistics = async () => {
    setMapError(undefined);
    try {
      await refreshStatisticsMutation.mutateAsync(modelId);
      await metadataQuery.refetch();
    } catch (error: unknown) {
      setMapError(error instanceof ApiError ? error.message : '模型物理统计刷新失败');
    }
  };

  const resetView = () => {
    if (!mapRef.current || !metadata) return;
    mapRef.current.fitBounds(
      [[metadata.initialBounds[0], metadata.initialBounds[1]], [metadata.initialBounds[2], metadata.initialBounds[3]]],
      { padding: 24, duration: 0 },
    );
  };

  if (metadataQuery.isPending) return <Spin tip="正在检查 PostGIS 空间预览能力…" />;
  if (metadataQuery.error) {
    return <Alert type="warning" showIcon title="空间预览能力检查失败" description={metadataQuery.error.message} />;
  }
  if (!metadata?.supported || availableFields.length === 0) {
    const fieldMessage = metadata?.geometryFields.find((field) => field.message)?.message;
    return (
      <Alert
        type="info"
        showIcon
        title="当前模型暂不能空间预览"
        description={mapError ?? metadata?.message ?? fieldMessage ?? '没有可预览的 Geometry 字段'}
        action={(
          <Space size={6}>
            {statisticsRefreshRequired && (
              <Button
                size="small"
                loading={refreshStatisticsMutation.isPending}
                onClick={() => void refreshPhysicalStatistics()}
              >
                刷新物理统计
              </Button>
            )}
            <Button size="small" onClick={() => void metadataQuery.refetch()}>重新检查</Button>
          </Space>
        )}
      />
    );
  }

  return (
    <div className="model-spatial-preview">
      <div className="model-tab-toolbar">
        <Space size={8}>
          <span>Geometry 字段</span>
          <Select
            size="small"
            value={geometryField}
            options={metadata.geometryFields.map((field) => ({
              value: field.code,
              label: `${field.name}（${field.kind} · ${field.sourceCrs.authority}:${field.sourceCrs.code}）`,
              disabled: !field.previewAllowed,
            }))}
            onChange={setGeometryField}
            style={{ minWidth: 280 }}
          />
          {selectedField?.spatialIndexAvailable
            ? <Tag color="green">空间索引可用</Tag>
            : <Tag color="orange">无空间索引 · 小表受限预览</Tag>}
          {selectedField?.estimatedRowCount !== null && selectedField?.estimatedRowCount !== undefined && (
            <span className="model-preview-caption">估算 {selectedField.estimatedRowCount.toLocaleString()} 行</span>
          )}
        </Space>
        <Space size={6}>
          {statisticsRefreshRequired && (
            <Button
              size="small"
              loading={refreshStatisticsMutation.isPending}
              onClick={() => void refreshPhysicalStatistics()}
            >
              刷新物理统计
            </Button>
          )}
          <Button size="small" onClick={resetView}>复位</Button>
          <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={() => void requestImage()}>刷新</Button>
        </Space>
      </div>
      {selectedField?.message && <Alert className="model-spatial-preview-alert" type="warning" showIcon message={selectedField.message} />}
      {mapError && <Alert className="model-spatial-preview-alert" type="warning" showIcon message={mapError} closable onClose={() => setMapError(undefined)} />}
      {mapResult?.truncated && (
        <Alert className="model-spatial-preview-alert" type="warning" showIcon message="当前视图图形过密，请继续放大" />
      )}
      <div className="model-spatial-preview-map-wrap">
        <div ref={containerRef} className="model-spatial-preview-map" />
        {loading && <div className="model-spatial-preview-loading"><Spin size="small" /> 正在渲染当前视图…</div>}
        <div className="model-spatial-preview-legend">
          <span><i className="point" />点</span>
          <span><i className="line" />线</span>
          <span><i className="polygon" />面</span>
          {mapResult && <span>{mapResult.featureCount} 个图形 · 跳过 {mapResult.skippedCount}</span>}
        </div>
      </div>
    </div>
  );
};
