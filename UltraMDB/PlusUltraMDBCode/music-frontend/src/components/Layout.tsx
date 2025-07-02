import React from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { clearAuth, getUser } from '../utils/storage';
import { authService } from '../services/auth.service';

const Layout: React.FC = () => {
  const navigate = useNavigate();
  const user = getUser();

  const handleLogout = async () => {
    if (user && user.userToken) {
      try {
        await authService.logout(user.userID, user.userToken);
      } catch (error) {
        console.error('Logout error:', error);
      }
    }
    clearAuth();
    navigate('/login');
  };

  return (
    <>
      <nav className="navbar">
        <div className="navbar-content">
          <div>
            <Link to="/">Music Management System</Link>
          </div>
          <div>
            <Link to="/">Dashboard</Link>
            <Link to="/songs">Songs</Link>
            <Link to="/artists">Artists</Link>
            <Link to="/bands">Bands</Link>
            <Link to="/genres">Genres</Link>
            <a href="#" onClick={handleLogout}>Logout</a>
          </div>
        </div>
      </nav>
      <div className="container">
        <Outlet />
      </div>
    </>
  );
};

export default Layout;