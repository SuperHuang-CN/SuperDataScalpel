/* eslint-disable react-refresh/only-export-components -- X6 runtime node registry. */
import type { Node } from '@antv/x6';
import { register } from '@antv/x6-react-shape';
import type { LineageGraphNode } from '../model/dataModel';
import { LineageKindIcon } from './LineageNodeAppearance';
import { LINEAGE_ASSET_SIZE, lineageNodeKindLabels } from '../model/lineageAppearance';

export const LINEAGE_ASSET_SHAPE = 'data-scalpel-lineage-asset';
export interface LineageAssetData extends LineageGraphNode {
  current: boolean;
  onSelect: (node: LineageGraphNode) => void;
}

const LineageAssetView = ({ node }: { node: Node }) => {
  const data = node.getData<LineageAssetData>();
  const size = node.getSize();
  const subtitle = data.subtitle && data.subtitle !== data.label && data.subtitle !== lineageNodeKindLabels[data.kind] ? data.subtitle : null;
  return (
    <button
      type="button"
      className={`lineage-asset-node is-${data.kind.toLowerCase()}${data.current ? ' is-current' : ''}${data.stale ? ' is-stale' : ''}`}
      style={{ width: size.width, height: size.height }}
      data-lineage-node-id={data.id}
      onClick={() => data.onSelect(data)}
      aria-label={`查看${lineageNodeKindLabels[data.kind]}：${data.label}`}
      title={[data.label, subtitle].filter(Boolean).join('\n')}
    >
      <span className="lineage-asset-symbol"><LineageKindIcon kind={data.kind} /></span>
      <span className="lineage-asset-copy">
        <span className="lineage-asset-type">{lineageNodeKindLabels[data.kind]}
          {data.current && <span className="lineage-asset-current">当前对象</span>}
        </span>
        <strong>{data.label}</strong>
        {(data.stale || subtitle) && <span className="lineage-asset-subtitle">{[data.stale ? '结构已变更' : null, subtitle].filter(Boolean).join(' · ')}</span>}
      </span>
    </button>
  );
};

let registered = false;
export const registerLineageAssetNode = () => {
  if (registered) return;
  register({ shape: LINEAGE_ASSET_SHAPE, ...LINEAGE_ASSET_SIZE, component: LineageAssetView, effect: ['data', 'size'] });
  registered = true;
};
