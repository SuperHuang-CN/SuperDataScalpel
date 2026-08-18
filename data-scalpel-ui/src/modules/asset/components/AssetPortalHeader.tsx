import {
  LogoutOutlined,
  SwapOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Tooltip } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { hasAccessToken } from '../../../shared/api/http';
import { useCurrentUser, useLogout } from '../../system';

interface AssetPortalHeaderProps {
  page: 'home' | 'detail';
}

export const AssetPortalHeader = ({ page }: AssetPortalHeaderProps) => {
  const location = useLocation();
  const navigate = useNavigate();
  const logout = useLogout();
  const currentUserQuery = useCurrentUser();
  const authenticated = hasAccessToken() && Boolean(currentUserQuery.data);

  const openLogin = () => navigate('/login', { state: { from: location } });
  const enterManagement = () => {
    if (authenticated) navigate('/');
    else navigate('/login', { state: { from: { pathname: '/' } } });
  };
  const navigateHome = (target?: 'discovery' | 'categories') => {
    if (page === 'home') {
      const elementId = target === 'discovery' ? 'asset-portal-discovery' : target === 'categories' ? 'asset-portal-categories' : undefined;
      if (elementId) document.getElementById(elementId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      else window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    navigate(target ? `/assets#${target}` : '/assets');
  };

  return (
    <header className="asset-portal-header">
      <div className="asset-portal-header-inner">
        <button type="button" className="asset-portal-brand" aria-label="返回数据资产门户首页" onClick={() => navigateHome()}>
          <span className="asset-portal-brand-mark"><img src="/data-scalpel-mark.svg" alt="" /></span>
          <span className="asset-portal-brand-copy"><strong>DataScalpel</strong><span>数据资产门户</span></span>
        </button>
        <nav className="asset-portal-navigation" aria-label="数据资产门户导航">
          <button type="button" className={`asset-portal-nav-item ${page === 'home' ? 'asset-portal-nav-item-active' : ''}`} onClick={() => navigateHome()}>门户首页</button>
          <button type="button" className="asset-portal-nav-item" onClick={() => navigateHome('discovery')}>资产浏览</button>
          <button type="button" className="asset-portal-nav-item" onClick={() => navigateHome('categories')}>业务领域</button>
        </nav>
        <div className="asset-portal-user-actions">
          <Button className="asset-portal-management-button" icon={<SwapOutlined />} onClick={enterManagement}>进入管理后台</Button>
          {authenticated ? (
            <>
              <Avatar size={30} icon={<TeamOutlined />} className="asset-portal-user-avatar" />
              <span className="asset-portal-username">{currentUserQuery.data?.username}</span>
              <Tooltip title="退出登录">
                <Button
                  type="text"
                  icon={<LogoutOutlined />}
                  aria-label="退出登录"
                  onClick={() => {
                    logout();
                    navigate(`${location.pathname}${location.search}`, { replace: true });
                  }}
                />
              </Tooltip>
            </>
          ) : (
            <Button type="primary" className="asset-portal-login-button" onClick={openLogin}>登录</Button>
          )}
        </div>
      </div>
    </header>
  );
};
