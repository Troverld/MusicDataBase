import React from 'react';
import { Link } from 'react-router-dom';
import { Band } from '../../types';
import { usePermissions, useBandPermission } from '../../hooks/usePermissions';
import './ModernBandCard.css';

// 修改接口以支持成员ID和名称
interface BandMember {
  id: string;
  name: string;
}

interface ModernBandCardProps {
  band: Band;
  memberNames: string[]; // 保留以保持向后兼容
  memberDetails?: BandMember[]; // 新增：包含ID和名称的成员详情
  onEdit: (band: Band) => void;
  onDelete: (bandID: string) => void;
}

const ModernBandCard: React.FC<ModernBandCardProps> = ({ 
  band, 
  memberNames, 
  memberDetails,
  onEdit, 
  onDelete 
}) => {
  const { isAdmin } = usePermissions();
  const { canEdit, loading: permissionLoading } = useBandPermission(band.bandID);

  const showEditButton = !permissionLoading && (canEdit || isAdmin);
  const showDeleteButton = !permissionLoading && isAdmin;

  // 获取乐队名字的首字母作为头像
  const getInitials = (name: string) => {
    const words = name.trim().split(' ');
    if (words.length >= 2) {
      return words[0][0];
    }
    return name.slice(0, 1);
  };

  // 获取权限标签
  const getPermissionBadge = () => {
    if (permissionLoading) return null;
    
    if (isAdmin) {
      return (
        <div className="band-permission-badge admin">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
          </svg>
          管理员
        </div>
      );
    } else if (canEdit) {
      return (
        <div className="band-permission-badge editable">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
          可编辑
        </div>
      );
    } else {
      return (
        <div className="band-permission-badge view-only">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          仅查看
        </div>
      );
    }
  };

  // 决定使用哪种数据：优先使用 memberDetails，否则回退到 memberNames
  const membersToDisplay = memberDetails || memberNames.map(name => ({ id: '', name }));
  const hasDetailedInfo = !!memberDetails;

  return (
    <div className="modern-band-card">
      {permissionLoading && <div className="band-card-loading" />}
      
      {getPermissionBadge()}
      
      <div className="band-card-header">
        <div className="band-avatar">
          <span className="band-avatar-text">{getInitials(band.name)}</span>
        </div>
        
        <div className="band-info">
          <h3 className="band-name">{band.name}</h3>
          <span className="band-id">{band.bandID}</span>
        </div>
      </div>
      
      <div className="band-card-content">
        <div className="band-members-section">
          <div className="band-members-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            成员
            {membersToDisplay.length > 0 && (
              <span className="band-member-count">{membersToDisplay.length}</span>
            )}
          </div>
          <div className="band-members-list">
            {membersToDisplay.length > 0 ? (
              membersToDisplay.slice(0, 5).map((member, index) => {
                // 如果有ID且ID不为空，则渲染为链接
                if (hasDetailedInfo && member.id) {
                  return (
                    <Link
                      key={member.id}
                      to={`/artists/${member.id}`}
                      className="band-member-tag band-member-link"
                      title={`查看 ${member.name} 的详情`}
                    >
                      {member.name}
                    </Link>
                  );
                } else {
                  // 否则渲染为普通标签
                  return (
                    <span key={index} className="band-member-tag">
                      {typeof member === 'string' ? member : member.name}
                    </span>
                  );
                }
              })
            ) : (
              <span className="band-members-empty">暂无成员信息</span>
            )}
            {membersToDisplay.length > 5 && (
              <span className="band-member-tag" style={{ background: 'rgba(107, 114, 128, 0.1)', color: '#6b7280' }}>
                +{membersToDisplay.length - 5} 更多
              </span>
            )}
          </div>
        </div>
        
        <p className={`band-bio ${!band.bio ? 'empty' : ''}`}>
          {band.bio || '暂无简介'}
        </p>
      </div>
      
      <div className="band-card-actions">
        <Link 
          to={`/bands/${band.bandID}`}
          className="band-action-btn primary"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          查看详情
        </Link>
        
        {showEditButton && (
          <button 
            className="band-action-btn secondary" 
            onClick={() => onEdit(band)}
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
            className="band-action-btn danger" 
            onClick={() => onDelete(band.bandID)}
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

export default ModernBandCard;