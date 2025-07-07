import React from 'react';
import { Link } from 'react-router-dom';
import { Band } from '../../types';
import { usePermissions, useBandPermission } from '../../hooks/usePermissions';

interface BandItemProps {
  band: Band;
  onEdit: (band: Band) => void;
  onDelete: (bandID: string) => void;
  memberNamesDisplay: { [bandID: string]: string[] };
}

const BandItem: React.FC<BandItemProps> = ({ 
  band, 
  onEdit, 
  onDelete, 
  memberNamesDisplay 
}) => {
  const { isAdmin } = usePermissions();
  const { canEdit, loading: permissionLoading } = useBandPermission(band.bandID);

  const showEditButton = !permissionLoading && (canEdit || isAdmin);
  const showDeleteButton = !permissionLoading && isAdmin;

  return (
    <div className="song-item">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '15px' }}>
        <Link 
          to={`/bands/${band.bandID}`}
          style={{ textDecoration: 'none', color: 'inherit' }}
        >
          <h3 style={{ cursor: 'pointer', color: '#007bff' }}>{band.name} →</h3>
        </Link>
        
        {permissionLoading && (
          <div className="loading-spinner" title="正在检查权限..."></div>
        )}
      </div>
      
      <div style={{ marginBottom: '15px' }}>
        <p><strong>乐队ID:</strong> 
          <span style={{ 
            fontFamily: 'monospace', 
            backgroundColor: '#f8f9fa', 
            padding: '2px 6px', 
            borderRadius: '3px',
            marginLeft: '8px',
            fontSize: '12px'
          }}>
            {band.bandID}
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
          {band.bio || '暂无简介'}
        </div>
      </div>

      <div style={{ marginBottom: '15px' }}>
        <strong>乐队成员:</strong>
        <div style={{ marginTop: '5px' }}>
          {memberNamesDisplay[band.bandID] && memberNamesDisplay[band.bandID].length > 0 ? (
            memberNamesDisplay[band.bandID].map((memberName, index) => (
              <span key={index} className="chip" style={{ backgroundColor: '#fff3cd', color: '#856404' }}>
                {memberName}
              </span>
            ))
          ) : (band.members && band.members.length > 0) ? (
            band.members.map((member, index) => (
              <span key={index} className="chip" style={{ backgroundColor: '#f8d7da', color: '#721c24' }}>
                {member} (未解析)
              </span>
            ))
          ) : (
            <span className="chip" style={{ backgroundColor: '#f8f9fa', color: '#6c757d' }}>
              暂无成员
            </span>
          )}
        </div>
      </div>

      {band.managers && band.managers.length > 0 && (
        <div style={{ marginBottom: '15px' }}>
          <strong>管理者:</strong>
          <div style={{ marginTop: '5px' }}>
            {band.managers.map((manager, index) => (
              <span key={index} className="chip" style={{ backgroundColor: '#e3f2fd', color: '#1976d2' }}>
                {manager}
              </span>
            ))}
          </div>
        </div>
      )}

      {!permissionLoading && !canEdit && !isAdmin && (
        <div className="permission-denied">
          ⚠️ 您没有编辑此乐队的权限
        </div>
      )}

      <div className="song-actions">
        <Link 
          to={`/bands/${band.bandID}`}
          className="btn btn-primary"
          style={{ textDecoration: 'none', marginRight: '10px' }}
        >
          查看详情
        </Link>
        
        {showEditButton && (
          <button 
            className="btn btn-secondary" 
            onClick={() => onEdit(band)}
            disabled={permissionLoading}
          >
            编辑
          </button>
        )}
        
        {showDeleteButton && (
          <button 
            className="btn btn-danger" 
            onClick={() => onDelete(band.bandID)}
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

export default BandItem;