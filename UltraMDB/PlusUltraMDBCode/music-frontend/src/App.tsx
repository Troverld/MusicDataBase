import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import SongManagement from './pages/SongManagement';
import GenreManagement from './pages/GenreManagement';
import ArtistManagement from './pages/ArtistManagement';
import BandManagement from './pages/BandManagement';
import ArtistDetail from './pages/ArtistDetail';
import BandDetail from './pages/BandDetail';
import UserProfile from './pages/UserProfile';
import Layout from './components/Layout';
import PrivateRoute from './components/PrivateRoute';
import './App.css';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/" element={<PrivateRoute><Layout /></PrivateRoute>}>
          <Route index element={<Dashboard />} />
          <Route path="songs" element={<SongManagement />} />
          <Route path="genres" element={<GenreManagement />} />
          <Route path="artists" element={<ArtistManagement />} />
          <Route path="artists/:artistID" element={<ArtistDetail />} />
          <Route path="bands" element={<BandManagement />} />
          <Route path="bands/:bandID" element={<BandDetail />} />
          <Route path="profile" element={<UserProfile />} />
        </Route>
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  );
}

export default App;