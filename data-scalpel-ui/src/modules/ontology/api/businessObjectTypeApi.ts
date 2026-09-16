import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  BusinessObjectCandidates, BusinessObjectPreview, BusinessObjectRelatedPreview, BusinessObjectRelationSummary,
  BusinessObjectType, BusinessObjectTypeBasics, BusinessObjectTypeDefinition, BusinessObjectTypeGraph,
  BusinessObjectTypeValidation,
} from '../model/businessObjectType';

const base = '/v1/business-object-types';
const post = <T>(path: string, body?: unknown): Promise<T> => requestJson(path, {
  method: 'POST', ...(body === undefined ? {} : { body: JSON.stringify(body) }),
});

export const fetchBusinessObjectTypes = (request: SearchRequest): Promise<PageResponse<BusinessObjectType>> => {
  const query = toSearchParams(request).toString();
  return requestJson(query ? `${base}?${query}` : base);
};
export const fetchBusinessObjectTypeGraph = (): Promise<BusinessObjectTypeGraph> => requestJson(`${base}/graph`);
export const fetchBusinessObjectType = (id: string): Promise<BusinessObjectType> => requestJson(`${base}/${id}`);
export const createBusinessObjectType = (request: BusinessObjectTypeBasics & { code: string }): Promise<BusinessObjectType> => post(base, request);
export const updateBusinessObjectType = (id: string, request: BusinessObjectTypeBasics): Promise<BusinessObjectType> => post(`${base}/${id}/actions/update`, request);
export const saveBusinessObjectTypeDefinition = (id: string, definition: BusinessObjectTypeDefinition): Promise<BusinessObjectType> => post(`${base}/${id}/actions/update-definition`, { definition });
export const validateBusinessObjectType = (id: string): Promise<BusinessObjectTypeValidation> => requestJson(`${base}/${id}/validation`);
export const executeBusinessObjectTypeCommand = (id: string, command: 'enable' | 'disable'): Promise<BusinessObjectType> => post(`${base}/${id}/actions/${command}`);
export const deleteBusinessObjectType = (id: string): Promise<void> => post(`${base}/${id}/actions/delete`);
export const fetchBusinessObjectRelations = (id: string): Promise<BusinessObjectRelationSummary[]> => requestJson(`${base}/${id}/relations`);
export const queryBusinessObjectCandidates = (id: string, pageNo = 1, pageSize = 20): Promise<BusinessObjectCandidates> => post(`${base}/${id}/actions/query-preview-candidates`, { pageNo, pageSize });
export const queryBusinessObjectPreview = (id: string, objectKey: string): Promise<BusinessObjectPreview> => post(`${base}/${id}/actions/query-preview`, { objectKey });
export const queryBusinessObjectRelated = (id: string, request: { objectKey: string; relationId: string; direction: 'OUTBOUND' | 'INBOUND'; pageNo?: number; pageSize?: number }): Promise<BusinessObjectRelatedPreview> => post(`${base}/${id}/actions/query-preview-related`, request);
