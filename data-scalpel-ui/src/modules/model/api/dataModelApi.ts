import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateDataModelRequest,
  DataModel,
  DataModelDetail,
  UpdateDataModelFieldsRequest,
  UpdateDataModelRequest,
} from '../model/dataModel';

const DATA_MODEL_PATH = '/v1/models';

export const fetchDataModels = async (request: SearchRequest): Promise<PageResponse<DataModel>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<DataModel>>(query ? `${DATA_MODEL_PATH}?${query}` : DATA_MODEL_PATH);
};

export const fetchDataModel = (id: string): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(`${DATA_MODEL_PATH}/${id}`)
);

export const createDataModel = (request: CreateDataModelRequest): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(DATA_MODEL_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateDataModel = (id: string, request: UpdateDataModelRequest): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(`${DATA_MODEL_PATH}/${id}/actions/update`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const updateDataModelFields = (id: string, request: UpdateDataModelFieldsRequest): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(`${DATA_MODEL_PATH}/${id}/actions/update-fields`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export type DataModelCommand = 'publish' | 'disable' | 'enable';

export const executeDataModelCommand = (id: string, command: DataModelCommand): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(`${DATA_MODEL_PATH}/${id}/actions/${command}`, { method: 'POST' })
);

export const deleteDataModel = (id: string): Promise<void> => (
  requestJson<void>(`${DATA_MODEL_PATH}/${id}/actions/delete`, { method: 'POST' })
);
