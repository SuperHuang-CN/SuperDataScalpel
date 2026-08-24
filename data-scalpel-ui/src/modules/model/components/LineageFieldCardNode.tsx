/* eslint-disable react-refresh/only-export-components -- X6 consumes this module as a runtime node registry. */
import type { Node } from '@antv/x6';
import { register } from '@antv/x6-react-shape';
import type { LineageGraphNode } from '../model/dataModel';

export const LINEAGE_FIELD_CARD_SHAPE = 'data-scalpel-lineage-field-card';

export interface LineageFieldCardData {
  visualKind: 'FIELD_CARD';
  cardId: string;
  ownerKind: NonNullable<LineageGraphNode['fieldOwner']>['kind'];
  ownerLabel: string;
  ownerSubtitle: string | null;
  stale: boolean;
  fields: LineageGraphNode[];
  onFieldEnter: (field: LineageGraphNode) => void;
  onFieldLeave: () => void;
  onFieldClick: (field: LineageGraphNode) => void;
}

interface LineageFieldCardViewProps {
  node: Node;
}

const ownerKindLabels: Record<LineageFieldCardData['ownerKind'], string> = {
  MODEL: '数据模型',
  JDBC_TABLE: 'JDBC 表',
  EXTERNAL_RESOURCE: '外部资源',
};

const LineageFieldCardView = ({ node }: LineageFieldCardViewProps) => {
  const data = node.getData<LineageFieldCardData>();
  const size = node.getSize();
  const ownerSubtitle = data.ownerKind === 'JDBC_TABLE'
    ? data.ownerSubtitle?.split(' · ')[0]
    : data.ownerSubtitle;
  return (
    <div
      className={`lineage-field-card is-${data.ownerKind.toLowerCase()}${data.stale ? ' is-stale' : ''}`}
      data-lineage-card-id={data.cardId}
      style={{ width: size.width, height: size.height }}
    >
      <div className="lineage-field-card-header" title={data.ownerSubtitle ?? data.ownerLabel}>
        <span className="lineage-field-card-kind">{ownerKindLabels[data.ownerKind]}</span>
        <strong>{data.ownerLabel}</strong>
        {ownerSubtitle && <span>{ownerSubtitle}</span>}
      </div>
      <div className="lineage-field-card-fields">
        {data.fields.map((field) => (
          <button
            type="button"
            key={field.id}
            className={`lineage-field-card-row${field.focusRoot ? ' is-focus-root' : ''}`}
            data-lineage-field-node-id={field.id}
            onMouseEnter={() => data.onFieldEnter(field)}
            onMouseLeave={data.onFieldLeave}
            onClick={(event) => {
              event.stopPropagation();
              data.onFieldClick(field);
            }}
            title={`${field.label}${field.subtitle ? ` · ${field.subtitle}` : ''}`}
          >
            <span className="lineage-field-card-field-name">{field.label}</span>
            <span className="lineage-field-card-field-code">
              {field.subtitle?.split(' · ').at(-1) ?? ''}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
};

let registered = false;

export const registerLineageFieldCardNode = () => {
  if (registered) return;
  register({
    shape: LINEAGE_FIELD_CARD_SHAPE,
    width: 280,
    height: 120,
    component: LineageFieldCardView,
    effect: ['data'],
  });
  registered = true;
};
