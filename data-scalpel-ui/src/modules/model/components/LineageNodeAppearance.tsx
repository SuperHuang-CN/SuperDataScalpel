import { ApiOutlined, ApartmentOutlined, DatabaseOutlined, FieldStringOutlined, GlobalOutlined, TableOutlined } from '@ant-design/icons';
import type { LineageGraphNodeKind } from '../model/dataModel';

const icons = {
  MODEL: TableOutlined, JDBC_TABLE: DatabaseOutlined, EXTERNAL_RESOURCE: GlobalOutlined,
  TASK: ApartmentOutlined, FIELD: FieldStringOutlined, DATA_SERVICE: ApiOutlined,
};

export const LineageKindIcon = ({ kind }: { kind: LineageGraphNodeKind }) => {
  const Icon = icons[kind];
  return <span className={`lineage-kind-icon is-${kind.toLowerCase()}`} aria-hidden><Icon /></span>;
};
