import { useQuery } from '@tanstack/react-query';
import { Button, Space, Typography } from 'antd';
import maplibregl, { type GeoJSONSource, type Map as MapLibreMap } from 'maplibre-gl';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { fetchPanoramaMapConfig, fetchPanoramaMapPoints } from '../api/panoramaApi';
import type { MapBounds, PanoramaMapConfig, PanoramaQuery } from '../model/panorama';
import 'maplibre-gl/dist/maplibre-gl.css';
const emptyConfig: PanoramaMapConfig = { url: '', attribution: '', maxZoom: 18 };
export const PanoramaMap = ({ query, revision }: { query: PanoramaQuery; revision: number }) => {
  const host = useRef<HTMLDivElement>(null); const mapRef = useRef<MapLibreMap | null>(null); const navigate = useNavigate();
  const [bounds, setBounds] = useState<MapBounds>({ west: -180, south: -90, east: 180, north: 90 });
  const [ready, setReady] = useState(0); const [attempt, setAttempt] = useState(0); const [mapError, setMapError] = useState<string>();
  const configQuery = useQuery({ queryKey: ['panorama-map-config'], queryFn: ({ signal }) => fetchPanoramaMapConfig(signal), staleTime: 0, refetchOnMount: 'always' });
  const config = configQuery.data ?? emptyConfig;
  const points = useQuery({ queryKey: ['panoramas', 'map', query, bounds, revision], queryFn: ({ signal }) => fetchPanoramaMapPoints(query, bounds, signal) });
  useEffect(() => {
    if (!host.current) return;
    let map: MapLibreMap;
    let disposed = false; let popup: maplibregl.Popup | undefined;
    try {
      map = new maplibregl.Map({ container: host.current, center: [105, 25], zoom: 1, attributionControl: false,
        style: { version: 8, sources: config.url ? { basemap: { type: 'raster', tiles: [config.url], tileSize: 256, maxzoom: config.maxZoom } } : {},
          layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#eef1f8' } }, ...(config.url ? [{ id: 'basemap', type: 'raster' as const, source: 'basemap' }] : [])] } });
      mapRef.current = map; map.addControl(new maplibregl.NavigationControl(), 'top-right');
    } catch { queueMicrotask(() => { if (!disposed) setMapError('地图无法启动，请检查 WebGL 支持；可切换列表管理。'); }); return () => { disposed = true; }; }
    const updateBounds = () => {
      const box = map.getBounds(); const width = box.getEast() - box.getWest();
      const wrap = (value: number) => ((value + 180) % 360 + 360) % 360 - 180;
      setBounds({ west: width >= 360 ? -180 : wrap(box.getWest()), east: width >= 360 ? 180 : wrap(box.getEast()), south: Math.max(-90, box.getSouth()), north: Math.min(90, box.getNorth()) });
      popup?.remove();
    };
    map.on('load', () => {
      map.addSource('panoramas', { type: 'geojson', data: { type: 'FeatureCollection', features: [] }, cluster: true, clusterRadius: 45, clusterMaxZoom: 14 });
      map.addLayer({ id: 'clusters', type: 'circle', source: 'panoramas', filter: ['has', 'point_count'], paint: { 'circle-color': '#6c62d9', 'circle-radius': 19, 'circle-stroke-color': '#fff', 'circle-stroke-width': 2 } });
      map.addLayer({ id: 'points', type: 'circle', source: 'panoramas', filter: ['!', ['has', 'point_count']], paint: { 'circle-color': '#3f72df', 'circle-radius': 7, 'circle-stroke-color': '#fff', 'circle-stroke-width': 2 } });
      setReady(value => value + 1); updateBounds();
    });
    map.on('moveend', updateBounds);
    map.on('error', event => { if ('sourceId' in event && event.sourceId === 'basemap') { setMapError('底图加载失败，拍摄点仍可使用。'); if (map.getLayer('basemap')) map.setLayoutProperty('basemap', 'visibility', 'none'); } });
    map.on('click', 'clusters', event => {
      const feature = event.features?.[0]; const clusterId = feature?.properties.cluster_id;
      if (typeof clusterId !== 'number' || feature?.geometry.type !== 'Point') return;
      const coordinates = feature.geometry.coordinates;
      const source = map.getSource('panoramas') as GeoJSONSource;
      void source.getClusterExpansionZoom(clusterId).then(zoom => { if (!disposed) map.easeTo({ center: [coordinates[0], coordinates[1]], zoom }); }).catch(() => { if (!disposed) setMapError('拍摄点已更新，请重新选择聚合点。'); });
    });
    map.on('mouseenter', 'clusters', event => {
      map.getCanvas().style.cursor = 'pointer';
      const count: unknown = event.features?.[0]?.properties.point_count;
      map.getCanvas().title = typeof count === 'number' ? `此聚合包含 ${count} 个已加载拍摄点，点击展开` : '点击展开拍摄点';
    });
    map.on('click', 'points', event => {
      const feature = event.features?.[0]; if (!feature || feature.geometry.type !== 'Point') return;
      const id: unknown = feature.properties.id, name: unknown = feature.properties.name;
      if (typeof id !== 'string' || typeof name !== 'string') return;
      const content = document.createElement('div'); const title = document.createElement('strong'); title.textContent = name; content.append(title);
      const time: unknown = feature.properties.captureTime;
      if (typeof time === 'string' && time) { const detail = document.createElement('p'); detail.textContent = time.replace('T', ' '); content.append(detail); }
      const button = document.createElement('button'); button.textContent = '浏览全景'; button.onclick = () => navigate(`/panorama/${id}`); content.append(button);
      popup?.remove(); popup = new maplibregl.Popup().setLngLat(event.lngLat).setDOMContent(content).addTo(map);
    });
    map.on('mouseenter', 'points', () => { map.getCanvas().style.cursor = 'pointer'; });
    for (const layer of ['points', 'clusters']) map.on('mouseleave', layer, () => { map.getCanvas().style.cursor = ''; map.getCanvas().title = ''; });
    const resize = new ResizeObserver(() => map.resize()); resize.observe(host.current);
    return () => { disposed = true; resize.disconnect(); popup?.remove(); map.remove(); mapRef.current = null; };
  }, [config.url, config.maxZoom, attempt, navigate]);
  useEffect(() => {
    const source = mapRef.current?.getSource('panoramas') as GeoJSONSource | undefined;
    source?.setData({ type: 'FeatureCollection', features: (points.data?.points ?? []).map(point => ({ type: 'Feature', geometry: { type: 'Point', coordinates: [point.longitude, point.latitude] }, properties: { ...point } })) });
  }, [points.data, ready]);
  return <div className="panorama-map-panel">
    <div className="panorama-map-summary"><Space wrap><Typography.Text>视口匹配 {points.data?.totalElements ?? '—'} 项 · 已加载 {points.data?.points.length ?? 0} 点</Typography.Text>
      {points.data?.truncated && <InlineFeedback tone="warning" label="已达 500 点上限，请缩小范围或筛选；聚合仅包含已加载点。" />}
      {!config.url && <Typography.Text type="secondary">未配置底图</Typography.Text>}
      {points.isError && <InlineFeedback tone="error" label="拍摄点加载失败" action={<Button size="small" onClick={() => void points.refetch()}>重试</Button>} />}
      {configQuery.isError && <InlineFeedback tone="error" label="底图配置读取失败" action={<Button size="small" onClick={() => void configQuery.refetch()}>重试</Button>} />}
      {mapError && <InlineFeedback tone="warning" label={mapError} action={<Button size="small" onClick={() => { setMapError(undefined); setAttempt(attempt + 1); }}>重试地图</Button>} />}
    </Space></div><div ref={host} className="panorama-map" aria-label="全景拍摄点地图" />
    {config.attribution && <div className="panorama-map-attribution">{config.attribution}</div>}
  </div>;
};
