import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { bandService } from '../services/band.service';
import { Band } from '../types';
import { useBandPermission } from '../hooks/usePermissions';
import { useArtistBand, ArtistBandItem } from '../hooks/useArtistBand';

const BandDetail: React.FC = () => {
  const { bandID } = useParams<{ bandID: string }>();
  const navigate = useNavigate();
  const [band, setBand] = useState<Band | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [memberDetails, setMemberDetails] = useState<ArtistBandItem[]>([]);

  // 检查编辑权限
  const { canEdit, loading: permissionLoading } = useBandPermission(bandID || '');
  const { convertIdsToArtistBandItems } = useArtistBand();

  useEffect(() => {
    const fetchBand = async () => {
      if (!bandID) {
        setError('乐队ID无效');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const [bandData, message] = await bandService.getBandById(bandID);
        
        if (bandData) {
          setBand(bandData);
          setError('');
          
          // 获取成员详细信息
          try {
            const memberItems = await convertIdsToArtistBandItems(bandData.members || []);
            setMemberDetails(memberItems);
          } catch (error) {
            console.error('Failed to load member details:', error);
            // 如果转换失败，创建基础项目
            const basicMembers: ArtistBandItem[] = (bandData.members || []).map(memberId => ({
              id: memberId,
              name: memberId,
              bio: '无法获取详细信息',
              type: 'artist'
            }));
            setMemberDetails(basicMembers);
          }
        } else {
          setError(message || '未找到乐队信息');
        }
      } catch (err: any) {
        setError(err.message || '获取乐队信息失败');
      } finally {
        setLoading(false);
      }
    };

    fetchBand();
  }, [bandID, convertIdsToArtistBandItems]);

  const handleEdit = () => {
    navigate('/bands', { state: { editBand: band } });
  };

  if (loading) {
    return (
      <div className="container">
        <div style={{ textAlign: 'center', padding: '60px 20px' }}>
          <div className="loading-spinner"></div>
          正在加载乐队信息...
        </div>
      </div>
    );
  }

  if (error || !band) {
    return (
      <div className="container">
        <div className="back-button">
          <button className="btn btn-secondary" onClick={() => navigate(-1)}>
            ← 返回
          </button>
        </div>
        <div className="empty-state">
          <h3>乐队信息获取失败</h3>
          <p>{error || '未找到乐队信息'}</p>
          <Link to="/bands" className="btn btn-primary" style={{ marginTop: '20px' }}>
            前往乐队管理
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
        <div className="detail-title">{band.name}</div>
        <div className="detail-subtitle">🎸 乐队</div>
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
          
          {canEdit && !permissionLoading && (
            <button 
              className="btn btn-primary"
              onClick={handleEdit}
              title="编辑乐队信息"
            >
              编辑信息
            </button>
          )}
        </div>

        <div>
          <h4 style={{ marginBottom: '15px' }}>乐队简介</h4>
          <div className="detail-bio">
            {band.bio || '暂无简介信息'}
          </div>
        </div>

        {memberDetails.length > 0 && (
          <div className="members-section">
            <h4 style={{ marginBottom: '15px' }}>
              乐队成员 ({memberDetails.length}人)
            </h4>
            <div className="members-grid">
              {memberDetails.map((member, index) => {
                // 检查是否是有效的艺术家ID（不是占位符或错误项目）
                const isValidArtist = !member.id.startsWith('not-found-') && 
                                    !member.id.startsWith('error-') && 
                                    !member.id.startsWith('placeholder-') &&
                                    member.type === 'artist';
                
                return (
                  <div key={index} className="member-card">
                    <div style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      gap: '8px',
                      marginBottom: '8px'
                    }}>
                      <span style={{ fontSize: '16px' }}>🎤</span>
                      {isValidArtist ? (
                        <Link 
                          to={`/artists/${member.id}`}
                          style={{ 
                            textDecoration: 'none',
                            color: '#007bff',
                            fontWeight: 'bold'
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.textDecoration = 'underline';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.textDecoration = 'none';
                          }}
                        >
                          {member.name} →
                        </Link>
                      ) : (
                        <strong style={{ color: '#666' }}>{member.name}</strong>
                      )}
                    </div>
                    
                    <div style={{ 
                      fontSize: '12px', 
                      color: '#666', 
                      marginBottom: '8px'
                    }}>
                      {isValidArtist ? '艺术家' : '乐队成员'}
                    </div>
                    
                    {member.bio && !member.bio.startsWith('警告：') && !member.bio.startsWith('错误：') && (
                      <div style={{ 
                        fontSize: '12px', 
                        color: '#888',
                        lineHeight: '1.3',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical'
                      }}>
                        {member.bio.length > 60 ? `${member.bio.substring(0, 60)}...` : member.bio}
                      </div>
                    )}
                    
                    {(member.bio?.startsWith('警告：') || member.bio?.startsWith('错误：')) && (
                      <div style={{ 
                        fontSize: '11px', 
                        color: '#dc3545',
                        backgroundColor: '#f8d7da',
                        padding: '4px 6px',
                        borderRadius: '3px',
                        marginTop: '5px'
                      }}>
                        数据异常
                      </div>
                    )}
                    
                    {isValidArtist && (
                      <div style={{ marginTop: '8px' }}>
                        <Link 
                          to={`/artists/${member.id}`}
                          className="btn btn-primary"
                          style={{ 
                            textDecoration: 'none',
                            fontSize: '11px',
                            padding: '4px 8px'
                          }}
                        >
                          查看详情
                        </Link>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {band.managers && band.managers.length > 0 && (
          <div style={{ 
            borderTop: '1px solid #eee', 
            paddingTop: '20px', 
            marginTop: '20px' 
          }}>
            <h4 style={{ marginBottom: '15px' }}>管理者</h4>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
              {band.managers.map((manager, index) => (
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
            to="/bands" 
            className="btn btn-secondary"
            style={{ marginRight: '10px' }}
          >
            查看所有乐队
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
        <h4 style={{ marginBottom: '10px', color: '#495057' }}>💡 乐队信息</h4>
        <p style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6', margin: 0 }}>
          这里显示了乐队的详细信息，包括乐队成员列表。如果您是该乐队的管理者，可以点击"编辑信息"按钮来修改乐队的基本信息和成员。
          点击成员名称可以查看对应艺术家的详细信息。
        </p>
      </div>
    </div>
  );
};

export default BandDetail;