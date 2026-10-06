import { Button } from 'antd';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { hasAccessToken } from '../../shared/api/http';
import { InlineFeedback } from '../../shared/components/ContextualFeedback';
import { useCurrentUser } from '../../modules/system';
import { PageLoading } from '../routing/PageLoading';

export const RequireAuthentication = () => {
  const location = useLocation();
  const currentUserQuery = useCurrentUser();

  if (!hasAccessToken()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  if (currentUserQuery.isPending) {
    return <PageLoading label="正在验证登录状态…" layout="overview" fullPage />;
  }
  if (currentUserQuery.isError) {
    return <main className="authentication-loading"><InlineFeedback tone="error" label="登录状态验证暂时失败" detail={currentUserQuery.error.message}
      action={<Button onClick={() => void currentUserQuery.refetch()}>重试</Button>} /></main>;
  }
  return <Outlet />;
};
