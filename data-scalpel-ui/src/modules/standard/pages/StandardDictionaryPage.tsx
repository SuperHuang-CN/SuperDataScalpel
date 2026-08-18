import {
  DeleteOutlined,
  EditOutlined,
  ExportOutlined,
  ImportOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import {
  Alert,
  Button,
  Dropdown,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tooltip,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementMoreFilters, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { useCurrentUser } from '../../system';
import { StandardDictionaryDrawer } from '../components/StandardDictionaryDrawer';
import { StandardDictionaryImportDrawer } from '../components/StandardDictionaryImportDrawer';
import { StandardDictionaryValueTypeIcon } from '../components/StandardDictionaryValueTypeIcon';
import {
  useExportStandardDictionaryMetadata,
  useStandardDictionaries,
  useStandardDictionaryCommand,
} from '../hooks/useStandardDictionaries';
import {
  standardDictionaryValueTypeLabels,
  type StandardDictionary,
  type StandardDictionaryValueType,
} from '../model/standardDictionary';

interface Filters {
  code?: string;
  name?: string;
  valueType?: StandardDictionaryValueType;
  enabled?: boolean;
}

const escapeDsl = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const buildSearch = (filters: Filters) => {
  const conditions = [
    filters.code?.trim() ? `code:*"${escapeDsl(filters.code.trim().toUpperCase())}"*` : undefined,
    filters.name?.trim() ? `name:*"${escapeDsl(filters.name.trim())}"*` : undefined,
    filters.valueType ? `valueType:"${filters.valueType}"` : undefined,
    filters.enabled === undefined ? undefined : `enabled:"${filters.enabled}"`,
  ].filter((condition): condition is string => Boolean(condition));
  return conditions.length ? conditions.join(' AND ') : undefined;
};

const valueTypeOptions = (
  Object.entries(standardDictionaryValueTypeLabels) as [StandardDictionaryValueType, string][]
).map(([value, label]) => ({ value, label }));

const valueTypeIconTones = {
  STRING: 'violet',
  INTEGER: 'cyan',
  LONG: 'blue',
  DECIMAL: 'orange',
  BOOLEAN: 'green',
} as const satisfies Record<StandardDictionaryValueType, 'blue' | 'violet' | 'cyan' | 'green' | 'orange'>;

export const StandardDictionaryPage = () => {
  const [form] = Form.useForm<Filters>();
  const [advancedForm] = Form.useForm<Filters>();
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [advancedFilters, setAdvancedFilters] = useState<Filters>({});
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<StandardDictionary | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [messageApi, contextHolder] = message.useMessage();
  const currentUser = useCurrentUser();
  const canManage = currentUser.data?.permissions.includes('standard.dictionary.manage') ?? false;
  const queryRequest = useMemo(() => ({
    search: buildSearch(filters),
    page: page - 1,
    size: pageSize,
    sort: '-updatedAt,code',
  }), [filters, page, pageSize]);
  const dictionariesQuery = useStandardDictionaries(queryRequest);
  const advancedFilterCount = Number(advancedFilters.valueType !== undefined) + Number(advancedFilters.enabled !== undefined);
  const commandMutation = useStandardDictionaryCommand();
  const exportMutation = useExportStandardDictionaryMetadata();

  const executeCommand = async (
    dictionary: StandardDictionary,
    command: 'enable' | 'disable' | 'delete',
  ) => {
    try {
      await commandMutation.mutateAsync({
        id: dictionary.id,
        command,
        expectedVersion: dictionary.version,
      });
      messageApi.success(command === 'delete' ? '码表已删除' : command === 'enable' ? '码表已启用' : '码表已停用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '码表操作失败');
    }
  };

  const exportSelected = async () => {
    if (!selectedIds.length) return;
    try {
      const blob = await exportMutation.mutateAsync(selectedIds);
      downloadBlob(blob, `DataScalpel-码表元数据-${new Date().toISOString().slice(0, 10)}.xlsx`);
      messageApi.success('码表导出已开始');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '导出码表失败');
    }
  };

  const columns: TableProps<StandardDictionary>['columns'] = [
    {
      title: '码表', dataIndex: 'name', width: 300,
      render: (value: string, row) => (
        <ManagementListCell
          icon={<StandardDictionaryValueTypeIcon valueType={row.valueType} />}
          iconLabel={`取值类型：${standardDictionaryValueTypeLabels[row.valueType]}`}
          iconTone={valueTypeIconTones[row.valueType]}
          primary={<Link to={`/standard/dictionaries/${row.id}`}>{value}</Link>}
          secondary={<Link to={`/standard/dictionaries/${row.id}`}><ManagementCode value={row.code} /></Link>}
        />
      ),
    },
    {
      title: '状态 / 版本', width: 140,
      render: (_value: unknown, row) => (
        <ManagementListCell
          primary={<ManagementStatusIndicator label={row.enabled ? '启用' : '停用'} tone={row.enabled ? 'success' : 'default'} />}
          secondary={`内容版本 v${row.version}`}
        />
      ),
    },
    {
      title: '说明',
      dataIndex: 'description',
      render: (value?: string) => (
        <ManagementListCell
          className="standard-dictionary-description-cell"
          primary={value ? <Tooltip title={value}><span>{value}</span></Tooltip> : '—'}
        />
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 160,
      render: (value: string) => <ManagementDateTime value={value} />,
    },
    ...(canManage ? [{
      title: '操作',
      key: 'actions',
      width: 112,
      render: (_value: unknown, row: StandardDictionary) => (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title="修改码表"><Button type="text" icon={<EditOutlined />} aria-label={`修改码表${row.name}`} onClick={() => { setEditing(row); setDrawerOpen(true); }} /></Tooltip>
            <Tooltip title={row.enabled ? '停用码表' : '启用码表'}><Button type="text" icon={row.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />} aria-label={`${row.enabled ? '停用' : '启用'}码表${row.name}`} onClick={() => void executeCommand(row, row.enabled ? 'disable' : 'enable')} /></Tooltip>
          </div>
          <Dropdown menu={{ items: [
            { key: 'edit', icon: <EditOutlined />, label: '修改', onClick: () => { setEditing(row); setDrawerOpen(true); } },
            { key: 'lifecycle', icon: row.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />, label: row.enabled ? '停用' : '启用', onClick: () => void executeCommand(row, row.enabled ? 'disable' : 'enable') },
            { type: 'divider' },
            { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true, onClick: () => Modal.confirm({ rootClassName: 'business-overlay business-modal-overlay', title: '删除码表', content: `确认删除“${row.name}”及其全部树节点吗？`, okText: '删除', okButtonProps: { danger: true }, cancelText: '取消', onOk: () => executeCommand(row, 'delete') }) },
          ] satisfies MenuProps['items'] }}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<MoreOutlined />} aria-label={`${row.name}的更多操作`} /></Tooltip></Dropdown>
        </div>
      ),
    }] : []),
  ];
  const applyDirect = (values: Filters) => {
    setFilters({ code: values.code, name: values.name, valueType: advancedFilters.valueType, enabled: advancedFilters.enabled });
    setPage(1);
  };
  const confirmAdvanced = () => {
    const values = advancedForm.getFieldsValue();
    setAdvancedFilters({ valueType: values.valueType, enabled: values.enabled });
    setAdvancedOpen(false);
  };
  const clearAdvanced = () => advancedForm.resetFields();
  const reset = () => { form.resetFields(); advancedForm.resetFields(); setAdvancedFilters({}); setAdvancedOpen(false); setFilters({}); setPage(1); };

  return (
    <div className="management-page">
      {contextHolder}
      <section className="management-workbench">
        <div className="management-filter-strip">
          <Form<Filters>
            autoComplete="off"
            form={form}
            layout="inline"
            onFinish={applyDirect}
          >
            <Form.Item name="code"><ManagementSearchInput allowClear placeholder="搜索码表编码" /></Form.Item>
            <Form.Item name="name"><Input allowClear placeholder="搜索码表名称" /></Form.Item>
            <ManagementMoreFilters
              count={advancedFilterCount}
              open={advancedOpen}
              onOpenChange={(open) => {
                setAdvancedOpen(open);
                if (open) {
                  advancedForm.resetFields();
                  advancedForm.setFieldsValue({ valueType: advancedFilters.valueType, enabled: advancedFilters.enabled });
                }
              }}
              onClear={clearAdvanced}
              onCancel={() => setAdvancedOpen(false)}
              onConfirm={confirmAdvanced}
            >
              <Form<Filters> form={advancedForm} layout="vertical" autoComplete="off">
                <Form.Item name="valueType" label="类型"><Select allowClear placeholder="全部" options={valueTypeOptions} className="advanced-filter-select" /></Form.Item>
                <Form.Item name="enabled" label="状态"><Select allowClear placeholder="全部" options={[{ value: true, label: '启用' }, { value: false, label: '停用' }]} className="advanced-filter-select" /></Form.Item>
              </Form>
            </ManagementMoreFilters>
          </Form>
          <ManagementFilterActions form={form} appliedFilters={filters} additionalActive={advancedFilterCount > 0} loading={dictionariesQuery.isFetching} onReset={reset} />
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <div className="management-result-title">码表管理 <span className="management-result-count">共 {dictionariesQuery.data?.totalElements ?? 0} 项</span></div>
          <Space size={4} className="management-result-actions">
            <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新码表列表" onClick={() => void dictionariesQuery.refetch()} /></Tooltip>
            <Button
              icon={<ExportOutlined />}
              disabled={!selectedIds.length}
              loading={exportMutation.isPending}
              onClick={() => void exportSelected()}
            >
              导出
            </Button>
            {canManage && (
              <>
                <Button icon={<ImportOutlined />} onClick={() => setImportOpen(true)}>导入</Button>
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => { setEditing(null); setDrawerOpen(true); }}
                >
                  新建码表
                </Button>
              </>
            )}
          </Space>
          </div>
          {dictionariesQuery.error && (
            <Alert
              type="error"
              showIcon
              title="码表加载失败"
              description={dictionariesQuery.error instanceof Error ? dictionariesQuery.error.message : undefined}
              action={<Button onClick={() => void dictionariesQuery.refetch()}>重试</Button>}
            />
          )}
          <Table<StandardDictionary>
          className="management-table"
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={dictionariesQuery.data?.content ?? []}
          loading={dictionariesQuery.isFetching}
          rowSelection={{
            selectedRowKeys: selectedIds,
            onChange: (keys) => setSelectedIds(keys.map(String)),
          }}
          scroll={{ y: '100%' }}
          pagination={{
            current: page,
            pageSize,
            total: dictionariesQuery.data?.totalElements ?? 0,
            showSizeChanger: true,
            hideOnSinglePage: false,
            showTotal: (total) => `共 ${total} 项`,
            placement: ['bottomEnd'],
          }}
          onChange={(pagination) => {
            setPage(pagination.current ?? 1);
            setPageSize(pagination.pageSize ?? 20);
          }}
          />
        </div>
      </section>
      <StandardDictionaryDrawer
        open={drawerOpen}
        dictionary={editing}
        onClose={() => { setDrawerOpen(false); setEditing(null); }}
      />
      <StandardDictionaryImportDrawer open={importOpen} onClose={() => setImportOpen(false)} />
    </div>
  );
};
