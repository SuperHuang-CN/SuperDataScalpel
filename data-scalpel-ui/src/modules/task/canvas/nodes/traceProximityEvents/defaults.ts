import type { TraceProximityEventsConfiguration } from "../../canvasTypes";

export const createTraceProximityEventsConfiguration = (
): TraceProximityEventsConfiguration => ({
  sourceTableName: '',
  pointGeometryColumnName: '',
  entityIdColumnName: '',
  timeColumnName: '',
  distanceMethod: 'PLANAR',
  spatialSearchDistance: 10,
  spatialSearchDistanceUnit: 'METERS',
  temporalSearchDistance: 5,
  temporalSearchDistanceUnit: 'MINUTES',
  interestSource: 'ENTITY_IDS',
  entitiesOfInterest: [],
  entitiesOfInterestTableName: '',
  interestEntityIdColumnName: '',
  interestStartTimeColumnName: null,
  maxTraceDepth: 3,
  attributeMatchColumns: [],
  includeTracks: false,
  outputTableName: '',
  tracksOutputTableName: '',
  fromEntityIdColumnName: 'trace_from_id',
  toEntityIdColumnName: 'trace_to_id',
  depthColumnName: 'trace_depth',
  durationMinutesColumnName: 'trace_duration_minutes',
  eventTimeColumnName: 'trace_event_time',
});
