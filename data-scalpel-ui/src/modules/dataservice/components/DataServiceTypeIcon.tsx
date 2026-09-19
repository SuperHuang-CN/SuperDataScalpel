import { Tooltip } from 'antd';
import type { ReactNode } from 'react';
import { dataServiceTypeLabels, type DataServiceType } from '../model/dataService';

const glyphs = {
  STANDARD_TABLE: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="3" width="19" height="18" rx="4" fill="#3568D4" />
      <path stroke="#fff" strokeWidth="1.2" strokeOpacity=".95" d="M5.5 6.3h13v10.8h-13zM5.8 10h12.4M5.8 13.6h12.4M10 6.6v10.2" />
      <path fill="#fff" d="m15.2 16.3 4.5 2.6-4.5 2.6v-1.6h-3.8v-2h3.8v-1.6Z" />
    </svg>
  ),
  SQL_QUERY: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="3" width="19" height="18" rx="4" fill="#167F9C" />
      <path stroke="#C7F5FF" strokeWidth="1.2" strokeLinecap="round" d="M6 7.2h12M6 10h7" />
      <text x="12" y="17.7" fill="#fff" fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace" fontSize="6.3" fontWeight="800" textAnchor="middle">SQL</text>
      <circle cx="17.7" cy="10.2" r="2.1" fill="#fff" />
      <path stroke="#fff" strokeWidth="1.4" strokeLinecap="round" d="m19.2 11.8 1.7 1.7" />
    </svg>
  ),
  SCRIPT_API: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="3" width="19" height="18" rx="4" fill="#7652C7" />
      <path stroke="#fff" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" d="m9 7.2-3.2 4.1L9 15.4m6-8.2 3.2 4.1-3.2 4.1M13.8 6l-3.3 11" />
      <path fill="#E7DEFF" d="m15.4 16.2 4.1 2.35-4.1 2.35v-4.7Z" />
    </svg>
  ),
  SPATIAL_SERVICE: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="3" width="19" height="18" rx="4" fill="#23856D" />
      <path d="M5.5 17.5 9 8l4.2 5 2.4-3.2 3 7.7H5.5Z" fill="#D8FFF2" stroke="#fff" strokeWidth="1" />
      <circle cx="9" cy="8" r="2" fill="#fff" />
    </svg>
  ),
} satisfies Record<DataServiceType, ReactNode>;

export const DataServiceTypeIcon = ({ type }: { type: DataServiceType }) => (
  <Tooltip title={dataServiceTypeLabels[type]}>
    <span className="data-service-type-icon">{glyphs[type]}</span>
  </Tooltip>
);
