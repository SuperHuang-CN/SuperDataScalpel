import { Spin } from 'antd';
import { useEffect } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { clearAccessToken, hasAccessToken } from '../../shared/api/http';
import { useCurrentUser } from '../../modules/system';

export const RequireAuthentication = () => {
  const location = useLocation();
  const currentUserQuery = useCurrentUser();

  useEffect(() => {
    if (currentUserQuery.isError) clearAccessToken();
  }, [currentUserQuery.isError]);

  if (!hasAccessToken() || currentUserQuery.isError) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  if (currentUserQuery.isPending) {
    return <main className="authentication-loading"><Spin tip="正在验证登录状态…" /></main>;
  }
  return <Outlet />;
};
