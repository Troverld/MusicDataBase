import React from 'react';
import { Link } from 'react-router-dom';
import { Artist } from '../../types';
import { usePermissions, useArtistPermission } from '../../hooks/usePermissions';
import './ModernArtistCard.css';

interface ModernArtistCardProps {
  artist: Artist;
  onEdit: (artist: Artist) => void;
  onDelete: (artistID: string) => void;
}

const ModernArtistCard: React.FC<ModernArtistCardProps> = ({ artist, onEdit, onDelete }) => {
  const { isAdmin } = usePermissions();
  const { canEdit, loading: permissionLoading } = useArtistPermission(artist.artistID);

  const showEditButton = !permissionLoading && (canEdit || isAdmin);
  const showDeleteButton = !permissionLoading && isAdmin;

  // 获取艺术家名字的首字母作为头像
  const getInitials = (name: string) => {
    const words = name.trim().split(' ');
    if (words.length >= 2) {
      return words[0][0] + words[1][0];
    }
    return name.slice(0, 2);
  };

  // 获取权限标签
  const getPermissionBadge = () => {
    if (permissionLoading) return null;
    
    if (isAdmin) {
      return (
        <div className="artist-permission-badge admin">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
          </svg>
          管理员
        </div>
      );
    } else if (canEdit) {
      return (
        <div className="artist-permission-badge editable">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
          可编辑
        </div>
      );
    } else {
      return (
        <div className="artist-permission-badge view-only">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          仅查看
        </div>
      );
    }
  };

  return (
    <div className="modern-artist-card">
      {permissionLoading && <div className="artist-card-loading" />}
      
      {getPermissionBadge()}
      
      <div className="artist-card-header">
        <div className="artist-avatar">
          <span className="artist-avatar-text">{getInitials(artist.name)}</span>
        </div>
        
        <div className="artist-info">
          <h3 className="artist-name">{artist.name}</h3>
          <span className="artist-id">{artist.artistID}</span>
        </div>
      </div>
      
      <div className="artist-card-content">
        <p className={`artist-bio ${!artist.bio ? 'empty' : ''}`}>
          {artist.bio || '暂无简介'}
        </p>
      </div>
      
      <div className="artist-card-actions">
        <Link 
          to={`/artists/${artist.artistID}`}
          className="artist-action-btn primary"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          查看详情
        </Link>
        
        {showEditButton && (
          <button 
            className="artist-action-btn secondary" 
            onClick={() => onEdit(artist)}
            disabled={permissionLoading}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            编辑
          </button>
        )}
        
        {showDeleteButton && (
          <button 
            className="artist-action-btn danger" 
            onClick={() => onDelete(artist.artistID)}
            disabled={permissionLoading}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
            删除
          </button>
        )}
      </div>
    </div>
  );
};

export default ModernArtistCard;