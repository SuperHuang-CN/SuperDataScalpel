import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  BookOutlined,
  DeleteOutlined,
  DownOutlined,
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
import { Button, Dropdown, Form, Input, Modal, Select, Table, Tooltip, message } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementName, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementAdaptiveMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
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
  const advancedSnapshot = useRef<Filters>({});
  const advancedDraft = Form.useWatch((values: Filters) => values, { form: advancedForm, preserve: true });
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
  const advancedFilterCount = Number(advancedDraft?.valueType !== undefined) + Number(advancedDraft?.enabled !== undefined);
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
          primary={<ManagementName name={row.name} code={row.code} description={row.description}><Link to={`/standard/dictionaries/${row.id}`}>{value}</Link></ManagementName>}
          secondary={row.code !== row.name ? <Link to={`/standard/dictionaries/${row.id}`}><ManagementCode value={row.code} /></Link> : undefined}
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
      align: 'center' as const,
      fixed: 'right' as const,
      width: 120,
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
            { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true, onClick: () => Modal.confirm({ rootClassName: 'business-overlay business-modal-overlay workspace-resource-overlay modeling-overlay', title: <OverlayTitle icon={<DeleteOutlined />} title="删除码表" tone="danger" />, icon: null, content: `确认删除“${row.name}”及其全部树节点吗？`, okText: '删除', okButtonProps: { danger: true }, cancelText: '取消', onOk: () => executeCommand(row, 'delete') }) },
          ] satisfies MenuProps['items'] }}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<MoreOutlined />} aria-label={`${row.name}的更多操作`} /></Tooltip></Dropdown>
        </div>
      ),
    }] : []),
  ];
  const applyDirect = (values: Filters) => {
    const advancedValues = advancedForm.getFieldsValue(true);
    const nextAdvancedFilters = { valueType: advancedValues.valueType, enabled: advancedValues.enabled };
    setFilters({ code: values.code, name: values.name, ...nextAdvancedFilters });
    setPage(1);
  };
  const confirmAdvanced = () => setAdvancedOpen(false);
  const cancelAdvanced = () => { if (advancedOpen) advancedForm.setFieldsValue({ valueType: undefined, enabled: undefined, ...advancedSnapshot.current }); setAdvancedOpen(false); };
  const clearAdvanced = () => advancedForm.setFieldsValue({ valueType: undefined, enabled: undefined });
  const reset = () => { form.resetFields(); advancedForm.resetFields(); advancedForm.setFieldsValue({ valueType: undefined, enabled: undefined }); setAdvancedOpen(false); setFilters({}); setPage(1); };

  return (
    <div className="management-page modeling-workspace">
      {contextHolder}
      <section className="management-workbench">
        <div className="management-filter-strip modeling-list-controls">
          <Form<Filters>
            id="dictionary-list-filters"
            name="dictionary-list-filters"
            autoComplete="off"
            form={form}
            layout="inline"
            onFinish={applyDirect}
          >
            <Form.Item name="code"><ManagementSearchInput allowClear placeholder="搜索码表编码" /></Form.Item>
            <Form.Item name="name"><Input allowClear placeholder="搜索码表名称" /></Form.Item>
          </Form>
            <ManagementAdaptiveMoreFilters
              count={advancedFilterCount}
              open={advancedOpen}
              onOpenChange={open => { if (open) { advancedSnapshot.current = advancedForm.getFieldsValue(true); setAdvancedOpen(true); } else cancelAdvanced(); }}
              onClear={clearAdvanced}
              onCancel={cancelAdvanced}
              onConfirm={confirmAdvanced}
            >
              <Form<Filters> form={advancedForm} layout="vertical" className="modeling-advanced-filters" autoComplete="off" onFinish={() => { setAdvancedOpen(false); form.submit(); }}>
                <Form.Item name="valueType" label="类型"><Select allowClear placeholder="全部类型" options={valueTypeOptions} className="advanced-filter-select" /></Form.Item>
                <Form.Item name="enabled" label="状态"><Select allowClear placeholder="全部状态" options={[{ value: true, label: '启用' }, { value: false, label: '停用' }]} className="advanced-filter-select" /></Form.Item>
              </Form>
            </ManagementAdaptiveMoreFilters>
          <div className="modeling-page-actions management-filter-actions"><ManagementFilterActions formId="dictionary-list-filters" form={form} appliedFilters={filters} additionalActive={advancedFilterCount > 0} loading={dictionariesQuery.isFetching} onReset={reset} /><div className="modeling-page-commands">
            <Dropdown trigger={['click']} classNames={{ root: 'workspace-resource-menu' }} menu={{ items: [
              ...(canManage ? [{ key: 'import', icon: <ImportOutlined />, label: '导入码表' }] : []),
              { key: 'export', icon: <ExportOutlined />, label: selectedIds.length ? `导出勾选码表（${selectedIds.length}）` : '导出码表（请先勾选）', disabled: !selectedIds.length || exportMutation.isPending },
            ], onClick: ({ key }) => { if (key === 'import') setImportOpen(true); else void exportSelected(); } }}>
              <Button loading={exportMutation.isPending}>{canManage ? '导入/导出' : '导出'} <DownOutlined /></Button>
            </Dropdown>
            <Tooltip title="刷新列表"><Button icon={<ReloadOutlined />} aria-label="刷新码表列表" onClick={() => void dictionariesQuery.refetch()} /></Tooltip>
            {canManage && (
              <>
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => { setEditing(null); setDrawerOpen(true); }}
                >
                  新建码表
                </Button>
              </>
            )}
          </div></div>
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <div className="management-result-title"><BookOutlined aria-hidden />码表管理 <span className="management-result-count">共 {dictionariesQuery.data?.totalElements ?? 0} 项</span></div>

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
