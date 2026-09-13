import { Button, Checkbox, Input, Radio } from 'antd';
import { useState } from 'react';
import type { Interaction } from '../model/types';
import { Markdown } from './Markdown';
export function Questions({ interaction, disabled, submit }: { interaction: Interaction; disabled: boolean; submit: (answers: {id:string; selected:string[]; custom?:string}[]) => Promise<void> }) {
  const [values, setValues] = useState<Record<string, {selected: string[]; custom: string}>>({});
  const [sending, setSending] = useState(false); const [error, setError] = useState('');
  return <section className="dsh-questions" aria-label="助手追问">{interaction.questions.map(q => {
    const value = values[q.id] ?? { selected: [], custom: '' };
    const select = (selected: string[]) => setValues(old => ({ ...old, [q.id]: { selected, custom: q.multiSelect ? value.custom : '' } }));
    const options = q.options?.map(option => ({ value: option.label, label: <span>{option.label}{option.description && <small>{option.description}</small>}</span> }));
    return <div key={q.id} className="dsh-question"><strong>{q.header ?? '请回答'}</strong><Markdown text={q.question} />
      {q.detail && <Markdown text={q.detail} />}
      {options?.length ? q.multiSelect ? <Checkbox.Group options={options} value={value.selected} onChange={v => select(v.map(String))} disabled={disabled || sending} />
        : <Radio.Group options={options} value={value.selected[0]} onChange={e => select([String(e.target.value)])} disabled={disabled || sending} /> : null}
      <Input.TextArea autoComplete="off" aria-label={`${q.question}：自由回答`} placeholder="输入自己的回答" value={value.custom} disabled={disabled || sending}
        onChange={e => setValues(old => ({ ...old, [q.id]: { selected: q.multiSelect ? value.selected : [], custom: e.target.value } }))} autoSize={{minRows:2,maxRows:5}} />
    </div>;
  })}<Button type="primary" disabled={disabled} loading={sending} onClick={() => {
    const answers = interaction.questions.map(q => ({ id:q.id, selected:values[q.id]?.selected ?? [], ...(values[q.id]?.custom.trim() ? {custom:values[q.id].custom.trim()} : {}) }));
    if (answers.some(v => !v.selected.length && !v.custom)) { setError('请回答所有问题'); return; }
    setSending(true); setError(''); void submit(answers).catch(e => setError(e instanceof Error ? e.message : '提交失败')).finally(() => setSending(false));
  }}>提交回答</Button>{error && <div role="alert" className="dsh-error">{error}</div>}</section>;
}
