import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { initializeAccessSession } from './shared/api/accessSession';
import './styles/global.css';
import './app/layout/modeling-workspace.css';
import './shared/theme/resource-workspace.css';
import './app/layout/management-polish.css';
import './shared/theme/feedback.css';

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('未找到前端挂载节点 #root');
}

initializeAccessSession();

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
