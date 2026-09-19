
export const createTrackBoundaryConfiguration = () => ({
  maximumTimeGap: null,
  maximumTimeGapUnit: null,
  maximumDistanceGap: null,
  maximumDistanceGapUnit: null,
});

export const createSnapshotDeletePolicy = () => ({
  action: 'KEEP' as const,
  maxDeleteRows: null,
  maxDeleteRatio: null,
});
