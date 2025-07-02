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
        <div style={{ 
          marginTop: '20px', 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', 
          gap: '15px',
          maxWidth: '800px'
        }}>
          <Link to="/songs" className="btn btn-primary" style={{ 
            textAlign: 'center', 
            textDecoration: 'none',
            padding: '20px',
            borderRadius: '8px',
            display: 'block'
          }}>
            🎵 Manage Songs
          </Link>
          
          <Link to="/artists" className="btn btn-primary" style={{ 
            textAlign: 'center', 
            textDecoration: 'none',
            padding: '20px',
            borderRadius: '8px',
            display: 'block'
          }}>
            🎤 Manage Artists
          </Link>
          
          <Link to="/genres" className="btn btn-primary" style={{ 
            textAlign: 'center', 
            textDecoration: 'none',
            padding: '20px',
            borderRadius: '8px',
            display: 'block'
          }}>
            🎼 Manage Genres
          </Link>
        </div>
      </div>

      <div style={{ 
        background: '#f8f9fa', 
        padding: '25px', 
        borderRadius: '8px', 
        marginTop: '40px',
        border: '1px solid #e9ecef'
      }}>
        <h3 style={{ marginBottom: '15px', color: '#495057' }}>📊 System Overview</h3>
        <div style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6' }}>
          <p><strong>Song Management:</strong> Upload, edit, and delete songs with metadata management.</p>
          <p><strong>Artist Management:</strong> Create and manage artist profiles with biographical information.</p>
          <p><strong>Genre Management:</strong> Define and organize music genres for better categorization.</p>
          <p><strong>Admin Features:</strong> Full administrative control over all system content and users.</p>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;