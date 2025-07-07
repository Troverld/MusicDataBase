import React from 'react';
import { Link } from 'react-router-dom';
import { Artist } from '../../types';
import { usePermissions, useArtistPermission } from '../../hooks/usePermissions';

interface ArtistItemProps {
  artist: Artist;
  onEdit: (artist: Artist) => void;
  onDelete: (artistID: string) => void;
}

const ArtistItem: React.FC<ArtistItemProps> = ({ artist, onEdit, onDelete }) => {
  const { isAdmin } = usePermissions();
  const { canEdit, loading: permissionLoading } = useArtistPermission(artist.artistID);

  const showEditButton = !permissionLoading && (canEdit || isAdmin);
  const showDeleteButton = !permissionLoading && isAdmin;

  return (
    <div className="song-item">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '15px' }}>
        <Link 
          to={`/artists/${artist.artistID}`}
          style={{ textDecoration: 'none', color: 'inherit' }}
        >
          <h3 style={{ cursor: 'pointer', color: '#007bff' }}>{artist.name} →</h3>
        </Link>
        
        {permissionLoading && (
          <div className="loading-spinner" title="正在检查权限..."></div>
        )}
      </div>
      
      <div style={{ marginBottom: '15px' }}>
        <p><strong>艺术家ID:</strong> 
          <span style={{ 
            fontFamily: 'monospace', 
            backgroundColor: '#f8f9fa', 
            padding: '2px 6px', 
            borderRadius: '3px',
            marginLeft: '8px',
            fontSize: '12px'
          }}>
            {artist.artistID}
          </span>
        </p>
      </div>

      <div style={{ marginBottom: '15px' }}>
        <strong>简介:</strong>
        <div style={{ 
          marginTop: '8px', 
          padding: '12px', 
          backgroundColor: '#f8f9fa', 
          borderRadius: '4px',
          lineHeight: '1.5',
          fontSize: '14px'
        }}>
          {artist.bio || '暂无简介'}
        </div>
      </div>

      {artist.managers && artist.managers.length > 0 && (
        <div style={{ marginBottom: '15px' }}>
          <strong>管理者:</strong>
          <div style={{ marginTop: '5px' }}>
            {artist.managers.map((manager, index) => (
              <span key={index} className="chip" style={{ backgroundColor: '#e3f2fd', color: '#1976d2' }}>
                {manager}
              </span>
            ))}
          </div>
        </div>
      )}

      {!permissionLoading && !canEdit && !isAdmin && (
        <div className="permission-denied">
          ⚠️ 您没有编辑此艺术家的权限
        </div>
      )}

      <div className="song-actions">
        <Link 
          to={`/artists/${artist.artistID}`}
          className="btn btn-primary"
          style={{ textDecoration: 'none', marginRight: '10px' }}
        >
          查看详情
        </Link>
        
        {showEditButton && (
          <button 
            className="btn btn-secondary" 
            onClick={() => onEdit(artist)}
            disabled={permissionLoading}
          >
            编辑
          </button>
        )}
        
        {showDeleteButton && (
          <button 
            className="btn btn-danger" 
            onClick={() => onDelete(artist.artistID)}
            disabled={permissionLoading}
          >
            删除
          </button>
        )}
        
        {!showEditButton && !showDeleteButton && !permissionLoading && (
          <span style={{ color: '#666', fontSize: '14px' }}>
            仅查看模式
          </span>
        )}
      </div>
    </div>
  );
};

export default ArtistItem;