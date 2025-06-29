import React from 'react';
import { Link } from 'react-router-dom';
import { getUser } from '../utils/storage';

const Dashboard: React.FC = () => {
  const user = getUser();

  return (
    <div>
      <h1>Welcome to Music Management System</h1>
      <p>Hello, {user?.account}!</p>
      
      <div style={{ marginTop: '40px' }}>
        <h2>Quick Actions</h2>
        <div style={{ marginTop: '20px' }}>
          <Link to="/songs" className="btn btn-primary">
            Manage Songs
          </Link>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;