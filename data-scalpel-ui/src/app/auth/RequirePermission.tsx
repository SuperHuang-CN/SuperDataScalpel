import { Result } from 'antd';
import type { PropsWithChildren } from 'react';
import { useCurrentUser } from '../../modules/system';

interface RequirePermissionProps extends PropsWithChildren {
  permission: string;
}

export const RequirePermission = ({ permission, children }: RequirePermissionProps) => {
  const currentUserQuery = useCurrentUser();
  if (!currentUserQuery.data?.permissions.includes(permission)) {
    return <Result status="403" title="无访问权限" subTitle="当前账号未被授予此功能的访问权限。" />;
  }
  return children;
};
