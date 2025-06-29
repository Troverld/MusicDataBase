import React from 'react';
import { Navigate } from 'react-router-dom';
import { getToken } from '../utils/storage';

interface PrivateRouteProps {
  children: React.ReactElement;
}

const PrivateRoute: React.FC<PrivateRouteProps> = ({ children }) => {
  const token = getToken();
  
  if (!token) {
    return <Navigate to="/login" />;
  }
  
  return children;
};

export default PrivateRoute;