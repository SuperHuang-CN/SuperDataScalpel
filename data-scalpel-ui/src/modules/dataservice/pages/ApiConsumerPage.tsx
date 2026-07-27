import { useCurrentUser } from '../../system';
import { ApiConsumerManagementPanel } from '../components/ApiConsumerManagementPanel';

export const ApiConsumerPage = () => {
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);

  return (
    <ApiConsumerManagementPanel
      canManage={permissions.has('service.update')}
      canConfigureAccess={permissions.has('service.publish')}
    />
  );
};
