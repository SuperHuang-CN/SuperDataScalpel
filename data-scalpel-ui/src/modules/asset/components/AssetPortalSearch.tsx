import { ArrowRightOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Input } from 'antd';
import { useState } from 'react';

export const AssetPortalSearch = ({ initialValue = '', onSearch }: { initialValue?: string; onSearch: (keyword: string) => void }) => {
  const [value, setValue] = useState(initialValue);
  return <form className="portal-search" role="search" autoComplete="off" onSubmit={e => { e.preventDefault(); onSearch(value.trim()); }}>
    <Input prefix={<SearchOutlined />} name="assetKeyword" aria-label="搜索资产名称、编码、简介或标签" placeholder="搜索名称、编码、简介或标签" value={value} onChange={e => setValue(e.target.value)} maxLength={100} autoComplete="off" allowClear />
    <Button type="primary" htmlType="submit">搜索资产 <ArrowRightOutlined /></Button>
  </form>;
};
