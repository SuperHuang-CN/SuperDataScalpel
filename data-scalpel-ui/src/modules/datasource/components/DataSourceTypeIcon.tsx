import { Tooltip } from 'antd';
import type { ReactNode } from 'react';
import { dataSourceTypeLabels, type DataSourceType } from '../model/dataSource';

const glyphs = {
  MYSQL: (
    <svg viewBox="0 0 24 24" fill="none">
      <path fill="#00758F" d="M3.2 14.9c2.1-3.9 5.4-6.1 9.4-6.2 2.7-.1 5 .8 6.8 2.7-1.4-.3-2.7-.2-3.7.3 2.2.9 3.8 2.5 4.8 4.8-2.3-1.2-4.5-1.6-6.6-1.1-2.4.5-4.4 1.9-6.1 4.2-1.3-1.8-2.8-3.3-4.6-4.7Z" />
      <path fill="#F29111" d="M15.2 8.8c.8-1.3 1.9-2.2 3.4-2.7-.2 1.5-.8 2.8-1.9 3.8l-1.5-1.1Z" />
      <circle cx="14.2" cy="11.4" r=".75" fill="#fff" />
    </svg>
  ),
  POSTGRESQL: (
    <svg viewBox="0 0 24 24" fill="none">
      <path fill="#336791" d="M12 3.1c-4.2 0-7.3 2.6-7.3 6.7 0 3.1 1.3 5.3 3.6 6.5l.1 2.5c0 1.2.8 2.1 1.9 2.1 1.3 0 2-1 2-2.4v-2.2c.8.3 1.8.3 2.6.1l.2 2.1c.1 1.3.8 2.1 2 2.1 1.2 0 1.9-.9 1.8-2.2l-.3-3.5c.7-1.2 1-2.7 1-4.6C19.6 5.9 16.5 3.1 12 3.1Z" />
      <path stroke="#fff" strokeWidth="1.35" strokeLinecap="round" d="M12.1 13.1c.1 1.5.1 3.7-.1 5.1-.1.8-.5 1.2-1.1 1.2-.7 0-1.1-.5-1.1-1.3l-.1-4.2" />
      <path stroke="#fff" strokeWidth="1.15" strokeLinecap="round" d="M8.1 10.4c.9 1 2.2 1.4 3.9 1.4 1.6 0 3-.5 4-1.5" />
      <circle cx="9.2" cy="8.8" r=".8" fill="#fff" />
      <circle cx="14.9" cy="8.8" r=".8" fill="#fff" />
    </svg>
  ),
  HIGHGO: (
    <svg viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9" fill="#244A9B" />
      <path d="M7 7v10M17 7v10M7 12h10" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  ),
  ORACLE: (
    <svg viewBox="0 0 24 24" fill="none">
      <ellipse cx="12" cy="12" rx="9" ry="5.4" stroke="#F80000" strokeWidth="3" />
    </svg>
  ),
  SQL_SERVER: (
    <svg viewBox="0 0 24 24" fill="none">
      <path fill="#CC2927" d="M4 6.2 12.1 3v5.6L4 10.8V6.2Zm0 5.8 8.1-2.1v5.6L4 17.8V12Zm9.3-9.4L20 5.3v4.2l-6.7-1.2V2.6Zm0 7 6.7 1.1v4.1l-6.7.4V9.6Zm0 6.8 6.7-.4v3.1l-6.7 2.3v-5Z" />
    </svg>
  ),
  CLICKHOUSE: (
    <svg viewBox="0 0 24 24" fill="none">
      <rect x="2.5" y="2.5" width="19" height="19" rx="3" fill="#1D1D1D" />
      <path stroke="#FFCC01" strokeWidth="2.2" d="M6 6v12M9.5 6v12M13 6v12M16.5 6v12" />
      <path stroke="#F24822" strokeWidth="2.2" d="M20 10v4" />
    </svg>
  ),
  DAMENG: (
    <svg viewBox="0 0 24 24" fill="none">
      <path fill="#1769AA" d="M3 6.2h6.1c3.3 0 5.3 2.1 5.3 5.7 0 3.7-2 5.9-5.3 5.9H3V6.2Zm3.1 2.6v6.4h2.4c1.7 0 2.7-1.1 2.7-3.3s-1-3.1-2.7-3.1H6.1Z" />
      <path fill="#E53935" d="m12.3 17.8 2.6-11.6h2.7l1.2 5.7 1.2-5.7h1.4l-.1 11.6h-2.4l-.1-3.9-1 3.9h-2l-1-3.9-.2 3.9h-2.3Z" />
    </svg>
  ),
  KINGBASE: (
    <svg viewBox="0 0 24 24" fill="none">
      <path fill="#C91C2B" d="M12 2.7 21.3 12 12 21.3 2.7 12 12 2.7Z" />
      <path fill="#fff" d="M7.4 6.9h3v4l3.6-4h3.5l-4.3 4.7 4.6 5.6h-3.7l-3.7-4.6v4.6h-3V6.9Z" />
      <circle cx="17.7" cy="6.3" r="1.4" fill="#F4B942" />
    </svg>
  ),
  OPENGAUSS: (
    <svg viewBox="0 0 24 24" fill="none">
      <path stroke="#E84D2A" strokeWidth="3" strokeLinecap="round" d="M18.4 7.2A8 8 0 1 0 19 16" />
      <path stroke="#F39A24" strokeWidth="3" strokeLinecap="round" d="M19 16v-4.1h-5" />
      <circle cx="12" cy="12" r="2.2" fill="#E84D2A" />
    </svg>
  ),
  TDENGINE_WEBSOCKET: (
    <svg viewBox="0 0 24 24" fill="none">
      <path fill="#0067B1" d="M3 5.5h18v4H3v-4Zm2 5.5h14v3.2H5V11Zm2 4.7h10v2.8H7v-2.8Z" />
      <path stroke="#42C7E8" strokeWidth="1.6" strokeLinecap="round" d="M5 3.2c1.4-1 2.8-1 4.2 0s2.8 1 4.2 0 2.8-1 4.2 0" />
      <circle cx="12" cy="12.6" r="1.35" fill="#fff" />
    </svg>
  ),
  TDENGINE_RESTFUL: (
    <svg viewBox="0 0 24 24" fill="none">
      <path fill="#5B6B7A" d="M3 5.5h18v4H3v-4Zm2 5.5h14v3.2H5V11Zm2 4.7h10v2.8H7v-2.8Z" />
      <path stroke="#F0A43C" strokeWidth="1.6" strokeLinecap="round" d="m6 3 2-1.4L10 3m4 0 2-1.4L18 3" />
      <circle cx="12" cy="12.6" r="1.35" fill="#fff" />
    </svg>
  ),
  KAFKA: (
    <svg viewBox="0 0 24 24" fill="none">
      <path stroke="#242424" strokeWidth="1.8" d="m8.1 6.4 7.8 4.1m-7.8 7.1 7.8-4.1M7 8.2v7.6" />
      <circle cx="6.8" cy="5.6" r="2.5" fill="#242424" />
      <circle cx="6.8" cy="18.4" r="2.5" fill="#242424" />
      <circle cx="17.2" cy="12" r="3" fill="#242424" />
      <circle cx="6.8" cy="5.6" r=".85" fill="#fff" />
      <circle cx="6.8" cy="18.4" r=".85" fill="#fff" />
      <circle cx="17.2" cy="12" r="1" fill="#fff" />
    </svg>
  ),
  S3: (
    <svg viewBox="0 0 24 24" fill="none">
      <path fill="#D64B4B" d="m12 2.8 8.2 4.1v10.2L12 21.2l-8.2-4.1V6.9L12 2.8Z" />
      <path fill="#fff" fillOpacity=".95" d="M8 7.5h8v2.2H8V7.5Zm-1.4 3.4h10.8v2.2H6.6v-2.2ZM8 14.3h8v2.2H8v-2.2Z" />
      <path stroke="#fff" strokeWidth="1" strokeOpacity=".7" d="M12 3.4v17.2" />
    </svg>
  ),
  HTTP_API: (
    <svg viewBox="0 0 24 24" fill="none">
      <rect x="2.5" y="4" width="19" height="16" rx="4" fill="#1677FF" />
      <path stroke="#fff" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" d="m9 9-3 3 3 3m6-6 3 3-3 3m-1.2-8-3.6 10" />
    </svg>
  ),
  ARCGIS_REST: (
    <svg viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9.2" fill="#2F8F75" />
      <path stroke="#fff" strokeWidth="1.2" d="M3.6 12h16.8M12 2.8c2.2 2.5 3.3 5.6 3.3 9.2S14.2 18.7 12 21.2C9.8 18.7 8.7 15.6 8.7 12S9.8 5.3 12 2.8Z" />
      <path fill="#fff" d="m13.7 9.4 5.1 1.9-2.2 1.1 1.6 2.6-1.4.8-1.6-2.7-1.5 1.3V9.4Z" />
    </svg>
  ),
  WFS: (
    <svg viewBox="0 0 24 24" fill="none">
      <rect x="2.5" y="3.5" width="19" height="17" rx="3" fill="#1285A8" />
      <path stroke="#fff" strokeWidth="1" strokeOpacity=".75" d="M3 9h18M3 15h18M8 4v16M16 4v16" />
      <path fill="#fff" d="M5.1 10.2h1.6l.6 2.4.7-2.4h1.4l.7 2.4.6-2.4h1.6l-1.4 4.2H9.4l-.7-2.1-.7 2.1H6.5l-1.4-4.2Zm8 0h3.3v1.1h-1.8v.6h1.6V13h-1.6v1.4h-1.5v-4.2Zm3.8.3c.5-.3 1.1-.4 1.7-.4.7 0 1.2.1 1.7.4l-.5 1c-.4-.2-.8-.3-1.2-.3-.3 0-.4.1-.4.2s.2.2.7.3c1 .2 1.6.6 1.6 1.4 0 .9-.8 1.4-2 1.4-.7 0-1.4-.2-1.9-.5l.5-1c.4.3.9.4 1.4.4.3 0 .5-.1.5-.2 0-.2-.2-.2-.7-.3-1-.2-1.5-.6-1.5-1.3 0-.5.1-.8.1-1.1Z" />
    </svg>
  ),
} satisfies Record<DataSourceType, ReactNode>;

export const DataSourceTypeIcon = ({ type }: { type: DataSourceType }) => (
  <Tooltip title={dataSourceTypeLabels[type]}>
    <span className="data-source-type-icon">{glyphs[type]}</span>
  </Tooltip>
);
