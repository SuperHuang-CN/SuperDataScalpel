export type DirectoryScope = 'DATA_SOURCE' | 'MODEL' | 'DATA_SERVICE';

export interface DirectoryTreeNode {
  id: string;
  scope: DirectoryScope;
  parentId: string | null;
  name: string;
  sortOrder: number;
  description: string | null;
  directResourceCount: number;
  resourceCount: number;
  children: DirectoryTreeNode[];
}

export interface DirectoryResponse {
  id: string;
  scope: DirectoryScope;
  parentId: string | null;
  name: string;
  sortOrder: number;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDirectoryRequest {
  scope: DirectoryScope;
  parentId?: string;
  name: string;
  sortOrder: number;
  description?: string;
}

export interface UpdateDirectoryRequest {
  parentId?: string;
  name: string;
  sortOrder: number;
  description?: string;
}

export interface DirectoryTreeSelectNode {
  value: string;
  title: string;
  children?: DirectoryTreeSelectNode[];
}

export const directoryTreeSelectData = (nodes: DirectoryTreeNode[]): DirectoryTreeSelectNode[] => nodes.map((node) => ({
  value: node.id,
  title: node.name,
  children: node.children.length ? directoryTreeSelectData(node.children) : undefined,
}));

export const findDirectoryDescendantIds = (nodes: DirectoryTreeNode[], id: string): string[] => {
  for (const node of nodes) {
    if (node.id === id) return [node.id, ...collectDirectoryIds(node.children)];
    const nested = findDirectoryDescendantIds(node.children, id);
    if (nested.length) return nested;
  }
  return [];
};

const collectDirectoryIds = (nodes: DirectoryTreeNode[]): string[] => nodes.flatMap((node) => [
  node.id,
  ...collectDirectoryIds(node.children),
]);
