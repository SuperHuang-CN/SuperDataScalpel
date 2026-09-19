import { useQuery, type QueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import { fetchBusinessObjectRelations, fetchBusinessObjectType, fetchBusinessObjectTypeGraph, fetchBusinessObjectTypes } from '../api/businessObjectTypeApi';

const key = 'business-object-types';
export const useBusinessObjectTypes = (request: SearchRequest, enabled = true) => useQuery({ queryKey: [key, 'list', request], queryFn: () => fetchBusinessObjectTypes(request), enabled });
export const useBusinessObjectTypeGraph = (enabled = true) => useQuery({ queryKey: [key, 'graph'], queryFn: fetchBusinessObjectTypeGraph, enabled });
export const useBusinessObjectType = (id: string) => useQuery({ queryKey: [key, 'detail', id], queryFn: () => fetchBusinessObjectType(id), enabled: Boolean(id) });
export const useBusinessObjectRelations = (id: string) => useQuery({ queryKey: [key, 'relations', id], queryFn: () => fetchBusinessObjectRelations(id), enabled: Boolean(id) });
export const invalidateBusinessObjectTypes = (client: QueryClient) => Promise.all([
  client.invalidateQueries({ queryKey: [key] }),
  invalidateDirectoryTree(client, 'BUSINESS_OBJECT'),
  client.invalidateQueries({ queryKey: ['data-models'] }),
]);
