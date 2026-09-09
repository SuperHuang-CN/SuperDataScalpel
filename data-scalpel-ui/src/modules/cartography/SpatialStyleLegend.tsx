import type { CSSProperties } from 'react';
import { useId } from 'react';
import type { SpatialStyleDocument, SpatialSymbol } from './model';
import './cartography.css';
import { createSpatialLegend } from './legend';

const lineDash = (pattern: 'SOLID' | 'DASHED' | 'DOTTED') => pattern === 'DASHED'
  ? '8 4' : pattern === 'DOTTED' ? '2 3' : undefined;

export const SpatialSymbolSwatch = ({ symbol }: { symbol: SpatialSymbol }) => {
  const patternId = useId().replaceAll(':', '');
  if (symbol.type === 'POINT') {
    const size = Math.max(6, Math.min(24, symbol.size));
    const style = {
      width: size,
      height: size,
      '--fill': symbol.fillColor,
      '--fill-opacity': String(symbol.fillOpacity),
      '--stroke': symbol.outlineColor,
      '--stroke-opacity': String(symbol.outlineOpacity),
      '--stroke-width': `${Math.min(4, symbol.outlineWidth)}px`,
    } as CSSProperties;
    return <span className={`cartography-swatch cartography-swatch-point cartography-shape-${symbol.shape.toLowerCase()}`} style={style} />;
  }
  if (symbol.type === 'LINE') {
    const outerWidth = symbol.width + 2 * (symbol.casing?.width ?? 0);
    const ratio = Math.min(1, 16 / outerWidth);
    return <svg width="28" height="22" viewBox="0 0 28 22" aria-label="线符号示意">
      {symbol.casing && <line x1="1" x2="27" y1="11" y2="11" stroke={symbol.casing.color} strokeOpacity={symbol.casing.opacity} strokeWidth={outerWidth * ratio} />}
      <line x1="1" x2="27" y1="11" y2="11" stroke={symbol.color} strokeOpacity={symbol.opacity} strokeWidth={symbol.width * ratio} strokeDasharray={lineDash(symbol.pattern)} />
    </svg>;
  }
  const pattern = symbol.pattern;
  return <svg width="26" height="24" viewBox="0 0 26 24" aria-label="面符号示意">
    {pattern && <defs><pattern id={patternId} width={pattern.spacing} height={pattern.spacing} patternUnits="userSpaceOnUse">
      {pattern.type === 'DOT' ? <circle cx={pattern.spacing / 2} cy={pattern.spacing / 2} r={pattern.dotSize / 2} fill={pattern.color} fillOpacity={pattern.opacity} />
        : <path d={`M0 ${pattern.spacing} L${pattern.spacing} 0${pattern.type === 'CROSS' ? ` M0 0 L${pattern.spacing} ${pattern.spacing}` : ''}`}
          fill="none" stroke={pattern.color} strokeOpacity={pattern.opacity} strokeWidth={pattern.strokeWidth} />}
    </pattern></defs>}
    <rect x="2" y="2" width="22" height="20" fill={symbol.fillColor} fillOpacity={symbol.fillOpacity} />
    {pattern && <rect x="2" y="2" width="22" height="20" fill={`url(#${patternId})`} />}
    <rect x="2" y="2" width="22" height="20" fill="none" stroke={symbol.outlineColor} strokeOpacity={symbol.outlineOpacity}
      strokeWidth={Math.min(4, symbol.outlineWidth)} strokeDasharray={lineDash(symbol.outlinePattern)} />
  </svg>;
};

export const SpatialStyleLegend = ({ document }: { document: SpatialStyleDocument | null }) => (
  <div className="cartography-legend">
    {createSpatialLegend(document).map((item) => <div key={item.id}>
      <span className="cartography-legend-symbol"><SpatialSymbolSwatch symbol={item.symbol} /></span>
      <span>{item.label}</span>
    </div>)}
  </div>
);
