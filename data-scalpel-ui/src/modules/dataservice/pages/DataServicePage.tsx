import { useCurrentUser } from '../../system';
import { DataServiceListPanel } from '../components/DataServiceListPanel';

export const DataServicePage = () => {
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);

  return (
    <DataServiceListPanel
      canCreate={permissions.has('service.create')}
      canUpdate={permissions.has('service.update')}
      canDelete={permissions.has('service.delete')}
      canPublish={permissions.has('service.publish')}
      canViewDirectories={permissions.has('directory.view')}
      canManageDirectories={permissions.has('directory.manage')}
      canViewModels={permissions.has('model.view')}
      canViewDataSources={permissions.has('datasource.view')}
      canViewEngines={permissions.has('service.engine.view')}
    />
  );
};
