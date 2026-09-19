import { Tooltip } from 'antd';
import type { ReactNode } from 'react';
import {
  standardDictionaryValueTypeLabels,
  type StandardDictionaryValueType,
} from '../model/standardDictionary';

const glyphs = {
  STRING: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden>
      <text
        x="12"
        y="16.2"
        fill="currentColor"
        fontFamily="ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace"
        fontSize="11.5"
        fontWeight="750"
        textAnchor="middle"
      >
        Aa
      </text>
    </svg>
  ),
  INTEGER: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden>
      <text
        x="12"
        y="15.8"
        fill="currentColor"
        fontFamily="ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace"
        fontSize="9.5"
        fontWeight="750"
        letterSpacing="-.7"
        textAnchor="middle"
      >
        123
      </text>
    </svg>
  ),
  LONG: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden>
      <text
        x="12"
        y="14.7"
        fill="currentColor"
        fontFamily="ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace"
        fontSize="10.5"
        fontWeight="750"
        textAnchor="middle"
      >
        64
      </text>
      <text
        x="12"
        y="19"
        fill="currentColor"
        fontFamily="ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace"
        fontSize="3.8"
        fontWeight="700"
        letterSpacing=".7"
        textAnchor="middle"
      >
        BIT
      </text>
    </svg>
  ),
  DECIMAL: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden>
      <text
        x="12"
        y="15.8"
        fill="currentColor"
        fontFamily="ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace"
        fontSize="9.5"
        fontWeight="750"
        letterSpacing="-.8"
        textAnchor="middle"
      >
        0.0
      </text>
    </svg>
  ),
  BOOLEAN: (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden>
      <rect x="3" y="7" width="18" height="10" rx="5" stroke="currentColor" strokeWidth="1.8" />
      <circle cx="16" cy="12" r="3" fill="currentColor" />
      <path d="M7 10.2v3.6" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" opacity=".75" />
    </svg>
  ),
} satisfies Record<StandardDictionaryValueType, ReactNode>;

export const StandardDictionaryValueTypeIcon = ({
  valueType,
}: {
  valueType: StandardDictionaryValueType;
}) => (
  <Tooltip title={standardDictionaryValueTypeLabels[valueType]}>
    <span className="standard-dictionary-value-type-icon">{glyphs[valueType]}</span>
  </Tooltip>
);
