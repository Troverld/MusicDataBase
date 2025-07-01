import React, { useState, useEffect } from 'react';
import { musicService } from '../services/music.service';
import { Song } from '../types';
import SongList from '../components/SongList';

const SongManagement: React.FC = () => {
  const [songs, setSongs] = useState<Song[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingSong, setEditingSong] = useState<Song | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  
  const [formData, setFormData] = useState({
    name: '',
    releaseTime: new Date().toISOString().split('T')[0],
    creators: '',
    performers: '',
    lyricists: '',
    composers: '',
    arrangers: '',
    instrumentalists: '',
    genres: ''
  });

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      setSongs([]);
      return;
    }
    
    setLoading(true);
    setError('');
    
    try {
      const [songIDs, message] = await musicService.searchSongs(searchKeyword);
      if (songIDs && songIDs.length > 0) {
        // 获取歌曲的详细信息
        const songDetails = await musicService.getSongsByIds(songIDs);
        setSongs(songDetails);
        
        if (songDetails.length === 0) {
          setError('未找到匹配的歌曲详情');
        }
      } else {
        setSongs([]);
        setError(message || '未找到匹配的歌曲');
      }
    } catch (err: any) {
      setError(err.message || '搜索失败');
      setSongs([]);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (songID: string) => {
    if (!window.confirm('确定要删除这首歌曲吗？')) return;
    
    try {
      const [success, message] = await musicService.deleteSong(songID);
      if (success) {
        setSongs(songs.filter(s => s.songID !== songID));
        setSuccess('歌曲删除成功');
      } else {
        setError(message);
      }
    } catch (err: any) {
      setError(err.message);
    }
  };

  const handleEdit = (song: Song) => {
    setEditingSong(song);
    setFormData({
      name: song.name,
      releaseTime: new Date(song.releaseTime).toISOString().split('T')[0],
      creators: song.creators.join(', '),
      performers: song.performers.join(', '),
      lyricists: song.lyricists?.join(', ') || '',
      composers: song.composers?.join(', ') || '',
      arrangers: song.arrangers?.join(', ') || '',
      instrumentalists: song.instrumentalists?.join(', ') || '',
      genres: song.genres.join(', ')
    });
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    const songData = {
      name: formData.name,
      releaseTime: new Date(formData.releaseTime).getTime(),
      creators: formData.creators.split(',').map(s => s.trim()).filter(s => s),
      performers: formData.performers.split(',').map(s => s.trim()).filter(s => s),
      lyricists: formData.lyricists.split(',').map(s => s.trim()).filter(s => s),
      composers: formData.composers.split(',').map(s => s.trim()).filter(s => s),
      arrangers: formData.arrangers.split(',').map(s => s.trim()).filter(s => s),
      instrumentalists: formData.instrumentalists.split(',').map(s => s.trim()).filter(s => s),
      genres: formData.genres.split(',').map(s => s.trim()).filter(s => s)
    };

    try {
      if (editingSong) {
        const [success, message] = await musicService.updateSong(editingSong.songID, songData);
        if (success) {
          setSuccess('歌曲更新成功');
          setShowModal(false);
          // 刷新歌曲列表
          if (searchKeyword.trim()) {
            handleSearch();
          }
        } else {
          setError(message);
        }
      } else {
        const [songID, message] = await musicService.uploadSong(songData);
        if (songID) {
          setSuccess('歌曲上传成功');
          setShowModal(false);
          // 重置表单
          resetForm();
          // 如果当前有搜索，刷新搜索结果
          if (searchKeyword.trim()) {
            handleSearch();
          }
        } else {
          setError(message);
        }
      }
    } catch (err: any) {
      setError(err.message);
    }
  };

  const resetForm = () => {
    setFormData({
      name: '',
      releaseTime: new Date().toISOString().split('T')[0],
      creators: '',
      performers: '',
      lyricists: '',
      composers: '',
      arrangers: '',
      instrumentalists: '',
      genres: ''
    });
    setEditingSong(null);
  };

  const clearMessages = () => {
    setError('');
    setSuccess('');
  };

  // 当搜索关键词变化时清除消息
  useEffect(() => {
    clearMessages();
  }, [searchKeyword]);

  return (
    <div>
      <h1>歌曲管理</h1>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <div className="search-box">
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <input
            type="text"
            placeholder="搜索歌曲..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            style={{ flex: 1 }}
          />
          <button 
            className="btn btn-primary" 
            onClick={handleSearch}
            disabled={loading}
          >
            {loading ? '搜索中...' : '搜索'}
          </button>
        </div>
      </div>
      
      <button 
        className="btn btn-primary" 
        onClick={() => { resetForm(); setShowModal(true); }}
        style={{ marginBottom: '20px' }}
      >
        上传新歌曲
      </button>
      
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <p>正在加载歌曲信息...</p>
        </div>
      ) : (
        <SongList songs={songs} onEdit={handleEdit} onDelete={handleDelete} />
      )}
      
      {showModal && (
        <div className="modal" onClick={() => setShowModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editingSong ? '编辑歌曲' : '上传新歌曲'}</h2>
              <button onClick={() => setShowModal(false)}>×</button>
            </div>
            
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>歌曲名称*</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  required
                />
              </div>
              
              <div className="form-group">
                <label>发布日期*</label>
                <input
                  type="date"
                  value={formData.releaseTime}
                  onChange={(e) => setFormData({...formData, releaseTime: e.target.value})}
                  required
                />
              </div>
              
              <div className="form-row">
                <div className="form-group">
                  <label>创作者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.creators}
                    onChange={(e) => setFormData({...formData, creators: e.target.value})}
                    placeholder="艺术家1, 艺术家2"
                  />
                </div>
                
                <div className="form-group">
                  <label>演唱者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.performers}
                    onChange={(e) => setFormData({...formData, performers: e.target.value})}
                    placeholder="歌手1, 歌手2"
                  />
                </div>
              </div>
              
              <div className="form-row">
                <div className="form-group">
                  <label>作词者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.lyricists}
                    onChange={(e) => setFormData({...formData, lyricists: e.target.value})}
                  />
                </div>
                
                <div className="form-group">
                  <label>作曲者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.composers}
                    onChange={(e) => setFormData({...formData, composers: e.target.value})}
                  />
                </div>
              </div>
              
              <div className="form-row">
                <div className="form-group">
                  <label>编曲者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.arrangers}
                    onChange={(e) => setFormData({...formData, arrangers: e.target.value})}
                  />
                </div>
                
                <div className="form-group">
                  <label>演奏者 (逗号分隔)</label>
                  <input
                    type="text"
                    value={formData.instrumentalists}
                    onChange={(e) => setFormData({...formData, instrumentalists: e.target.value})}
                  />
                </div>
              </div>
              
              <div className="form-group">
                <label>曲风 (逗号分隔)</label>
                <input
                  type="text"
                  value={formData.genres}
                  onChange={(e) => setFormData({...formData, genres: e.target.value})}
                  placeholder="流行, 摇滚, 爵士"
                />
              </div>
              
              <button type="submit" className="btn btn-primary">
                {editingSong ? '更新歌曲' : '上传歌曲'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default SongManagement;