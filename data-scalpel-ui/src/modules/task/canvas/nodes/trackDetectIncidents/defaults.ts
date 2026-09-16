import type { TrackDetectIncidentsConfiguration } from "../../canvasTypes";
import { createTrackBoundaryConfiguration } from '../configurationDefaults';

export const createTrackDetectIncidentsConfiguration = (): TrackDetectIncidentsConfiguration => ({
  conditionWindows: [],
  conditionScalars: [],
  incidentSemantics: 'CONDITION_LIFECYCLE',
  incidentStatusColumnName: 'incident_status',
  orderByColumns: [],
  sourceTableName: '',
  pointGeometryColumnName: null,
  trackIdColumns: [],
  timeColumnName: '',
  distanceMethod: 'PLANAR',
  boundaries: createTrackBoundaryConfiguration(),
  startCondition: { kind: 'GROUP', operator: 'AND', children: [] },
  endCondition: null,
  resultMode: 'INCIDENTS_ONLY',
  outputTableName: '',
  incidentIdColumnName: 'incident_id',
  incidentFlagColumnName: 'is_incident',
  incidentStartTimeColumnName: 'incident_start_time',
  incidentEndTimeColumnName: 'incident_end_time',
  incidentDurationColumnName: 'incident_duration',
  incidentDurationUnit: 'MILLISECONDS',
});
