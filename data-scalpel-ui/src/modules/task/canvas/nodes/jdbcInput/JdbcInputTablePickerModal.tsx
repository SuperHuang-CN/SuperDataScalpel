import { JdbcTablePickerModal, type TableIdentifier } from '../../../../datasource';
import type { JdbcInputTableSelection } from '../../canvasTypes';

interface JdbcInputTablePickerModalProps {
  open: boolean;
  dataSourceId: string;
  value: readonly JdbcInputTableSelection[];
  onCancel: () => void;
  onConfirm: (tables: JdbcInputTableSelection[]) => void;
}

export const JdbcInputTablePickerModal = ({
  open,
  dataSourceId,
  value,
  onCancel,
  onConfirm,
}: JdbcInputTablePickerModalProps) => {
  const existingOptions = new Map(value.map((selection) => [selection.tableName, selection.readOptions]));
  const selectedTables: TableIdentifier[] = value.map((selection) => ({
    catalog: null,
    schema: null,
    table: selection.tableName,
  }));

  return (
    <JdbcTablePickerModal
      open={open}
      dataSourceId={dataSourceId}
      value={selectedTables}
      selectionMode="multiple"
      title="选择 JDBC 物理表"
      onCancel={onCancel}
      onConfirm={(tables) => onConfirm(tables.map((table) => ({
        tableName: table.table,
        readOptions: existingOptions.get(table.table)?.map((option) => ({ ...option })) ?? [],
      })))}
    />
  );
};
