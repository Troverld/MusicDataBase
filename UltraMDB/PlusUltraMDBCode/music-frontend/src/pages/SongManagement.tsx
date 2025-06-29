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
    if (!searchKeyword.trim()) return;
    
    try {
      const [songIDs, message] = await musicService.searchSongs(searchKeyword);
      if (songIDs) {
        // In a real app, you would fetch song details by IDs
        // For now, we'll just show the IDs
        setSongs(songIDs.map(id => ({ 
          songID: id, 
          name: `Song ${id}`, 
          releaseTime: Date.now(),
          creators: [],
          performers: [],
          genres: []
        })));
      } else {
        setError(message);
      }
    } catch (err: any) {
      setError(err.message);
    }
  };

  const handleDelete = async (songID: string) => {
    if (!window.confirm('Are you sure you want to delete this song?')) return;
    
    try {
      const [success, message] = await musicService.deleteSong(songID);
      if (success) {
        setSongs(songs.filter(s => s.songID !== songID));
        setSuccess('Song deleted successfully');
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
          setSuccess('Song updated successfully');
          setShowModal(false);
          // Refresh song list
          handleSearch();
        } else {
          setError(message);
        }
      } else {
        const [songID, message] = await musicService.uploadSong(songData);
        if (songID) {
          setSuccess('Song uploaded successfully');
          setShowModal(false);
          // Reset form
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

  return (
    <div>
      <h1>Song Management</h1>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <div className="search-box">
        <input
          type="text"
          placeholder="Search songs..."
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
        />
      </div>
      
      <button 
        className="btn btn-primary" 
        onClick={() => { resetForm(); setShowModal(true); }}
        style={{ marginBottom: '20px' }}
      >
        Upload New Song
      </button>
      
      <SongList songs={songs} onEdit={handleEdit} onDelete={handleDelete} />
      
      {showModal && (
        <div className="modal" onClick={() => setShowModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editingSong ? 'Edit Song' : 'Upload New Song'}</h2>
              <button onClick={() => setShowModal(false)}>×</button>
            </div>
            
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>Song Name*</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  required
                />
              </div>
              
              <div className="form-group">
                <label>Release Date*</label>
                <input
                  type="date"
                  value={formData.releaseTime}
                  onChange={(e) => setFormData({...formData, releaseTime: e.target.value})}
                  required
                />
              </div>
              
              <div className="form-row">
                <div className="form-group">
                  <label>Creators (comma separated)</label>
                  <input
                    type="text"
                    value={formData.creators}
                    onChange={(e) => setFormData({...formData, creators: e.target.value})}
                    placeholder="artist1, artist2"
                  />
                </div>
                
                <div className="form-group">
                  <label>Performers (comma separated)</label>
                  <input
                    type="text"
                    value={formData.performers}
                    onChange={(e) => setFormData({...formData, performers: e.target.value})}
                    placeholder="artist1, artist2"
                  />
                </div>
              </div>
              
              <div className="form-row">
                <div className="form-group">
                  <label>Lyricists (comma separated)</label>
                  <input
                    type="text"
                    value={formData.lyricists}
                    onChange={(e) => setFormData({...formData, lyricists: e.target.value})}
                  />
                </div>
                
                <div className="form-group">
                  <label>Composers (comma separated)</label>
                  <input
                    type="text"
                    value={formData.composers}
                    onChange={(e) => setFormData({...formData, composers: e.target.value})}
                  />
                </div>
              </div>
              
              <div className="form-row">
                <div className="form-group">
                  <label>Arrangers (comma separated)</label>
                  <input
                    type="text"
                    value={formData.arrangers}
                    onChange={(e) => setFormData({...formData, arrangers: e.target.value})}
                  />
                </div>
                
                <div className="form-group">
                  <label>Instrumentalists (comma separated)</label>
                  <input
                    type="text"
                    value={formData.instrumentalists}
                    onChange={(e) => setFormData({...formData, instrumentalists: e.target.value})}
                  />
                </div>
              </div>
              
              <div className="form-group">
                <label>Genres (comma separated)</label>
                <input
                  type="text"
                  value={formData.genres}
                  onChange={(e) => setFormData({...formData, genres: e.target.value})}
                  placeholder="pop, rock, jazz"
                />
              </div>
              
              <button type="submit" className="btn btn-primary">
                {editingSong ? 'Update Song' : 'Upload Song'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default SongManagement;