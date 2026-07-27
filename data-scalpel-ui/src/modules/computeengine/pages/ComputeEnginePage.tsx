import { useCurrentUser } from '../../system';
import { ComputeEngineManagementPanel } from '../components/ComputeEngineManagementPanel';

export const ComputeEnginePage = () => {
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  return <ComputeEngineManagementPanel
    canCreate={permissions.has('compute.engine.create')}
    canUpdate={permissions.has('compute.engine.update')}
    canDelete={permissions.has('compute.engine.delete')}
    canTest={permissions.has('compute.engine.test')}
    canManage={permissions.has('compute.engine.manage')}
  />;
};
