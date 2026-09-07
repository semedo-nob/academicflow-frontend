import { Navigate, Outlet } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { Sidebar } from '../components/layout/Sidebar';
import { Topbar } from '../components/layout/Topbar';
import { Drawer } from '../components/ui/Drawer';

export function AppLayout() {
  const { authenticated, drawer, closeDrawer } = useApp();

  if (!authenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <div id="app" style={{ display: 'flex' }}>
      <Sidebar />
      <div className="main-col">
        <Topbar />
        <main className="content">
          <Outlet />
        </main>
      </div>
      <Drawer open={!!drawer} onClose={closeDrawer}>
        {drawer}
      </Drawer>
    </div>
  );
}
