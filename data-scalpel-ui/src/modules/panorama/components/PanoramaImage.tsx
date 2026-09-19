import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Spin } from 'antd';
import { ApiError } from '../../../shared/api/http';
import { fetchPanoramaImage } from '../api/panoramaApi';
export const PanoramaImage = ({ id, version }: { id: string; version: number }) => {
  const [image, setImage] = useState<{ key: string; url: string }>();
  const [error, setError] = useState<string>(); const [attempt, setAttempt] = useState(0);
  const client = useQueryClient(); const key = `${id}:${version}:${attempt}`;
  useEffect(() => {
    const controller = new AbortController(); let url: string | undefined;
    void fetchPanoramaImage(id, version, 'thumbnail', controller.signal).then(blob => {
      if (controller.signal.aborted) return; url = URL.createObjectURL(blob); setImage({ key, url });
    }).catch((e: unknown) => {
      if (controller.signal.aborted) return;
      if (e instanceof ApiError && e.problem?.code === 'PANORAMA_CONTENT_CHANGED') void client.invalidateQueries({ queryKey: ['panoramas'] });
      setError(key);
    });
    return () => { controller.abort(); if (url) URL.revokeObjectURL(url); };
  }, [client, id, version, key]);
  return image?.key === key ? <img className="panorama-thumbnail" src={image.url} alt="全景缩略图" /> : error === key ? <Button size="small" onClick={() => setAttempt(attempt + 1)}>重试缩略图</Button> : <Spin size="small" />;
};
