import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps, TabsProps } from 'antd';
import {
  Alert,
  Button,
  Descriptions,
  Dropdown,
  Form,
  Input,
  Modal,
  Space,
  Skeleton,
  Table,
  Tabs,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { dataModelStatusLabels, physicalTableModeLabels } from '../../model';
import { useCurrentUser } from '../../system';
import { MoveStandardDictionaryItemModal } from '../components/MoveStandardDictionaryItemModal';
import { StandardDictionaryDrawer } from '../components/StandardDictionaryDrawer';
import { StandardDictionaryItemDrawer } from '../components/StandardDictionaryItemDrawer';
import { StandardDictionaryValueTypeIcon } from '../components/StandardDictionaryValueTypeIcon';
import {
  useStandardDictionary,
  useStandardDictionaryFieldReferences,
  useStandardDictionaryItemCommand,
  useStandardDictionaryTree,
} from '../hooks/useStandardDictionaries';
import {
  standardDictionaryValueTypeLabels,
  type StandardDictionaryFieldReference,
  type StandardDictionaryTreeNode,
} from '../model/standardDictionary';

interface ReferenceFilters {
  keyword?: string;
}

const escapeDsl = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

export const StandardDictionaryDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [messageApi, contextHolder] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const [dictionaryDrawerOpen, setDictionaryDrawerOpen] = useState(false);
  const [itemDrawerOpen, setItemDrawerOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<StandardDictionaryTreeNode | null>(null);
  const [parentItem, setParentItem] = useState<StandardDictionaryTreeNode | null>(null);
  const [movingItem, setMovingItem] = useState<StandardDictionaryTreeNode | null>(null);
  const [referenceFilters, setReferenceFilters] = useState<ReferenceFilters>({});
  const [referencePage, setReferencePage] = useState(1);
  const [referencePageSize, setReferencePageSize] = useState(20);
  const [referenceForm] = Form.useForm<ReferenceFilters>();
  const currentUser = useCurrentUser();
  const permissions = new Set(currentUser.data?.permissions ?? []);
  const canManage = permissions.has('standard.dictionary.manage');
  const canViewModels = permissions.has('model.view');
  const detailQuery = useStandardDictionary(id);
  const treeQuery = useStandardDictionaryTree(id);
  const itemCommand = useStandardDictionaryItemCommand();
  const referenceRequest = useMemo(() => ({
    search: referenceFilters.keyword?.trim()
      ? `(code:*"${escapeDsl(referenceFilters.keyword.trim())}"* OR name:*"${escapeDsl(referenceFilters.keyword.trim())}"*)`
      : undefined,
    page: referencePage - 1,
    size: referencePageSize,
    sort: 'sortOrder,code',
  }), [referenceFilters, referencePage, referencePageSize]);
  const referencesQuery = useStandardDictionaryFieldReferences(id, referenceRequest, canViewModels);
  const detail = detailQuery.data;
  const dictionary = detail?.dictionary;
  const tree = treeQuery.data ?? [];

  const executeItemCommand = async (
    item: StandardDictionaryTreeNode,
    command: 'enable' | 'disable' | 'delete',
  ) => {
    if (!dictionary) return;
    try {
      await itemCommand.mutateAsync({
        dictionaryId: dictionary.id,
        itemId: item.id,
        command,
        expectedVersion: dictionary.version,
      });
      messageApi.success(command === 'delete' ? '节点已删除' : command === 'enable' ? '节点已启用' : '节点已停用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '节点操作失败');
    }
  };

  const moreItems = (item: StandardDictionaryTreeNode): MenuProps['items'] => [
    {
      key: 'move',
      icon: <SwapOutlined />,
      label: '移动节点',
      onClick: () => setMovingItem(item),
    },
    {
      key: 'delete',
      icon: <DeleteOutlined />,
      danger: true,
      disabled: Boolean((detail?.fieldReferenceCount ?? 0) + (detail?.templateFieldReferenceCount ?? 0)) || item.children.length > 0,
      label: item.children.length > 0
        ? '包含子节点，不能删除'
        : (detail?.fieldReferenceCount ?? 0) + (detail?.templateFieldReferenceCount ?? 0) > 0
          ? '已被引用，只能停用'
          : '删除节点',
      onClick: () => {
        modalApi.confirm({
          rootClassName: 'business-overlay business-modal-overlay',
          title: '删除码表节点',
          content: `确认删除“${item.name}”吗？`,
          okText: '删除',
          okButtonProps: { danger: true },
          cancelText: '取消',
          onOk: () => executeItemCommand(item, 'delete'),
        });
      },
    },
  ];

  const itemColumns: TableProps<StandardDictionaryTreeNode>['columns'] = [
    {
      title: '节点编码',
      dataIndex: 'code',
      width: 230,
      fixed: 'left',
      render: (value: string) => <code>{value}</code>,
    },
    { title: '节点名称', dataIndex: 'name', width: 220, ellipsis: true },
    {
      title: '自身状态',
      dataIndex: 'enabled',
      width: 100,
      render: (enabled: boolean) => <Tag color={enabled ? 'success' : 'default'}>{enabled ? '启用' : '停用'}</Tag>,
    },
    {
      title: '实际可用',
      dataIndex: 'effectiveEnabled',
      width: 110,
      render: (enabled: boolean, row) => enabled
        ? <Tag color="success">可用取值</Tag>
        : <Tooltip title={!row.enabled ? '当前节点已停用' : '码表或上级节点已停用'}><Tag>不可用</Tag></Tooltip>,
    },
    { title: '同级顺序', dataIndex: 'sortOrder', width: 100 },
    { title: '说明', dataIndex: 'description', ellipsis: true, render: (value?: string) => value || '—' },
    ...(canManage ? [{
      title: '操作',
      key: 'actions',
      width: 142,
      fixed: 'right' as const,
      render: (_value: unknown, item: StandardDictionaryTreeNode) => (
        <Space size={2}>
          <Tooltip title="添加子节点">
            <Button
              type="text"
              icon={<PlusOutlined />}
              aria-label={`为${item.name}添加子节点`}
              onClick={() => {
                setEditingItem(null);
                setParentItem(item);
                setItemDrawerOpen(true);
              }}
            />
          </Tooltip>
          <Tooltip title="修改节点">
            <Button
              type="text"
              icon={<EditOutlined />}
              aria-label={`修改节点${item.name}`}
              onClick={() => {
                setEditingItem(item);
                setParentItem(null);
                setItemDrawerOpen(true);
              }}
            />
          </Tooltip>
          <Tooltip title={item.enabled ? '停用节点' : '启用节点'}>
            <Button
              type="text"
              icon={item.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
              aria-label={`${item.enabled ? '停用' : '启用'}节点${item.name}`}
              onClick={() => void executeItemCommand(item, item.enabled ? 'disable' : 'enable')}
            />
          </Tooltip>
          <Dropdown menu={{ items: moreItems(item) }} trigger={['click']}>
            <Tooltip title="更多操作">
              <Button type="text" icon={<MoreOutlined />} aria-label={`${item.name}更多操作`} />
            </Tooltip>
          </Dropdown>
        </Space>
      ),
    }] : []),
  ];

  const referenceColumns: TableProps<StandardDictionaryFieldReference>['columns'] = [
    {
      title: '模型',
      key: 'model',
      width: 240,
      render: (_value, row) => (
        <Space direction="vertical" size={0}>
          <Link to={`/model/${row.modelId}`}>{row.modelName}</Link>
          <code>{row.modelCode}</code>
        </Space>
      ),
    },
    {
      title: '字段',
      key: 'field',
      width: 220,
      render: (_value, row) => <span>{row.fieldName}（<code>{row.fieldCode}</code>）</span>,
    },
    { title: '字段类型', dataIndex: 'fieldType', width: 120 },
    {
      title: '模型状态',
      dataIndex: 'modelStatus',
      width: 110,
      render: (value: StandardDictionaryFieldReference['modelStatus']) => dataModelStatusLabels[value],
    },
    {
      title: '物理表模式',
      dataIndex: 'physicalTableMode',
      width: 120,
      render: (value: StandardDictionaryFieldReference['physicalTableMode']) => physicalTableModeLabels[value],
    },
    { title: 'Schema 版本', dataIndex: 'schemaVersion', width: 110, render: (value: number) => `v${value}` },
  ];

  if (!dictionary && detailQuery.isPending) {
    return <div className="business-detail-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }

  if (!dictionary) {
    return (
      <Alert
        type="error"
        showIcon
        title="码表详情加载失败"
        description={detailQuery.error instanceof Error ? detailQuery.error.message : '码表不存在'}
        action={<Button onClick={() => navigate('/standard/dictionaries')}>返回列表</Button>}
      />
    );
  }

  const tabItems: TabsProps['items'] = [
    {
      key: 'basic',
      label: '基本信息',
      children: (
        <div className="standard-dictionary-detail-panel standard-dictionary-basic-panel">
          <Descriptions size="small" bordered column={2}>
            <Descriptions.Item label="码表编码"><code>{dictionary.code}</code></Descriptions.Item>
            <Descriptions.Item label="码表名称">{dictionary.name}</Descriptions.Item>
            <Descriptions.Item label="取值类型">{standardDictionaryValueTypeLabels[dictionary.valueType]}</Descriptions.Item>
            <Descriptions.Item label="内容版本">v{dictionary.version}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={dictionary.enabled ? 'success' : 'default'}>{dictionary.enabled ? '启用' : '停用'}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="节点数量">{detail.itemCount}</Descriptions.Item>
            <Descriptions.Item label="字段引用">{detail.fieldReferenceCount}</Descriptions.Item>
            <Descriptions.Item label="模板字段引用">{detail.templateFieldReferenceCount}</Descriptions.Item>
            <Descriptions.Item label="说明" span={2}>{dictionary.description || '—'}</Descriptions.Item>
          </Descriptions>
        </div>
      ),
    },
    {
      key: 'items',
      label: `码表项（${detail.itemCount}）`,
      children: (
        <div className="standard-dictionary-detail-panel standard-dictionary-table-panel">
          <div className="management-toolbar">
            <Alert
              showIcon
              type="info"
              title="所有实际启用的节点均可作为业务取值，包括包含子节点的父节点。"
            />
            <Space>
              <Button icon={<ReloadOutlined />} onClick={() => void treeQuery.refetch()}>刷新</Button>
              {canManage && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => {
                    setEditingItem(null);
                    setParentItem(null);
                    setItemDrawerOpen(true);
                  }}
                >
                  新增根节点
                </Button>
              )}
            </Space>
          </div>
          {treeQuery.error && (
            <Alert
              type="error"
              showIcon
              title="码表树加载失败"
              action={<Button onClick={() => void treeQuery.refetch()}>重试</Button>}
            />
          )}
          <Table<StandardDictionaryTreeNode>
            className="management-table"
            size="small"
            rowKey="id"
            columns={itemColumns}
            dataSource={tree}
            loading={treeQuery.isFetching}
            expandable={{ defaultExpandAllRows: true }}
            pagination={false}
            scroll={{ x: 1100, y: '100%' }}
          />
        </div>
      ),
    },
    ...(canViewModels ? [{
      key: 'references',
      label: `引用字段（${detail.fieldReferenceCount}）`,
      children: (
        <div className="standard-dictionary-detail-panel standard-dictionary-table-panel">
          <div className="management-toolbar">
            <Form<ReferenceFilters>
              autoComplete="off"
              form={referenceForm}
              layout="inline"
              onFinish={(value) => {
                setReferenceFilters(value);
                setReferencePage(1);
              }}
            >
              <Form.Item name="keyword" label="字段名称/编码">
                <Input allowClear placeholder="筛选模型字段" />
              </Form.Item>
            </Form>
            <Space>
              <Button type="primary" onClick={() => referenceForm.submit()}>查询</Button>
              <Button onClick={() => {
                referenceForm.resetFields();
                setReferenceFilters({});
                setReferencePage(1);
              }}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={() => void referencesQuery.refetch()}>刷新</Button>
            </Space>
          </div>
          {referencesQuery.error && (
            <Alert
              type="error"
              showIcon
              title="字段引用加载失败"
              action={<Button onClick={() => void referencesQuery.refetch()}>重试</Button>}
            />
          )}
          <Table<StandardDictionaryFieldReference>
            className="management-table"
            size="small"
            rowKey="fieldId"
            columns={referenceColumns}
            dataSource={referencesQuery.data?.content ?? []}
            loading={referencesQuery.isFetching}
            scroll={{ x: 1000, y: '100%' }}
            pagination={{
              current: referencePage,
              pageSize: referencePageSize,
              total: referencesQuery.data?.totalElements ?? 0,
              showSizeChanger: true,
              hideOnSinglePage: false,
              showTotal: (total) => `共 ${total} 项`,
            }}
            onChange={(pagination) => {
              setReferencePage(pagination.current ?? 1);
              setReferencePageSize(pagination.pageSize ?? 20);
            }}
          />
        </div>
      ),
    }] : []),
  ];

  return (
    <div className="standard-dictionary-detail-page business-detail-page">
      {contextHolder}
      {modalContext}
      <header className="standard-dictionary-detail-header business-detail-header">
        <div className="standard-dictionary-detail-identity">
          <div className="standard-dictionary-detail-title-row">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/standard/dictionaries')}>返回列表</Button>
            <span className="business-detail-resource-icon business-detail-resource-icon-purple">
              <StandardDictionaryValueTypeIcon valueType={dictionary.valueType} />
            </span>
            <span className="standard-dictionary-detail-title">{dictionary.name}</span>
            <Tag color={dictionary.enabled ? 'success' : 'default'}>{dictionary.enabled ? '启用' : '停用'}</Tag>
          </div>
          <div className="standard-dictionary-detail-subtitle">
            <code>{dictionary.code}</code>
            <span>·</span>
            <span>{standardDictionaryValueTypeLabels[dictionary.valueType]}</span>
            <span>·</span>
            <span>内容版本 v{dictionary.version}</span>
          </div>
        </div>
        <Space size={4}>
          <Tooltip title="刷新码表">
            <Button
              icon={<ReloadOutlined />}
              aria-label="刷新码表详情"
              loading={detailQuery.isFetching || treeQuery.isFetching}
              onClick={() => void Promise.all([
                detailQuery.refetch(),
                treeQuery.refetch(),
                ...(canViewModels ? [referencesQuery.refetch()] : []),
              ])}
            />
          </Tooltip>
          {canManage && <Button icon={<EditOutlined />} onClick={() => setDictionaryDrawerOpen(true)}>修改</Button>}
        </Space>
      </header>
      <Tabs className="standard-dictionary-detail-tabs business-detail-tabs" items={tabItems} defaultActiveKey="items" />
      <StandardDictionaryDrawer
        open={dictionaryDrawerOpen}
        dictionary={dictionary}
        onClose={() => setDictionaryDrawerOpen(false)}
      />
      <StandardDictionaryItemDrawer
        open={itemDrawerOpen}
        dictionary={dictionary}
        item={editingItem}
        parent={parentItem}
        referenced={detail.fieldReferenceCount + detail.templateFieldReferenceCount > 0}
        onClose={() => {
          setItemDrawerOpen(false);
          setEditingItem(null);
          setParentItem(null);
        }}
      />
      <MoveStandardDictionaryItemModal
        open={Boolean(movingItem)}
        dictionary={dictionary}
        item={movingItem}
        tree={tree}
        onClose={() => setMovingItem(null)}
      />
    </div>
  );
};
