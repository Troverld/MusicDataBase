import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { artistService } from '../services/artist.service';
import { Artist } from '../types';
import { useArtistPermission } from '../hooks/usePermissions';

const ArtistDetail: React.FC = () => {
  const { artistID } = useParams<{ artistID: string }>();
  const navigate = useNavigate();
  const [artist, setArtist] = useState<Artist | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // 检查编辑权限
  const { canEdit, loading: permissionLoading } = useArtistPermission(artistID || '');

  useEffect(() => {
    const fetchArtist = async () => {
      if (!artistID) {
        setError('艺术家ID无效');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const [artistData, message] = await artistService.getArtistById(artistID);
        
        if (artistData) {
          setArtist(artistData);
          setError('');
        } else {
          setError(message || '未找到艺术家信息');
        }
      } catch (err: any) {
        setError(err.message || '获取艺术家信息失败');
      } finally {
        setLoading(false);
      }
    };

    fetchArtist();
  }, [artistID]);

  const handleEdit = () => {
    navigate('/artists', { state: { editArtist: artist } });
  };

  if (loading) {
    return (
      <div className="container">
        <div style={{ textAlign: 'center', padding: '60px 20px' }}>
          <div className="loading-spinner"></div>
          正在加载艺术家信息...
        </div>
      </div>
    );
  }

  if (error || !artist) {
    return (
      <div className="container">
        <div className="back-button">
          <button className="btn btn-secondary" onClick={() => navigate(-1)}>
            ← 返回
          </button>
        </div>
        <div className="empty-state">
          <h3>艺术家信息获取失败</h3>
          <p>{error || '未找到艺术家信息'}</p>
          <Link to="/artists" className="btn btn-primary" style={{ marginTop: '20px' }}>
            前往艺术家管理
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container">
      <div className="back-button">
        <button className="btn btn-secondary" onClick={() => navigate(-1)}>
          ← 返回
        </button>
      </div>

      <div className="detail-header">
        <div className="detail-title">{artist.name}</div>
        <div className="detail-subtitle">🎤 艺术家</div>
      </div>

      <div className="detail-content">
        <div style={{ 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'flex-start',
          marginBottom: '20px'
        }}>
          <div>
            <h3 style={{ marginBottom: '15px' }}>基本信息</h3>
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
          
          {canEdit && !permissionLoading && (
            <button 
              className="btn btn-primary"
              onClick={handleEdit}
              title="编辑艺术家信息"
            >
              编辑信息
            </button>
          )}
        </div>

        <div>
          <h4 style={{ marginBottom: '15px' }}>艺术家简介</h4>
          <div className="detail-bio">
            {artist.bio || '暂无简介信息'}
          </div>
        </div>

        {artist.managers && artist.managers.length > 0 && (
          <div style={{ 
            borderTop: '1px solid #eee', 
            paddingTop: '20px', 
            marginTop: '20px' 
          }}>
            <h4 style={{ marginBottom: '15px' }}>管理者</h4>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
              {artist.managers.map((manager, index) => (
                <span 
                  key={index} 
                  className="chip" 
                  style={{ backgroundColor: '#e3f2fd', color: '#1976d2' }}
                >
                  {manager}
                </span>
              ))}
            </div>
          </div>
        )}

        <div style={{ 
          borderTop: '1px solid #eee', 
          paddingTop: '20px', 
          marginTop: '20px',
          textAlign: 'center'
        }}>
          <Link 
            to="/artists" 
            className="btn btn-secondary"
            style={{ marginRight: '10px' }}
          >
            查看所有艺术家
          </Link>
          <Link 
            to="/songs" 
            className="btn btn-primary"
          >
            查看歌曲
          </Link>
        </div>
      </div>

      {/* 提示信息 */}
      <div style={{ 
        background: '#f8f9fa', 
        padding: '20px', 
        borderRadius: '8px', 
        marginTop: '20px',
        border: '1px solid #e9ecef'
      }}>
        <h4 style={{ marginBottom: '10px', color: '#495057' }}>💡 艺术家信息</h4>
        <p style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6', margin: 0 }}>
          这里显示了艺术家的详细信息。如果您是该艺术家的管理者，可以点击"编辑信息"按钮来修改艺术家的基本信息。
          您也可以通过导航栏访问其他功能页面。
        </p>
      </div>
    </div>
  );
};

export default ArtistDetail;