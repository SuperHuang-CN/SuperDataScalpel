import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Spin } from 'antd';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { fetchPanoramaImage } from '../api/panoramaApi';
import 'pannellum/build/pannellum.css';
export const PanoramaViewer = ({ id, version }: { id: string; version: number }) => {
  const container = useRef<HTMLDivElement>(null); const viewer = useRef<PannellumViewer | null>(null);
  const [attempt, setAttempt] = useState(0); const [state, setState] = useState<{ key: string; error?: string; ready?: boolean }>();
  const client = useQueryClient(); const key = `${id}:${version}:${attempt}`;
  useEffect(() => {
    const controller = new AbortController(); let url: string | undefined; let instance: PannellumViewer | undefined;
    const load = async () => {
      await import('pannellum');
      const blob = await fetchPanoramaImage(id, version, 'preview', controller.signal);
      if (controller.signal.aborted || !container.current) return;
      url = URL.createObjectURL(blob);
      instance = window.pannellum.viewer(container.current, { type: 'equirectangular', panorama: url,
        autoLoad: true, autoRotate: 0, orientationOnByDefault: false, showControls: true, showFullscreenCtrl: true,
        compass: false, pitch: 0, yaw: 0, hfov: 100, escapeHTML: true,
        strings: { loadingLabel: '正在加载全景…', genericWebGLError: '无法启动全景查看器，请检查 WebGL 支持。', noWebGLError: '浏览器不支持 WebGL。', fileAccessError: '全景图片读取失败。' } });
      viewer.current = instance;
      instance.on('load', () => { if (!controller.signal.aborted) setState({ key, ready: true }); });
      instance.on('error', () => { if (!controller.signal.aborted) setState({ key, error: '全景加载失败，请检查浏览器 WebGL 支持并重试。' }); });
    };
    void load().catch((e: unknown) => {
      if (controller.signal.aborted) return;
      if (e instanceof ApiError && e.problem?.code === 'PANORAMA_CONTENT_CHANGED') void client.invalidateQueries({ queryKey: ['panoramas'] });
      setState({ key, error: e instanceof ApiError ? e.message : '全景查看器加载失败，请重试。' });
    });
    return () => { controller.abort(); instance?.destroy(); viewer.current = null; if (url) URL.revokeObjectURL(url); };
  }, [client, id, version, key]);
  const current = state?.key === key ? state : undefined;
  return <div className="panorama-viewer-shell">
    <div className="panorama-viewer" ref={container} aria-label="360 度全景浏览" />
    {!current?.ready && !current?.error && <div className="panorama-viewer-loading"><Spin tip="正在加载全景" /></div>}
    {current?.error && <div className="panorama-viewer-feedback"><InlineFeedback tone="error" label={current.error} /><Button onClick={() => setAttempt(attempt + 1)}>重试</Button></div>}
    <Button className="panorama-viewer-reset" disabled={!current?.ready} onClick={() => viewer.current?.lookAt(0, 0, 100, false)}>复位视角</Button>
  </div>;
};
