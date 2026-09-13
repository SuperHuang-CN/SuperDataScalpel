import { DatePicker, Form, Input, InputNumber, Select } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useDataEntryOptions } from '../hooks/useDataEntry';
import { queryDataEntryOptions } from '../api/dataEntryApi';
import type { DataEntryField, DataEntryOption } from '../model/dataEntry';

const optionStatusLabel: Record<string, string> = {
  DISABLED: '（已停用）', MISSING: '（无匹配项）', SOURCE_UNAVAILABLE: '（来源不可用）', ACTIVE: '',
};

export const DataEntryInputField = ({ formId, field, disabled = false, existingValue, preserveDatePrecision = false }: {
  formId: string; field: DataEntryField; disabled?: boolean; existingValue?: unknown; preserveDatePrecision?: boolean;
}) => {
  const optionMutation = useDataEntryOptions(formId, field.id);
  const [options, setOptions] = useState<DataEntryOption[]>([]);
  const hasOptions = field.inputSource === 'DICTIONARY' || field.inputSource === 'MODEL_LOOKUP';
  const selectOptions = useMemo(() => options.map((option) => ({
    value: option.value as string | number | boolean,
    label: `${option.displayLabel}${optionStatusLabel[option.status] ?? ''}`,
    disabled: option.status !== 'ACTIVE',
  })), [options]);

  const loadOptions = async (keyword?: string) => {
    const response = await optionMutation.mutateAsync({ keyword, pageNo: 1, pageSize: 50 });
    setOptions(response.content);
  };

  useEffect(() => {
    if (!hasOptions || existingValue === null || existingValue === undefined) return;
    void queryDataEntryOptions(formId, field.id, { values: [existingValue] }).then((response) => {
      setOptions((current) => [...response.content, ...current.filter((item) => item.value !== existingValue)]);
    });
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
        onDropdownVisibleChange={(open) => { if (open && !options.length) void loadOptions(); }}
        onSearch={(keyword) => void loadOptions(keyword)}
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
