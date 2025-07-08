import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { artistService } from '../services/artist.service';
import { musicService } from '../services/music.service';
import { Artist, Song } from '../types';
import { useArtistPermission, usePermissions } from '../hooks/usePermissions';
import SongList from '../components/SongList';

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

  // 检查编辑权限
  const { canEdit, loading: permissionLoading } = useArtistPermission(artistID || '');
  const { isAdmin } = usePermissions();

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

  // 获取艺术家的歌曲
  const fetchArtistSongs = async () => {
    if (!artistID) return;

    setSongsLoading(true);
    try {
      // 使用 filterSongsByEntity 获取该艺术家的所有歌曲
      const [songIds, message] = await musicService.filterSongsByEntity(
        { id: artistID, type: 'artist' },
        undefined
      );

      if (songIds && songIds.length > 0) {
        // 获取歌曲详情
        const songs = await musicService.getSongsByIds(songIds);
        setArtistSongs(songs);
      } else {
        setArtistSongs([]);
      }
    } catch (error: any) {
      console.error('Failed to fetch artist songs:', error);
      setError('获取艺术家歌曲失败');
    } finally {
      setSongsLoading(false);
    }
  };

  const handleShowSongs = () => {
    if (!showSongs && artistSongs.length === 0) {
      fetchArtistSongs();
    }
    setShowSongs(!showSongs);
  };

  const handleEdit = () => {
    navigate('/artists', { state: { editArtist: artist } });
  };

  const handleEditSong = (song: Song) => {
    // 传递歌曲数据到歌曲管理页面进行编辑
    navigate('/songs', { 
      state: { 
        editSong: song,
        returnTo: 'artist',
        returnId: artistID 
      } 
    });
  };

  const handleDeleteSong = async (songID: string) => {
    // 确认删除操作
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
      
      // 调用删除API
      const [deleteSuccess, message] = await musicService.deleteSong(songID);
      
      if (deleteSuccess) {
        // 从本地状态中移除删除的歌曲
        setArtistSongs(prevSongs => prevSongs.filter(song => song.songID !== songID));
        setSuccess(`歌曲《${songToDelete.name}》删除成功`);
        
        // 3秒后清除成功消息
        setTimeout(() => setSuccess(''), 3000);
      } else {
        setError(message || '删除歌曲失败');
      }
    } catch (err: any) {
      console.error('Delete song error:', err);
      setError(err.message || '删除歌曲时发生错误');
    }
  };

  // 清除消息
  const clearMessages = () => {
    setError('');
    setSuccess('');
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

  if (error && !artist) {
    return (
      <div className="container">
        <div className="back-button">
          <button className="btn btn-secondary" onClick={() => navigate(-1)}>
            ← 返回
          </button>
        </div>
        <div className="empty-state">
          <h3>艺术家信息获取失败</h3>
          <p>{error}</p>
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

      {/* 显示错误和成功消息 */}
      {error && (
        <div className="error-message" style={{ marginBottom: '20px' }}>
          {error}
          <button 
            onClick={clearMessages}
            style={{ 
              marginLeft: '10px', 
              background: 'none', 
              border: 'none', 
              color: 'inherit', 
              cursor: 'pointer',
              fontWeight: 'bold'
            }}
          >
            ×
          </button>
        </div>
      )}
      
      {success && (
        <div className="success-message" style={{ marginBottom: '20px' }}>
          {success}
          <button 
            onClick={clearMessages}
            style={{ 
              marginLeft: '10px', 
              background: 'none', 
              border: 'none', 
              color: 'inherit', 
              cursor: 'pointer',
              fontWeight: 'bold'
            }}
          >
            ×
          </button>
        </div>
      )}

      <div className="detail-header">
        <div className="detail-title">{artist?.name}</div>
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
                {artist?.artistID}
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
            {artist?.bio || '暂无简介信息'}
          </div>
        </div>

        {artist?.managers && artist.managers.length > 0 && (
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
          <button 
            className="btn btn-primary"
            onClick={handleShowSongs}
            disabled={songsLoading}
          >
            {songsLoading ? '加载中...' : (showSongs ? '隐藏歌曲' : '查看该艺术家的歌曲')}
          </button>
        </div>
      </div>

      {/* 艺术家的歌曲列表 */}
      {showSongs && (
        <div style={{ marginTop: '30px' }}>
          <h3 style={{ marginBottom: '20px' }}>
            {artist?.name} 的歌曲 
            {!songsLoading && `(${artistSongs.length} 首)`}
          </h3>
          
          {songsLoading ? (
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <div className="loading-spinner"></div>
              正在加载歌曲...
            </div>
          ) : artistSongs.length > 0 ? (
            <>
              {/* 权限提示 */}
              {!isAdmin && (
                <div className="permission-warning" style={{ marginBottom: '15px' }}>
                  💡 提示：您可以编辑自己上传的歌曲，删除操作需要管理员权限。点击"编辑"将跳转到歌曲管理页面。
                </div>
              )}
              
              <SongList 
                songs={artistSongs} 
                onEdit={handleEditSong} 
                onDelete={handleDeleteSong} 
              />
            </>
          ) : (
            <div className="empty-state">
              <p>该艺术家暂无歌曲</p>
              <Link 
                to="/songs" 
                className="btn btn-primary"
                style={{ marginTop: '15px' }}
              >
                去上传歌曲
              </Link>
            </div>
          )}
        </div>
      )}

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
          点击"查看该艺术家的歌曲"可以查看所有由该艺术家创作或参与的歌曲。您可以编辑自己有权限的歌曲，
          删除操作需要管理员权限。
        </p>
      </div>
    </div>
  );
};

export default ArtistDetail;