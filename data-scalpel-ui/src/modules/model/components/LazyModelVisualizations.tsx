import { Spin } from 'antd';
import { lazy, Suspense, type ComponentProps } from 'react';

// Statistics and resource hooks also use the module entry point. Keep X6 and
// MapLibre behind the visualization boundary instead of loading them on home.
const LazyLineageGraphCanvas = lazy(async () => ({
  default: (await import('./LineageGraphCanvas')).LineageGraphCanvas,
}));
const LazySpatialPreviewPanel = lazy(async () => ({
  default: (await import('./DataModelSpatialPreviewPanel')).SpatialPreviewPanel,
}));

export const LineageGraphCanvas = (props: ComponentProps<typeof LazyLineageGraphCanvas>) => (
  <Suspense fallback={<Spin aria-label="正在加载血缘图" />}>
    <LazyLineageGraphCanvas {...props} />
  </Suspense>
);

export const SpatialPreviewPanel = (props: ComponentProps<typeof LazySpatialPreviewPanel>) => (
  <Suspense fallback={<Spin aria-label="正在加载空间预览" />}>
    <LazySpatialPreviewPanel {...props} />
  </Suspense>
);
