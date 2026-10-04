import { DatePicker, Form, Input, InputNumber, Select } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useDataEntryOptions } from '../hooks/useDataEntry';
import { queryDataEntryOptions } from '../api/dataEntryApi';
import type { DataEntryField, DataEntryOption } from '../model/dataEntry';

const optionStatusLabel: Record<string, string> = {
  DISABLED: '（已停用）', MISSING: '（无匹配项）', SOURCE_UNAVAILABLE: '（来源不可用）', ACTIVE: '',
};
const SEARCH_DELAY_MS = 350;

export const DataEntryInputField = ({ formId, field, disabled = false, existingValue, preserveDatePrecision = false }: {
  formId: string; field: DataEntryField; disabled?: boolean; existingValue?: unknown; preserveDatePrecision?: boolean;
}) => {
  const optionMutation = useDataEntryOptions(formId, field.id);
  const [options, setOptions] = useState<DataEntryOption[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchError, setSearchError] = useState(false);
  const selectedValueRef = useRef(existingValue);
  const hasOptions = field.inputSource === 'DICTIONARY' || field.inputSource === 'MODEL_LOOKUP';
  const selectOptions = useMemo(() => options.map((option) => ({
    value: option.value as string | number | boolean,
    label: `${option.displayLabel}${optionStatusLabel[option.status] ?? ''}`,
    disabled: option.status !== 'ACTIVE',
  })), [options]);

  const loadOptions = optionMutation.mutateAsync;

  useEffect(() => {
    if (!hasOptions || !searchOpen || disabled) return;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      void loadOptions({ keyword: searchKeyword.trim() || undefined, pageNo: 1, pageSize: 50, signal: controller.signal })
        .then((response) => {
          if (controller.signal.aborted) return;
          setSearchError(false);
          setOptions((current) => {
            const selected = current.find((option) => Object.is(option.value, selectedValueRef.current));
            return selected && !response.content.some((option) => Object.is(option.value, selected.value))
              ? [selected, ...response.content] : response.content;
          });
        })
        .catch(() => {
          if (!controller.signal.aborted) {
            setOptions((current) => current.filter((option) => Object.is(option.value, selectedValueRef.current)));
            setSearchError(true);
          }
        });
    }, searchKeyword ? SEARCH_DELAY_MS : 0);
    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [disabled, field.id, formId, hasOptions, loadOptions, searchKeyword, searchOpen]);

  useEffect(() => {
    selectedValueRef.current = existingValue;
    if (!hasOptions || existingValue === null || existingValue === undefined) return;
    const controller = new AbortController();
    void queryDataEntryOptions(formId, field.id, { values: [existingValue] }, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) setOptions((current) => [
          ...response.content, ...current.filter((item) => !Object.is(item.value, existingValue)),
        ]);
      })
      .catch(() => {});
    return () => controller.abort();
  }, [existingValue, field.id, formId, hasOptions]);

  const label = `${field.name}（${field.code}）`;
  const rules = field.nullable && !field.primaryKey ? [] : [{ required: true, message: `请填写${field.name}` }];
  let control;
  if (hasOptions) {
    control = (
      <Select
        disabled={disabled}
        allowClear={field.nullable && !field.primaryKey}
        showSearch
        filterOption={false}
        options={selectOptions}
        loading={optionMutation.isPending}
        notFoundContent={searchError ? '选项查询失败，请修改关键词重试' : undefined}
        onOpenChange={(open) => {
          setSearchOpen(open);
          if (open) setSearchError(false);
          if (!open) setSearchKeyword('');
        }}
        onSearch={(keyword) => {
          setSearchError(false);
          setSearchKeyword(keyword);
        }}
        onChange={(value) => { selectedValueRef.current = value; }}
        placeholder={field.inputSource === 'DICTIONARY' ? '请选择码表值' : '搜索关联模型'}
      />
    );
  } else if (field.fieldType === 'BOOLEAN') {
    control = <Select disabled={disabled} allowClear={field.nullable} options={[{ value: true, label: '是' }, { value: false, label: '否' }]} />;
  } else if (['BYTE', 'SHORT', 'INTEGER'].includes(field.fieldType)) {
    control = <InputNumber disabled={disabled} precision={0} style={{ width: '100%' }} />;
  } else if (['LONG', 'DECIMAL'].includes(field.fieldType)) {
    control = <InputNumber disabled={disabled} stringMode style={{ width: '100%' }} />;
  } else if (['FLOAT', 'DOUBLE'].includes(field.fieldType)) {
    control = <InputNumber disabled={disabled} style={{ width: '100%' }} />;
  } else if (preserveDatePrecision && ['DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ'].includes(field.fieldType)) {
    control = <Input disabled={disabled} />;
  } else if (field.fieldType === 'DATE') {
    control = <DatePicker disabled={disabled} style={{ width: '100%' }} />;
  } else if (field.fieldType === 'TIMESTAMP' || field.fieldType === 'TIMESTAMP_NTZ') {
    control = <DatePicker disabled={disabled} showTime={{ format: 'HH:mm:ss.SSSSSS' }} style={{ width: '100%' }} />;
  } else if (field.fieldType === 'STRING' && (!field.length || field.length > 500)) {
    control = <Input.TextArea disabled={disabled} rows={4} maxLength={field.length ?? undefined} />;
  } else {
    control = <Input disabled={disabled} maxLength={field.length ?? undefined} />;
  }
  return <Form.Item name={field.code} label={label} rules={rules} extra={field.description}>{control}</Form.Item>;
};
