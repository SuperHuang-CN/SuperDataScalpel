import { Button, Modal, Radio, Select, Space, Typography } from 'antd';
import { useState } from 'react';
import type { BatchWriteOptions, CanvasColumnSchema, CanvasFilterCondition, JdbcWriteMode } from '../canvasTypes';
import { FilterConditionTreeEditor } from './processors/FilterProcessorInspector';
import { createDefaultFilterCondition, validateFilterConditionDraft } from './processors/filterConditionDraft';

export const BatchWriteFields = ({ value, onChange, mode, columns, databaseType }: {
  value?: BatchWriteOptions | null;
  onChange?: (value: BatchWriteOptions | null) => void;
  mode: JdbcWriteMode | null;
  columns: CanvasColumnSchema[];
  databaseType?: string;
}) => {
  const [draft, setDraft] = useState<CanvasFilterCondition | null>(null);
  const [error, setError] = useState<string | null>(null);
  const supported = Boolean(databaseType && ['POSTGRESQL', 'MYSQL', 'ORACLE', 'SQL_SERVER', 'OPENGAUSS'].includes(databaseType));
  const eligible = columns.filter(c => !['GEOMETRY', 'BINARY'].includes(c.fieldType));
  const confirmWiden = (next: BatchWriteOptions | null) => Modal.confirm({
    title: '扩大覆盖范围？',
    content: `这会删除目标表全部旧数据并写入本次结果，请确认目标和字段映射。${next === null ? '直接提交按分区写入，失败可能部分成功。' : ''}`,
    okText: '确认全表覆盖', cancelText: '取消', okButtonProps: { danger: true }, onOk: () => onChange?.(next),
  });
  return <Space orientation="vertical" size={8} style={{ width: '100%' }}>
    <Select style={{ width: '100%' }} aria-label="提交保障" value={value ? 'ATOMIC' : 'DIRECT'}
      options={[{ value: 'ATOMIC', label: '原子提交 · 单目标事务', disabled: !supported },
        { value: 'DIRECT', label: '直接提交 · 可能部分成功（原有方式）' }]}
      onChange={v => {
        if (v === 'DIRECT' && mode === 'OVERWRITE' && value?.overwriteCondition) confirmWiden(null);
        else onChange?.(v === 'ATOMIC' ? { overwriteCondition: null, allowEmptyOverwrite: false } : null);
      }} />
    {value && <Typography.Text type="secondary">中间表装载完成后提交；需要目标库建表与删表权限。</Typography.Text>}
    {!value && mode === 'OVERWRITE' && <Typography.Text type="warning">直接覆盖先清空目标，后续失败可能只写入部分数据。</Typography.Text>}
    {value && mode === 'OVERWRITE' && <>
      <Typography.Text>覆盖范围</Typography.Text>
      <Radio.Group value={value.overwriteCondition ? 'MATCHING' : 'ALL'} onChange={e => {
        if (e.target.value === 'ALL') confirmWiden({ ...value, overwriteCondition: null });
        else onChange?.({ ...value, overwriteCondition: createDefaultFilterCondition(eligible[0]) });
      }} options={[{ value: 'ALL', label: '全部数据' }, { value: 'MATCHING', label: '指定条件' }]} />
      {value.overwriteCondition ? <>
        <Button disabled={!columns.length} onClick={() => {
          const condition = structuredClone(value.overwriteCondition!);
          setDraft(condition.kind === 'GROUP' ? condition : { kind: 'GROUP', operator: 'AND', children: [condition] });
          setError(null);
        }}>编辑删除条件</Button>
        <Typography.Text type="secondary">只替换范围内数据；条件字段须参与映射，输入越界则不提交。</Typography.Text>
      </> : <Typography.Text type="warning">提交成功后目标全部旧数据被替换。</Typography.Text>}
      <Typography.Text>输入为空时</Typography.Text>
      <Select aria-label="空输入策略" style={{ width: '100%' }} value={value.allowEmptyOverwrite ? 'CLEAR' : 'FAIL'}
        options={[{ value: 'FAIL', label: '保留原数据并报错' }, { value: 'CLEAR', label: '允许清空覆盖范围' }]}
        onChange={policy => {
          const allowEmptyOverwrite = policy === 'CLEAR';
          if (!allowEmptyOverwrite) onChange?.({ ...value, allowEmptyOverwrite });
          else Modal.confirm({ title: '允许空输入清空数据？', content: '任务输出为空时也会删除选定范围的旧数据。',
            okText: '允许清空', cancelText: '取消', okButtonProps: { danger: true },
            onOk: () => onChange?.({ ...value, allowEmptyOverwrite }) });
        }} />
    </>}
    <Modal title="覆盖范围 · 目标字段条件" open={draft !== null} width={880} destroyOnHidden
      onCancel={() => setDraft(null)} okText="应用条件" cancelText="取消" onOk={() => {
        if (!draft || !value) return;
        const issue = validateFilterConditionDraft(draft);
        if (issue) { setError(issue); return; }
        onChange?.({ ...value, overwriteCondition: draft }); setDraft(null);
      }}>
      {draft && <FilterConditionTreeEditor condition={draft} columns={eligible} onChange={setDraft}
        allowedOperators={['EQUALS', 'NOT_EQUALS', 'GREATER_THAN', 'GREATER_THAN_OR_EQUALS',
          'LESS_THAN', 'LESS_THAN_OR_EQUALS', 'IN', 'NOT_IN', 'IS_NULL', 'IS_NOT_NULL']} />}
      {error && <Typography.Text type="danger">{error}</Typography.Text>}
      <Typography.Paragraph type="secondary">支持比较、IN/NOT IN、空值判断及 AND/OR。此处只保存条件，不执行删除。</Typography.Paragraph>
    </Modal>
  </Space>;
};
