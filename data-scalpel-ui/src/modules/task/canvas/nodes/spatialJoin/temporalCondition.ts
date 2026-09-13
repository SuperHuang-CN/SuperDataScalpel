import type {
  SpatialJoinTemporalCondition,
  SpatialJoinTemporalRelationship,
} from '../../canvasTypes';

export const createSpatialJoinTemporalCondition = (
  leftStartColumnName = '',
  rightStartColumnName = '',
): SpatialJoinTemporalCondition => ({
  relationship: 'INTERSECTS',
  leftStartColumnName,
  leftEndColumnName: null,
  rightStartColumnName,
  rightEndColumnName: null,
  nearDistance: 1,
  nearDistanceUnit: 'MINUTES',
});

export const spatialJoinTemporalSummary = (
  value: SpatialJoinTemporalCondition | null | undefined,
) => {
  if (!value) return '未配置';
  const relationship = value.relationship ?? '关系待选择';
  const left = value.leftEndColumnName
    ? `${value.leftStartColumnName || '?'}～${value.leftEndColumnName}`
    : value.leftStartColumnName || '?';
  const right = value.rightEndColumnName
    ? `${value.rightStartColumnName || '?'}～${value.rightEndColumnName}`
    : value.rightStartColumnName || '?';
  const near = isSpatialJoinTemporalNear(relationship)
    ? ` · ${value.nearDistance ?? '?'} ${value.nearDistanceUnit ?? '?'}`
    : '';
  return `${relationship} · ${left} ↔ ${right}${near}`;
};

export const isSpatialJoinTemporalNear = (
  relationship: SpatialJoinTemporalRelationship | string | null | undefined,
) => relationship === 'NEAR'
  || relationship === 'NEAR_BEFORE'
  || relationship === 'NEAR_AFTER';
