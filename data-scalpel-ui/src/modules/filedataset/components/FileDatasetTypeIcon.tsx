import { Tooltip } from 'antd';
import type { ReactNode } from 'react';
import { fileDatasetTypeLabels, type FileDatasetType } from '../model/fileDataset';

const glyphs = {
  CSV: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="2.5" width="19" height="19" rx="4" fill="#16866A" />
      <path stroke="#fff" strokeWidth="1.15" strokeOpacity=".9" d="M5.2 5.5h13.6v7.2H5.2zM9.7 5.8v6.6m4.6-6.6v6.6M5.5 9.1h13" />
      <text x="12" y="18.6" fill="#fff" fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace" fontSize="5.2" fontWeight="800" textAnchor="middle">CSV</text>
    </svg>
  ),
  TSV: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="2.5" width="19" height="19" rx="4" fill="#167C9E" />
      <path stroke="#fff" strokeWidth="1.15" strokeOpacity=".9" d="M5.2 5.5h13.6v7.2H5.2zM9.7 5.8v6.6m4.6-6.6v6.6M5.5 9.1h13" />
      <text x="12" y="18.6" fill="#fff" fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace" fontSize="5.2" fontWeight="800" textAnchor="middle">TSV</text>
    </svg>
  ),
  TXT: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path fill="#65718A" d="M5 2.5h9.4L19.5 7v14.5H5V2.5Z" />
      <path fill="#94A0B5" d="M14 2.5 19.5 7H14V2.5Z" />
      <path stroke="#fff" strokeWidth="1.25" strokeLinecap="round" d="M8 9.5h8m-8 3h8m-8 3h6m-6 3h4" />
    </svg>
  ),
  JSON: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="2.5" width="19" height="19" rx="4" fill="#7656B5" />
      <path stroke="#fff" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" d="M9.8 5.8H8.6c-1 0-1.4.5-1.4 1.5v2.2c0 1.1-.5 1.8-1.4 2.5.9.7 1.4 1.4 1.4 2.5v2.2c0 1 .4 1.5 1.4 1.5h1.2m4.4-12.4h1.2c1 0 1.4.5 1.4 1.5v2.2c0 1.1.5 1.8 1.4 2.5-.9.7-1.4 1.4-1.4 2.5v2.2c0 1-.4 1.5-1.4 1.5h-1.2" />
      <circle cx="12" cy="9.5" r="1" fill="#fff" />
      <circle cx="12" cy="14.5" r="1" fill="#fff" />
    </svg>
  ),
  JSONL: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="2.5" width="19" height="19" rx="4" fill="#654EA3" />
      <path stroke="#fff" strokeWidth="1.25" strokeLinecap="round" strokeLinejoin="round" d="M8.3 5.5H7.6c-.7 0-1 .4-1 1v.7c0 .7-.3 1.1-.9 1.4.6.4.9.8.9 1.5v.7c0 .6.3.9 1 .9h.7m3.1-6.2h.7c.7 0 1 .4 1 1v.7c0 .7.3 1.1.9 1.4-.6.4-.9.8-.9 1.5v.7c0 .6-.3.9-1 .9h-.7M8.3 13h-.7c-.7 0-1 .4-1 1v.7c0 .7-.3 1.1-.9 1.4.6.4.9.8.9 1.5v.7c0 .6.3.9 1 .9h.7m3.1-6.2h.7c.7 0 1 .4 1 1v.7c0 .7.3 1.1.9 1.4-.6.4-.9.8-.9 1.5v.7c0 .6-.3.9-1 .9h-.7" />
      <path stroke="#CFC4EA" strokeWidth="1.2" strokeLinecap="round" d="M16.2 7h2.2m-2.2 3.2h2.2m-2.2 4.3h2.2m-2.2 3.2h2.2" />
    </svg>
  ),
  PARQUET: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="2.5" width="19" height="19" rx="4" fill="#C45A3A" />
      <path fill="#fff" fillOpacity=".95" d="M5.2 5.3h3.6v4.1H5.2V5.3Zm5 0h3.6v6.5h-3.6V5.3Zm5 0h3.6v3.1h-3.6V5.3ZM5.2 10.8h3.6v7.9H5.2v-7.9Zm5 2.4h3.6v5.5h-3.6v-5.5Zm5-3.4h3.6v8.9h-3.6V9.8Z" />
    </svg>
  ),
  AVRO: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="2.5" width="19" height="19" rx="4" fill="#B74658" />
      <path fill="#fff" fillOpacity=".95" d="M5 6h9.2l2 2H7L5 6Zm0 4.1h11.5l2 2H7l-2-2Zm0 4.1h11.5l2 2H7l-2-2Zm0 4.1h9.2l2-2H7l-2 2Z" />
      <circle cx="17.4" cy="7.3" r="1.4" fill="#FFD4D9" />
    </svg>
  ),
  EXCEL: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="5.5" y="3" width="16" height="18" rx="2.5" fill="#1F7A50" />
      <path stroke="#fff" strokeWidth="1" strokeOpacity=".75" d="M11 6h8m-8 4h8m-8 4h8m-8 4h8M15 5v14" />
      <path fill="#14633F" d="M2.5 6.2 12.8 4.5v15L2.5 17.8V6.2Z" />
      <path stroke="#fff" strokeWidth="1.8" strokeLinecap="round" d="m5.6 9 4 6m0-6-4 6" />
    </svg>
  ),
  GDB: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <ellipse cx="12" cy="5.7" rx="8.5" ry="3.2" fill="#3473B8" />
      <path fill="#2A65A6" d="M3.5 5.7v11.8c0 1.8 3.8 3.3 8.5 3.3s8.5-1.5 8.5-3.3V5.7c0 1.8-3.8 3.3-8.5 3.3S3.5 7.5 3.5 5.7Z" />
      <path stroke="#8FC7FF" strokeWidth="1" d="M3.8 10.4c.8 1.5 4.1 2.6 8.2 2.6 4.2 0 7.6-1.2 8.3-2.7M3.8 14.6c.8 1.5 4.1 2.6 8.2 2.6 4.2 0 7.6-1.2 8.3-2.7" />
      <path fill="#fff" d="m12.2 9.6 3.7 2.3-1.1 4.3-4.5.3-1.6-4.1 3.5-2.8Z" />
      <circle cx="12.2" cy="9.6" r="1" fill="#9FD3FF" />
      <circle cx="15.9" cy="11.9" r="1" fill="#9FD3FF" />
      <circle cx="14.8" cy="16.2" r="1" fill="#9FD3FF" />
      <circle cx="10.3" cy="16.5" r="1" fill="#9FD3FF" />
      <circle cx="8.7" cy="12.4" r="1" fill="#9FD3FF" />
    </svg>
  ),
  SHP: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="2.5" width="19" height="19" rx="4" fill="#D47A28" />
      <path fill="#FFE4B8" stroke="#fff" strokeWidth="1.15" strokeLinejoin="round" d="m6.2 15.8 1.5-7.2 5.1-2.8 5.4 4.1-1.6 7.5-6.2 1.2-4.2-2.8Z" />
      <g fill="#fff">
        <circle cx="6.2" cy="15.8" r="1.4" />
        <circle cx="7.7" cy="8.6" r="1.4" />
        <circle cx="12.8" cy="5.8" r="1.4" />
        <circle cx="18.2" cy="9.9" r="1.4" />
        <circle cx="16.6" cy="17.4" r="1.4" />
        <circle cx="10.4" cy="18.6" r="1.4" />
      </g>
    </svg>
  ),
} satisfies Record<FileDatasetType, ReactNode>;

export const FileDatasetTypeIcon = ({ type }: { type: FileDatasetType }) => (
  <Tooltip title={fileDatasetTypeLabels[type]}>
    <span className="file-dataset-type-icon">{glyphs[type]}</span>
  </Tooltip>
);
