import {
  ClearOutlined,
  DeleteOutlined,
  DownOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { AutoComplete, Button, Empty, Input, Modal, Space, Tooltip, Typography } from 'antd';
import { useMemo, useState } from 'react';
import type { JdbcInputReadOption } from '../../canvasTypes';

const MAX_OPTIONS = 32;
const MAX_VALUE_LENGTH = 4096;
const namePattern = /^[A-Za-z][A-Za-z0-9._-]{0,127}$/;
const sensitiveNamePattern = /(?:password|passwd|pwd|secret|token|credential|api[-_.]?key|access[-_.]?key|secret[-_.]?key|private[-_.]?key)/i;
const reservedNames = new Set([
  'url', 'driver', 'user', 'password', 'dbtable', 'query', 'preparequery', 'customschema',
  'keytab', 'principal', 'refreshkrb5config', 'connectionprovider',
  'catalog', 'schema', 'currentschema', 'database', 'databasename',
  'truncate', 'cascadetruncate', 'createtableoptions', 'createtablecolumntypes', 'batchsize',
  'isolationlevel', 'tablecomment',
  'partitioncolumn', 'lowerbound', 'upperbound', 'numpartitions',
]);
const booleanNames = new Set([
  'pushdownpredicate', 'pushdownaggregate', 'pushdownlimit', 'pushdownoffset',
  'pushdowntablesample', 'pushdownjoin', 'prefertimestampntz',
]);
const nonNegativeIntegerNames = new Set(['fetchsize', 'querytimeout']);

const suggestedNames = [
  'fetchsize',
  'queryTimeout',
  'sessionInitStatement',
  'pushDownPredicate',
  'pushDownAggregate',
  'pushDownLimit',
  'pushDownOffset',
  'pushDownTableSample',
  'pushDownJoin',
  'preferTimestampNTZ',
  'hint',
];

interface JdbcInputTableReadOptionsModalProps {
  open: boolean;
  tableName: string;
  value: readonly JdbcInputReadOption[];
  onCancel: () => void;
  onConfirm: (options: JdbcInputReadOption[]) => void;
}

const cloneOptions = (options: readonly JdbcInputReadOption[]) => options.map((option) => ({ ...option }));

const optionIssues = (options: readonly JdbcInputReadOption[]) => {
  const issues = new Map<number, string[]>();
  const add = (index: number, message: string) => {
    const current = issues.get(index) ?? [];
    issues.set(index, [...current, message]);
  };
  if (options.length > MAX_OPTIONS) {
    options.forEach((_option, index) => add(index, `每张表最多 ${MAX_OPTIONS} 个参数`));
  }
  const names = new Set<string>();
  options.forEach((option, index) => {
    if (!namePattern.test(option.name)) {
      add(index, '参数名必须以字母开头，且只能包含字母、数字、.、_、-');
      return;
    }
    const normalized = option.name.toLowerCase();
    if (names.has(normalized)) add(index, '参数名不能重复（忽略大小写）');
    names.add(normalized);
    if (reservedNames.has(normalized) || sensitiveNamePattern.test(option.name)) {
      add(index, '该参数由平台控制、属于敏感连接信息，或预留给分片读取');
    }
    if (option.value.length > MAX_VALUE_LENGTH) add(index, `参数值不能超过 ${MAX_VALUE_LENGTH} 个字符`);
    if (normalized === 'sessioninitstatement' && !option.value.trim()) {
      add(index, 'sessionInitStatement 不能为空');
    }
    if (booleanNames.has(normalized) && !/^(true|false)$/i.test(option.value)) {
      add(index, '该参数只能为 true 或 false');
    }
    if (nonNegativeIntegerNames.has(normalized) && (!/^\d+$/.test(option.value) || Number(option.value) > 2_147_483_647)) {
      add(index, '该参数必须是非负整数');
    }
  });
  return issues;
};

const suggestedValueOptions = (name: string) => booleanNames.has(name.toLowerCase())
  ? [{ value: 'true' }, { value: 'false' }]
  : [];

export const JdbcInputTableReadOptionsModal = ({
  open,
  tableName,
  value,
  onCancel,
  onConfirm,
}: JdbcInputTableReadOptionsModalProps) => {
  if (!open) return null;
  return (
    <JdbcInputTableReadOptionsModalContent
      key={`${tableName}:${JSON.stringify(value)}`}
      tableName={tableName}
      value={value}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
};

interface JdbcInputTableReadOptionsModalContentProps {
  tableName: string;
  value: readonly JdbcInputReadOption[];
  onCancel: () => void;
  onConfirm: (options: JdbcInputReadOption[]) => void;
}

const JdbcInputTableReadOptionsModalContent = ({
  tableName,
  value,
  onCancel,
  onConfirm,
}: JdbcInputTableReadOptionsModalContentProps) => {
  const [options, setOptions] = useState<JdbcInputReadOption[]>(() => cloneOptions(value));

  const issues = useMemo(() => optionIssues(options), [options]);
  const update = (index: number, patch: Partial<JdbcInputReadOption>) => {
    setOptions((current) => current.map((option, optionIndex) => (
      optionIndex === index ? { ...option, ...patch } : option
    )));
  };
  const move = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= options.length) return;
    setOptions((current) => {
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };
  const confirm = () => onConfirm(options.filter((option) => option.name !== '' || option.value !== ''));

  return (
    <Modal
      open
      width={680}
      className="canvas-jdbc-input-read-options-modal"
      title={<>读取配置 <Typography.Text type="secondary">· {tableName}</Typography.Text></>}
      onCancel={onCancel}
      footer={(
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" onClick={confirm}>保存读取配置</Button>
        </Space>
      )}
    >
      <div className="canvas-jdbc-input-read-options-header">
        <Typography.Text type="secondary">
          参数会按字符串传给 Spark JDBC；未识别参数可能成为 JDBC Driver Property。
        </Typography.Text>
        <Tooltip title="sessionInitStatement 会在每个 JDBC 连接建立后执行。请勿填写密码、Token 等凭据；分片读取参数将由后续专门配置提供。">
          <InfoCircleOutlined aria-label="读取参数使用说明" />
        </Tooltip>
      </div>
      <div className="canvas-jdbc-input-read-options-toolbar">
        <Typography.Text strong>高级读取参数</Typography.Text>
        <Space size={4}>
          {options.length > 0 && (
            <Button
              type="text"
              size="small"
              icon={<ClearOutlined />}
              onClick={() => setOptions([])}
            >
              清空
            </Button>
          )}
          <Button
            type="dashed"
            size="small"
            icon={<PlusOutlined />}
            disabled={options.length >= MAX_OPTIONS}
            onClick={() => setOptions((current) => [...current, { name: '', value: '' }])}
          >
            添加参数
          </Button>
        </Space>
      </div>
      {options.length === 0 ? (
        <div className="canvas-jdbc-input-read-options-empty">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="使用默认 Spark JDBC 读取行为" />
        </div>
      ) : (
        <div className="canvas-jdbc-input-read-options-list">
          <div className="canvas-jdbc-input-read-options-column-header" aria-hidden="true">
            <span>参数名</span><span>参数值</span><span />
          </div>
          {options.map((option, index) => {
            const rowIssues = issues.get(index) ?? [];
            return (
              <div className={`canvas-jdbc-input-read-option${rowIssues.length > 0 ? ' is-invalid' : ''}`} key={`${index}:${option.name}`}>
                <AutoComplete
                  allowClear
                  options={suggestedNames.map((name) => ({ value: name }))}
                  value={option.name}
                  onChange={(name) => update(index, { name })}
                  placeholder="Spark JDBC 参数名"
                >
                  <Input autoComplete="off" name={`jdbc-read-option-name-${index}`} />
                </AutoComplete>
                {suggestedValueOptions(option.name).length > 0 ? (
                  <AutoComplete
                    allowClear
                    options={suggestedValueOptions(option.name)}
                    value={option.value}
                    onChange={(value) => update(index, { value })}
                  >
                    <Input.TextArea
                      autoComplete="off"
                      name={`jdbc-read-option-value-${index}`}
                      autoSize={{ minRows: 1, maxRows: 4 }}
                      placeholder="参数值"
                    />
                  </AutoComplete>
                ) : (
                  <Input.TextArea
                    autoComplete="off"
                    name={`jdbc-read-option-value-${index}`}
                    value={option.value}
                    autoSize={{ minRows: 1, maxRows: 4 }}
                    placeholder="参数值"
                    onChange={(event) => update(index, { value: event.target.value })}
                  />
                )}
                <Space size={0} className="canvas-jdbc-input-read-option-actions">
                  <Tooltip title="上移"><Button type="text" size="small" disabled={index === 0} icon={<UpOutlined />} aria-label={`上移读取参数 ${index + 1}`} onClick={() => move(index, -1)} /></Tooltip>
                  <Tooltip title="下移"><Button type="text" size="small" disabled={index === options.length - 1} icon={<DownOutlined />} aria-label={`下移读取参数 ${index + 1}`} onClick={() => move(index, 1)} /></Tooltip>
                  <Tooltip title="删除"><Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`删除读取参数 ${index + 1}`} onClick={() => setOptions((current) => current.filter((_item, optionIndex) => optionIndex !== index))} /></Tooltip>
                </Space>
                {rowIssues.length > 0 && <Typography.Text className="canvas-jdbc-input-read-option-error" type="danger">{rowIssues.join('；')}</Typography.Text>}
              </div>
            );
          })}
        </div>
      )}
    </Modal>
  );
};
