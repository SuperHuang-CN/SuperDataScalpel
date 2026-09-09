import type { ReactNode, SVGProps } from 'react';
import { CanvasNodeCategory } from '../canvasTypes';
import {
  CanvasNodeIconKey,
  type CanvasNodeIconKey as CanvasNodeIconKeyValue,
} from '../nodes/nodeSpec';

interface CanvasSvgIconProps extends Omit<SVGProps<SVGSVGElement>, 'children'> {
  children?: ReactNode;
  size?: number | string;
}

const SvgIcon = ({ children, size = '1em', className, ...props }: CanvasSvgIconProps) => (
  <svg
    aria-hidden="true"
    className={['canvas-svg-icon', className].filter(Boolean).join(' ')}
    fill="none"
    height={size}
    viewBox="0 0 24 24"
    width={size}
    xmlns="http://www.w3.org/2000/svg"
    {...props}
  >
    {children}
  </svg>
);

const nodeGlyph = (iconKey: CanvasNodeIconKeyValue): ReactNode => {
  switch (iconKey) {
    case CanvasNodeIconKey.Database:
      return <>
        <ellipse cx="7.5" cy="5" rx="4.5" ry="2.25" />
        <path d="M3 5v8c0 1.24 2.01 2.25 4.5 2.25S12 14.24 12 13V5M3 9c0 1.24 2.01 2.25 4.5 2.25S12 10.24 12 9" />
        <path d="M14.5 12H21m-2.5-2.5L21 12l-2.5 2.5" />
      </>;
    case CanvasNodeIconKey.DatabaseQuery:
      return <>
        <ellipse cx="7" cy="5" rx="4" ry="2" />
        <path d="M3 5v8c0 1.1 1.79 2 4 2 1 0 1.92-.16 2.62-.43M3 9c0 1.1 1.79 2 4 2" />
        <circle cx="15.5" cy="14.5" r="3.5" />
        <path d="m18 17 3 3" />
      </>;
    case CanvasNodeIconKey.Model:
      return <>
        <rect x="3" y="4" width="12" height="16" rx="2" />
        <path d="M7 8h4M7 12h4M7 16h4M18 9v6m-2.5-2.5L18 15l2.5-2.5" />
      </>;
    case CanvasNodeIconKey.File:
      return <>
        <path d="M4 3h9l4 4v13H4zM13 3v4h4" />
        <path d="M7 11h7M7 14h7M7 17h4M19 11v6m-2.5-2.5L19 17l2.5-2.5" />
      </>;
    case CanvasNodeIconKey.Api:
      return <>
        <circle cx="5" cy="7" r="2" /><circle cx="5" cy="17" r="2" /><circle cx="14" cy="12" r="2" />
        <path d="m6.8 8 5.4 3m-5.4 5 5.4-3M16 12h5m-2-2 2 2-2 2" />
      </>;
    case CanvasNodeIconKey.Stream:
      return <>
        <path d="M3 7h5c3.5 0 3.5 4 7 4h6M3 12h4c3.5 0 3.5 5 7 5h7M3 17h3" />
        <path d="m18.5 8.5 2.5 2.5-2.5 2.5m0 1 2.5 2.5-2.5 2.5" />
      </>;
    case CanvasNodeIconKey.Join:
      return <>
        <path d="M3 6h4c3 0 3 6 6 6h8M3 18h4c3 0 3-6 6-6" />
        <circle cx="3" cy="6" r="1.5" /><circle cx="3" cy="18" r="1.5" /><path d="m18.5 9.5 2.5 2.5-2.5 2.5" />
      </>;
    case CanvasNodeIconKey.GeometryConstruct:
      return <>
        <path d="m4 17 3-9 7-3 4 7-5 7z" />
        <circle cx="4" cy="17" r="1.3" /><circle cx="7" cy="8" r="1.3" /><circle cx="14" cy="5" r="1.3" /><circle cx="18" cy="12" r="1.3" /><circle cx="13" cy="19" r="1.3" />
        <path d="M20 3v5m-2.5-2.5h5" />
      </>;
    case CanvasNodeIconKey.SpatialTransform:
      return <>
        <path d="m6 12 5-6 6 4-2 7-7 1z" />
        <path d="M4 8a9 9 0 0 1 14-3m0-2v4h-4M20 16a9 9 0 0 1-14 3m0 2v-4h4" />
      </>;
    case CanvasNodeIconKey.GeometryValidate:
      return <>
        <path d="m3 16 3-9 7-3 5 5-2 9-8 2z" />
        <path d="m11 13 2 2 5-6" />
      </>;
    case CanvasNodeIconKey.GeometryRepair:
      return <>
        <path d="m3 17 3-10 7-3 5 5-2 9-8 2-2.5-1" strokeDasharray="2.5 2.5" />
        <path d="m14.5 14.5 5-5m-1.8-.7 2.5 2.5M13 16l-1 4 4-1" />
      </>;
    case CanvasNodeIconKey.GeometryDerive:
      return <>
        <path d="m3 13 3-7 6-2 4 5-2 6-7 2z" />
        <path d="M14 12h3.5M16 10l2 2-2 2" />
        <circle cx="20" cy="7" r="1.6" />
        <rect x="17.5" y="16" width="5" height="4" rx=".8" />
        <path d="M18 12.5 20 15" />
      </>;
    case CanvasNodeIconKey.GeometrySimplify:
      return <>
        <path d="m3 17 3-10 7-3 6 6-3 8-8 2z" />
        <path d="m6 15 3-6 5-1 2 3-2 4-5 1z" strokeDasharray="2 2" />
        <path d="M18 4v4m-2-2h4M18 15v5m-2.5-2.5h5" />
      </>;
    case CanvasNodeIconKey.SpatialNearest:
      return <>
        <circle cx="6" cy="12" r="2.5" />
        <circle cx="18" cy="6" r="1.8" />
        <circle cx="19" cy="17" r="1.8" />
        <circle cx="13" cy="13" r="1.8" />
        <path d="m8.5 11 3-1.3M8.3 13l3 1M14.5 11.5l2.2-3.8" strokeDasharray="2 2" />
      </>;
    case CanvasNodeIconKey.SpatialSummarizeWithin:
      return <>
        <path d="m3 17 3-11 8-3 7 6-3 10-9 2z" />
        <circle cx="9" cy="10" r="1.2" />
        <circle cx="14" cy="8" r="1.2" />
        <circle cx="16" cy="14" r="1.2" />
        <path d="M7 18h10M9 15v3m4-6v6m4-3v3" />
      </>;
    case CanvasNodeIconKey.SpatialOverlay:
      return <>
        <path d="M3 6 12 3l7 4-3 10-9 2-4-5z" />
        <path d="m8 9 8-3 5 5-3 9-9 1-4-6z" />
        <path d="m8 9 8-3 3 2-3 9-7 2-4-5z" fill="currentColor" fillOpacity=".12" />
      </>;
    case CanvasNodeIconKey.TrackReconstruct:
      return <>
        <circle cx="4" cy="18" r="1.7" /><circle cx="9" cy="8" r="1.7" />
        <circle cx="15" cy="13" r="1.7" /><circle cx="20" cy="5" r="1.7" />
        <path d="m5 16.5 3.2-7M10.5 9l3.2 3M16.2 11.5l2.6-5" />
      </>;
    case CanvasNodeIconKey.TrackMotionStatistics:
      return <>
        <path d="M3 17c3-7 5-10 8-8s4 7 10-3" />
        <circle cx="3" cy="17" r="1.4" /><circle cx="11" cy="9" r="1.4" /><circle cx="21" cy="6" r="1.4" />
        <path d="M4 21h16M7 19v2m5-5v5m5-9v9" />
      </>;
    case CanvasNodeIconKey.TrackFindDwell:
      return <>
        <path d="M4 18c2-7 5-12 9-10s4 8 7 10" strokeDasharray="2 2" />
        <circle cx="13" cy="11" r="6" /><circle cx="13" cy="11" r="2" />
        <path d="M13 3V1M5 11H3m18 0h-2" />
      </>;
    case CanvasNodeIconKey.TrackDetectIncidents:
      return <>
        <path d="M3 18c4-8 7-9 10-5s5 3 8-5" />
        <circle cx="4" cy="17" r="1.5" /><circle cx="20" cy="8" r="1.5" />
        <path d="M12 3v6m0 4v1" /><path d="m8 10 4-8 4 8z" />
      </>;
    case CanvasNodeIconKey.SpatialBinAggregate:
      return <>
        <path d="M3 4h18v16H3zM9 4v16m6-16v16M3 9.3h18M3 14.7h18" />
        <circle cx="6" cy="7" r="1.1" fill="currentColor" />
        <circle cx="12" cy="12" r="1.1" fill="currentColor" />
        <circle cx="18" cy="17" r="1.1" fill="currentColor" />
      </>;
    case CanvasNodeIconKey.SpatialPointCluster:
      return <>
        <circle cx="7" cy="8" r="1.6" /><circle cx="11" cy="6" r="1.6" />
        <circle cx="10" cy="11" r="1.6" /><circle cx="15" cy="14" r="1.6" />
        <circle cx="18" cy="11" r="1.6" /><circle cx="17" cy="18" r="1.6" />
        <path d="M4 4c5-3 10-1 11 4M7 14c3 5 9 7 14 3" strokeDasharray="2 2" />
      </>;
    case CanvasNodeIconKey.SpatialCenterDispersion:
      return <>
        <ellipse cx="12" cy="12" rx="9" ry="5.5" transform="rotate(-22 12 12)" />
        <circle cx="12" cy="12" r="2" />
        <path d="M12 4v16M4 12h16" strokeDasharray="2 2" />
        <circle cx="6" cy="8" r="1" fill="currentColor" />
        <circle cx="18" cy="15" r="1" fill="currentColor" />
      </>;
    case CanvasNodeIconKey.GeometryBuffer:
      return <>
        <path d="m9 7 5-1 3 4-2 6-6 1-3-4z" />
        <path d="M8 3.5 16 2l5 7-3 10-10 2-6-7 3-9z" />
      </>;
    case CanvasNodeIconKey.GeometryExplode:
      return <>
        <path d="m4 13 4-7 4 4-2 6zM14 5l5 2-1 5-4-2zM14 15l5-1 1 5-6 1z" />
        <path d="M11 12h4m-2-2 2 2-2 2" />
      </>;
    case CanvasNodeIconKey.SpatialMeasure:
      return <>
        <path d="m4 17 3-10 7-3 5 6-3 8-8 2z" />
        <path d="m5 20 14-14 3 3L8 23zM10 16l2 2m1-5 2 2m1-5 2 2" />
      </>;
    case CanvasNodeIconKey.GeometrySerialize:
      return <>
        <path d="m3 16 3-9 6-3 4 5-2 8-7 2z" />
        <path d="m18 10-2 2 2 2m3-4 2 2-2 2M20 9l-1 6" />
      </>;
    case CanvasNodeIconKey.SpatialClip:
      return <>
        <path d="m4 15 3-9 7-2 5 6-3 8-8 2z" />
        <rect x="10" y="9" width="10" height="10" rx="1" />
        <path d="M8 9H4v4M18 5h3v4" />
      </>;
    case CanvasNodeIconKey.SpatialAggregate:
      return <>
        <path d="m3 7 2-3 3 1 1 3-3 2zM3 17l2-3 3 1 1 3-3 2zM12 12l3-6 6 2v8l-6 2z" />
        <path d="M9 7h3M9 17h3m-2-7 2 2-2 2" />
      </>;
    case CanvasNodeIconKey.SpatialJoin:
      return <>
        <path d="m3 14 3-8 7-2 4 6-3 7-7 2z" />
        <circle cx="16" cy="14" r="5" />
        <path d="M11.5 11.5c1.5.2 3.2 1.5 3.8 3.1" />
      </>;
    case CanvasNodeIconKey.StreamJoin:
      return <>
        <path d="M3 7h4c3 0 3 5 6 5h8M3 16h4c3 0 3-4 6-4" />
        <path d="M3 11c1.5-1.5 3-1.5 4.5 0M3 20h6v-5H3zM18.5 9.5 21 12l-2.5 2.5" />
      </>;
    case CanvasNodeIconKey.Rename:
      return <>
        <path d="M3 18h6M6 18V7m-3 3V7h6v3M13 16h8m-2.5-2.5L21 16l-2.5 2.5" />
        <path d="m13 7 2-2 3 3-2 2-3 1z" />
      </>;
    case CanvasNodeIconKey.Filter:
      return <>
        <path d="M3 5h18l-7 8v5l-4 2v-7z" />
        <path d="M8 8h8" />
      </>;
    case CanvasNodeIconKey.SqlTransform:
      return <>
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d="m9 9-3 3 3 3M15 9l3 3-3 3M13 8l-2 8" />
      </>;
    case CanvasNodeIconKey.Columns:
      return <>
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d="M9 4v16m6-16v16M3 9h18" />
        <path d="m16 15 1.5 1.5L20 13" />
      </>;
    case CanvasNodeIconKey.Derive:
      return <>
        <rect x="3" y="4" width="14" height="16" rx="2" />
        <path d="M8 4v16m-5-9h14M12 7v4m-2-2h4M19 14v6m-3-3h6" />
      </>;
    case CanvasNodeIconKey.Cast:
      return <>
        <path d="M3 17 7 6l4 11M4.5 13h5M13 12h8m-2.5-2.5L21 12l-2.5 2.5" />
        <path d="M15 18h5m-2.5-4v4" />
      </>;
    case CanvasNodeIconKey.Aggregate:
      return <>
        <path d="M4 5h6M4 10h6M4 15h6M15 6h5l-4 6 4 6h-5" />
        <path d="M11 5v10c0 2 1 3 3 3" />
      </>;
    case CanvasNodeIconKey.Union:
      return <>
        <rect x="3" y="3" width="7" height="5" rx="1" /><rect x="3" y="16" width="7" height="5" rx="1" />
        <path d="M10 5.5h2c3 0 3 6.5 6 6.5h3M10 18.5h2c3 0 3-6.5 6-6.5m.5-2.5L21 12l-2.5 2.5" />
      </>;
    case CanvasNodeIconKey.Deduplicate:
      return <>
        <rect x="6" y="6" width="13" height="13" rx="2" />
        <path d="M4 15V5a2 2 0 0 1 2-2h10M9 10h7M9 14h4" />
        <path d="m14 16 1.5 1.5L19 14" />
      </>;
    case CanvasNodeIconKey.NullHandling:
      return <>
        <path d="M8 4v16M4 7v10M12 7v10" />
        <circle cx="18" cy="12" r="4" /><path d="m15.2 14.8 5.6-5.6" />
      </>;
    case CanvasNodeIconKey.ValueMapping:
      return <>
        <rect x="3" y="5" width="6" height="6" rx="1" /><rect x="15" y="13" width="6" height="6" rx="1" />
        <path d="M6 11v3c0 2 1 2 3 2h6m-2.5-2.5L15 16l-2.5 2.5M16 5h5M18.5 3v4" />
      </>;
    case CanvasNodeIconKey.Masking:
      return <>
        <path d="M3 12s3.5-5 9-5 9 5 9 5-3.5 5-9 5-9-5-9-5Z" />
        <circle cx="12" cy="12" r="2.5" /><path d="M4 20 20 4" />
      </>;
    case CanvasNodeIconKey.Json:
      return <>
        <path d="M8 3C6 3 6 5 6 7v2c0 2-1 3-3 3 2 0 3 1 3 3v2c0 2 0 4 2 4M16 3c2 0 2 2 2 4v2c0 2 1 3 3 3-2 0-3 1-3 3v2c0 2 0 4-2 4" />
        <path d="M10 8h4m-4 4h4m-4 4h2" />
      </>;
    case CanvasNodeIconKey.Window:
      return <>
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d="M3 9h18M8 4v16M15 9v11" />
        <path d="M10.5 13h2v4h-2zM17.5 12h1v5h-1" />
      </>;
    case CanvasNodeIconKey.TopN:
      return <>
        <path d="M5 19v-5h4v5M10 19V9h4v10M15 19V5h4v14M3 19h18" />
        <path d="m4 10 3-3 2 2 5-5" />
      </>;
    case CanvasNodeIconKey.ModelOutput:
      return <>
        <path d="M3 12h6m-2.5-2.5L9 12l-2.5 2.5" />
        <rect x="11" y="4" width="10" height="16" rx="2" />
        <path d="M14 8h4M14 12h4M14 16h4" />
      </>;
    case CanvasNodeIconKey.JdbcOutput:
      return <>
        <path d="M3 12h7m-2.5-2.5L10 12l-2.5 2.5" />
        <ellipse cx="16.5" cy="6" rx="4.5" ry="2.25" />
        <path d="M12 6v10c0 1.24 2.01 2.25 4.5 2.25S21 17.24 21 16V6m-9 5c0 1.24 2.01 2.25 4.5 2.25S21 12.24 21 11" />
      </>;
    case CanvasNodeIconKey.SnapshotSync:
      return <>
        <ellipse cx="12" cy="6" rx="6" ry="3" />
        <path d="M6 6v4c0 1.65 2.69 3 6 3 1.38 0 2.65-.23 3.66-.62M6 10v4c0 1.65 2.69 3 6 3" />
        <path d="M17 14a4 4 0 0 1 3 3.87M20 15v3h-3M19 20a4 4 0 0 1-5.6-.4M13 21v-3h3" />
      </>;
    case CanvasNodeIconKey.StreamOutput:
      return <>
        <path d="M3 7h5c3.5 0 3.5 4 7 4h6M3 12h4c3.5 0 3.5 5 7 5h7" />
        <path d="m18.5 8.5 2.5 2.5-2.5 2.5m0 1 2.5 2.5-2.5 2.5M3 17h3" />
        <circle cx="3" cy="7" r="1" />
      </>;
    case CanvasNodeIconKey.FileOutput:
      return <>
        <path d="M3 12h6m-2.5-2.5L9 12l-2.5 2.5" />
        <path d="M11 3h6l4 4v14H11zM17 3v4h4M14 12h4M14 16h4" />
      </>;
    default: {
      const exhaustive: never = iconKey;
      return exhaustive;
    }
  }
};

