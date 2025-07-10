import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { artistService } from '../services/artist.service';
import { musicService } from '../services/music.service';
import { statisticsService } from '../services/statistics.service';
import { Artist, Song, CreatorID_Type } from '../types';
import { useArtistPermission, usePermissions } from '../hooks/usePermissions';
import { useArtistBand, ArtistBandItem } from '../hooks/useArtistBand';
import SongList from '../components/SongList';
import './ArtistDetail.css';

const ArtistDetail: React.FC = () => {
  const { artistID } = useParams<{ artistID: string }>();
  const navigate = useNavigate();
  const [artist, setArtist] = useState<Artist | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [artistSongs, setArtistSongs] = useState<Song[]>([]);
  const [songsLoading, setSongsLoading] = useState(false);
  const [showSongs, setShowSongs] = useState(false);
  const [success, setSuccess] = useState('');
  
  // 相似创作者相关状态
  const [similarCreators, setSimilarCreators] = useState<ArtistBandItem[]>([]);
  const [similarCreatorsLoading, setSimilarCreatorsLoading] = useState(false);

  // 检查编辑权限
  const { canEdit, loading: permissionLoading } = useArtistPermission(artistID || '');
  const { isAdmin } = usePermissions();
  const { getArtistBandsByIds } = useArtistBand();

  // 统计数据
  const [songCount, setSongCount] = useState(0);
  const [bandCount, setBandCount] = useState(0);

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
          
          // 获取歌曲数量
          const [songIds] = await musicService.filterSongsByEntity({id: artistID, type: 'artist'});
          if (songIds) {
            setSongCount(songIds.length);
          }
          
          // 获取所属乐队数量
          const [bandIds] = await artistService.searchAllBelongingBands(artistID);
          if (bandIds) {
            setBandCount(bandIds.length);
          }
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

  // 获取艺术家的歌曲
  const fetchArtistSongs = async () => {
    if (!artistID) return;

    setSongsLoading(true);
    try {
      const [songIds, message] = await musicService.filterSongsByEntity({id: artistID, type: 'artist'});

      if (songIds && songIds.length > 0) {
        const songsData = await musicService.getSongsByIds(songIds);
        setArtistSongs(songsData);
        setShowSongs(true);
      } else {
        setArtistSongs([]);
        setShowSongs(true);
        if (message && message !== 'Success') {
          setError(message);
        }
      }
    } catch (err: any) {
      console.error('Failed to fetch artist songs:', err);
      setError('获取歌曲列表失败');
      setArtistSongs([]);
    } finally {
      setSongsLoading(false);
    }
  };

  // 获取相似创作者
  const fetchSimilarCreators = async () => {
    if (!artistID) return;

    setSimilarCreatorsLoading(true);
    try {
      const [creatorIds, message] = await statisticsService.getSimilarCreators(artistID, 'artist', 6);

      if (creatorIds && creatorIds.length > 0) {
        // 转换 CreatorID_Type[] 为 getArtistBandsByIds 需要的格式
        const creatorRequests = creatorIds.map((creator: CreatorID_Type) => ({
          id: creator.id,
          type: creator.creatorType as 'artist' | 'band'
        }));
        
        const creators = await getArtistBandsByIds(creatorRequests);
        setSimilarCreators(creators.slice(0, 6)); // 限制显示6个
      } else {
        setSimilarCreators([]);
      }
    } catch (err: any) {
      console.error('Failed to fetch similar creators:', err);
      setSimilarCreators([]);
    } finally {
      setSimilarCreatorsLoading(false);
    }
  };

  useEffect(() => {
    if (artist) {
      fetchSimilarCreators();
    }
  }, [artist]);

  const handleEdit = () => {
    if (artist) {
      navigate('/artists', { 
        state: { 
          showModal: true, 
          editArtist: artist 
        } 
      });
    }
  };

  const handleEditSong = (song: Song) => {
    navigate('/songs', { 
      state: { 
        editSong: song,
        returnTo: 'artist',
        returnId: artistID 
      } 
    });
  };

  const handleDeleteSong = async (songID: string) => {
    const songToDelete = artistSongs.find(song => song.songID === songID);
    if (!songToDelete) {
      setError('未找到要删除的歌曲');
      return;
    }

    const confirmMessage = `确定要删除歌曲《${songToDelete.name}》吗？此操作不可撤销。`;
    if (!window.confirm(confirmMessage)) {
      return;
    }

    try {
      setError('');
      setSuccess('');
      
      const [success, message] = await musicService.deleteSong(songID);
      
      if (success) {
        setSuccess(`歌曲《${songToDelete.name}》删除成功`);
        // 从列表中移除已删除的歌曲
        setArtistSongs(prevSongs => prevSongs.filter(song => song.songID !== songID));
        setSongCount(prev => prev - 1);
      } else {
        setError(message || '删除歌曲失败');
      }
    } catch (err: any) {
      setError(err.message || '删除歌曲失败');
    }
  };

  const clearMessages = () => {
    setError('');
    setSuccess('');
  };

  if (loading || permissionLoading) {
    return (
      <div className="artist-detail-page">
        <div className="loading-card">
          <div className="loading-spinner"></div>
          <span>加载中...</span>
        </div>
      </div>
    );
  }

  if (error && !artist) {
    return (
      <div className="artist-detail-page">
        <div className="content-card" style={{ textAlign: 'center', margin: '40px auto', maxWidth: '600px' }}>
          <div className="empty-state-icon">❌</div>
          <h3>{error}</h3>
          <button className="btn btn-primary" onClick={() => navigate('/artists')}>
            返回艺术家列表
          </button>
        </div>
      </div>
    );
  }

  const getArtistInitial = (name: string) => {
    return name ? name.charAt(0).toUpperCase() : 'A';
  };

  return (
    <div className="artist-detail-page">
      {/* 消息提示 */}
      {error && (
        <div className="message-toast">
          <div className="message-content message-error">
            <span>{error}</span>
            <button className="message-close" onClick={clearMessages}>×</button>
          </div>
        </div>
      )}
      
      {success && (
        <div className="message-toast">
          <div className="message-content message-success">
            <span>{success}</span>
            <button className="message-close" onClick={clearMessages}>×</button>
          </div>
        </div>
      )}

      {/* 英雄区域 */}
      <div className="artist-hero">
        <div className="artist-hero-content">
          <div className="artist-header-info">
            <div className="artist-avatar-large">
              {getArtistInitial(artist?.name || '')}
            </div>
            
            <div className="artist-core-info">
              <h1 className="artist-name-large">{artist?.name}</h1>
              <div className="artist-id-badge">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M12 2L2 7v10c0 5.5 3.8 10.7 9 12 5.2-1.3 9-6.5 9-12V7l-10-5z"/>
                </svg>
                ID: {artist?.artistID}
              </div>
              
              <div className="artist-stats">
                <div className="artist-stat">
                  <span className="artist-stat-value">{songCount}</span>
                  <span className="artist-stat-label">首歌曲</span>
                </div>
                <div className="artist-stat">
                  <span className="artist-stat-value">{bandCount}</span>
                  <span className="artist-stat-label">个乐队</span>
                </div>
                <div className="artist-stat">
                  <span className="artist-stat-value">{similarCreators.length}</span>
                  <span className="artist-stat-label">相似艺术家</span>
                </div>
              </div>
            </div>
          </div>
          
          <div className="artist-hero-actions">
            {canEdit && !permissionLoading && (
              <button className="hero-btn hero-btn-primary" onClick={handleEdit}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
                编辑信息
              </button>
            )}
            
            <button 
              className="hero-btn hero-btn-secondary"
              onClick={fetchArtistSongs}
              disabled={songsLoading}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/>
                <polygon points="10 8 16 12 10 16 10 8"/>
              </svg>
              {songsLoading ? '加载中...' : '查看作品'}
            </button>
          </div>
        </div>
      </div>

      {/* 主内容区 */}
      <div className="artist-content">
        <div className="artist-content-grid">
          {/* 主要内容列 */}
          <div className="artist-main-content">
            {/* 简介卡片 */}
            <div className="content-card">
              <div className="content-card-header">
                <h2 className="content-card-title">艺术家简介</h2>
              </div>
              <div className={artist?.bio ? 'bio-content' : 'bio-content bio-empty'}>
                {artist?.bio || '这位艺术家还没有添加简介...'}
              </div>
            </div>

            {/* 歌曲列表 */}
            {showSongs && (
              <div className="content-card">
                <div className="content-card-header">
                  <h2 className="content-card-title">
                    作品列表 ({artistSongs.length})
                  </h2>
                </div>
                {artistSongs.length > 0 ? (
                  <SongList 
                    songs={artistSongs} 
                    onEdit={handleEditSong}
                    onDelete={handleDeleteSong}
                  />
                ) : (
                  <div className="empty-state-card">
                    <div className="empty-state-icon">🎵</div>
                    <div className="empty-state-text">暂无作品</div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* 侧边栏 */}
          <div className="artist-sidebar">
            {/* 快速信息 */}
            <div className="content-card">
              <div className="content-card-header">
                <h3 className="content-card-title" style={{ fontSize: '18px' }}>快速信息</h3>
              </div>
              
              <div className="quick-info-item">
                <div className="quick-info-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                    <circle cx="12" cy="7" r="4"/>
                  </svg>
                </div>
                <div className="quick-info-content">
                  <div className="quick-info-label">身份</div>
                  <div className="quick-info-value">独立艺术家</div>
                </div>
              </div>
              
              <div className="quick-info-item">
                <div className="quick-info-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M9 18V5l12-2v13"/>
                    <circle cx="6" cy="18" r="3"/>
                    <circle cx="18" cy="16" r="3"/>
                  </svg>
                </div>
                <div className="quick-info-content">
                  <div className="quick-info-label">作品数量</div>
                  <div className="quick-info-value">{songCount} 首歌曲</div>
                </div>
              </div>
              
              <div className="quick-info-item">
                <div className="quick-info-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                    <circle cx="9" cy="7" r="4"/>
                    <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                    <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                  </svg>
                </div>
                <div className="quick-info-content">
                  <div className="quick-info-label">所属乐队</div>
                  <div className="quick-info-value">{bandCount} 个</div>
                </div>
              </div>
            </div>

            {/* 相似艺术家 */}
            <div className="content-card">
              <div className="content-card-header">
                <h3 className="content-card-title" style={{ fontSize: '18px' }}>相似艺术家</h3>
                <Link to="/artists" className="content-card-action">
                  查看全部
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="9 18 15 12 9 6"/>
                  </svg>
                </Link>
              </div>
              
              {similarCreatorsLoading ? (
                <div className="loading-card" style={{ padding: '24px' }}>
                  <div className="loading-spinner"></div>
                </div>
              ) : similarCreators.length > 0 ? (
                <div className="similar-creators-grid">
                  {similarCreators.map((creator) => (
                    <Link
                      key={`${creator.type}-${creator.id}`}
                      to={creator.type === 'artist' ? `/artists/${creator.id}` : `/bands/${creator.id}`}
                      className="similar-creator-card"
                    >
                      <div className="similar-creator-avatar">
                        {creator.type === 'artist' ? '🎤' : '🎸'}
                      </div>
                      <div className="similar-creator-info">
                        <div className="similar-creator-name">{creator.name}</div>
                        <div className="similar-creator-type">
                          {creator.type === 'artist' ? '艺术家' : '乐队'}
                        </div>
                      </div>
                    </Link>
                  ))}
                </div>
              ) : (
                <div className="empty-state-card" style={{ padding: '24px' }}>
                  <div className="empty-state-text">暂无相似艺术家</div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ArtistDetail;