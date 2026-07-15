import { useCurrentUser } from '../../system';
import { ServiceEngineManagementPanel } from '../components/ServiceEngineManagementPanel';

export const ServiceEnginePage = () => {
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);

  return (
    <ServiceEngineManagementPanel
      canCreate={permissions.has('service.engine.create')}
      canUpdate={permissions.has('service.engine.update')}
      canDelete={permissions.has('service.engine.delete')}
      canTest={permissions.has('service.engine.test')}
      canViewDataSources={permissions.has('datasource.view')}
    />
  );
};
