import { FolderOpenOutlined, FolderOutlined } from '@ant-design/icons';
import { directoryTreeSelectData, type DirectoryTreeNode, type DirectoryTreeSelectNode } from '../model/directory';

const folderIcon = ({ expanded }: { expanded?: boolean }) => expanded ? <FolderOpenOutlined /> : <FolderOutlined />;

interface DirectoryOption extends DirectoryTreeSelectNode {
  icon: typeof folderIcon;
  children?: DirectoryOption[];
}

const withFolderIcons = (nodes: DirectoryTreeSelectNode[]): DirectoryOption[] => nodes.map(node => ({
  ...node,
  icon: folderIcon,
  children: node.children ? withFolderIcons(node.children) : undefined,
}));

/** Native tree icons receive expansion state; labels remain plain searchable text. */
export const directoryTreeSelectOptions = (nodes: DirectoryTreeNode[]) => withFolderIcons(directoryTreeSelectData(nodes));