export interface CanvasNodeIconProps extends CanvasSvgIconProps {
  iconKey: CanvasNodeIconKeyValue;
}

export const CanvasNodeIcon = ({ iconKey, ...props }: CanvasNodeIconProps) => (
  <SvgIcon
    stroke="currentColor"
    strokeLinecap="round"
    strokeLinejoin="round"
    strokeWidth="1.75"
    {...props}
  >
    {nodeGlyph(iconKey)}
  </SvgIcon>
);

const categoryGlyph: Record<CanvasNodeCategory, ReactNode> = {
  [CanvasNodeCategory.Input]: <>
    <path d="M3 5h9v14H3zM12 12h9m-3-3 3 3-3 3" />
    <circle cx="7.5" cy="9" r="1" /><circle cx="7.5" cy="15" r="1" />
  </>,
  [CanvasNodeCategory.Processor]: <>
    <circle cx="5" cy="6" r="2" /><circle cx="5" cy="18" r="2" /><circle cx="19" cy="12" r="2" />
    <path d="M7 6h2c4 0 4 6 8 6M7 18h2c4 0 4-6 8-6" />
  </>,
  [CanvasNodeCategory.Output]: <>
    <path d="M12 5h9v14h-9zM3 12h9m-3-3 3 3-3 3" />
    <circle cx="16.5" cy="9" r="1" /><circle cx="16.5" cy="15" r="1" />
  </>,
};

export interface CanvasCategoryIconProps extends CanvasSvgIconProps {
  category: CanvasNodeCategory;
}

export const CanvasCategoryIcon = ({ category, ...props }: CanvasCategoryIconProps) => (
  <SvgIcon
    stroke="currentColor"
    strokeLinecap="round"
    strokeLinejoin="round"
    strokeWidth="1.8"
    {...props}
  >
    {categoryGlyph[category]}
  </SvgIcon>
);
