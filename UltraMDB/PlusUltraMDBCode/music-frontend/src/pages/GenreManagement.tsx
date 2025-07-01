import React, { useState } from 'react';
import { genreService } from '../services/genre.service';

const GenreManagement: React.FC = () => {
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  
  // Add Genre Form State
  const [addFormData, setAddFormData] = useState({
    name: '',
    description: ''
  });

  // Delete Genre Form State
  const [deleteFormData, setDeleteFormData] = useState({
    genreID: ''
  });

  const clearMessages = () => {
    setError('');
    setSuccess('');
  };

  const handleAddSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    clearMessages();

    try {
      const [genreID, message] = await genreService.createGenre(addFormData);
      
      if (genreID) {
        setSuccess(`曲风创建成功！曲风ID: ${genreID}`);
        setAddFormData({ name: '', description: '' });
      } else {
        setError(message || '创建曲风失败');
      }
    } catch (err: any) {
      setError(err.message || '创建曲风失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!window.confirm('确定要删除这个曲风吗？此操作不可撤销。')) {
      return;
    }

    setLoading(true);
    clearMessages();

    try {
      const [success, message] = await genreService.deleteGenre(deleteFormData.genreID);
      
      if (success) {
        setSuccess('曲风删除成功！');
        setDeleteFormData({ genreID: '' });
      } else {
        setError(message || '删除曲风失败');
      }
    } catch (err: any) {
      setError(err.message || '删除曲风失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h1>曲风管理</h1>
      <p style={{ color: '#666', marginBottom: '30px', fontSize: '16px' }}>
        管理系统中的音乐曲风，添加新曲风或删除现有曲风。
      </p>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '40px', marginTop: '20px' }}>
        {/* Add Genre Section */}
        <div style={{ 
          background: 'white', 
          padding: '30px', 
          borderRadius: '8px', 
          boxShadow: '0 2px 10px rgba(0, 0, 0, 0.1)' 
        }}>
          <h2 style={{ marginBottom: '20px', color: '#333' }}>添加新曲风</h2>
          <form onSubmit={handleAddSubmit}>
            <div className="form-group">
              <label>曲风名称*</label>
              <input
                type="text"
                value={addFormData.name}
                onChange={(e) => setAddFormData({...addFormData, name: e.target.value})}
                placeholder="例如：流行、摇滚、爵士"
                required
                disabled={loading}
              />
            </div>
            
            <div className="form-group">
              <label>曲风描述*</label>
              <textarea
                value={addFormData.description}
                onChange={(e) => setAddFormData({...addFormData, description: e.target.value})}
                placeholder="描述这个曲风的特点和风格..."
                required
                disabled={loading}
                rows={4}
              />
            </div>
            
            <button 
              type="submit" 
              className="btn btn-primary"
              disabled={loading || !addFormData.name.trim() || !addFormData.description.trim()}
            >
              {loading ? '创建中...' : '创建曲风'}
            </button>
          </form>
        </div>

        {/* Delete Genre Section */}
        <div style={{ 
          background: 'white', 
          padding: '30px', 
          borderRadius: '8px', 
          boxShadow: '0 2px 10px rgba(0, 0, 0, 0.1)' 
        }}>
          <h2 style={{ marginBottom: '10px', color: '#333' }}>删除曲风</h2>
          <p style={{ fontSize: '14px', color: '#666', marginBottom: '20px' }}>
            输入要删除的曲风ID。注意：删除曲风可能会影响使用该曲风的歌曲。
          </p>
          
          <form onSubmit={handleDeleteSubmit}>
            <div className="form-group">
              <label>曲风ID*</label>
              <input
                type="text"
                value={deleteFormData.genreID}
                onChange={(e) => setDeleteFormData({genreID: e.target.value})}
                placeholder="例如：genre-001"
                required
                disabled={loading}
              />
              <small style={{ display: 'block', marginTop: '5px', color: '#666' }}>
                曲风ID通常以 "genre-" 开头，如 genre-001, genre-002 等
              </small>
            </div>
            
            <button 
              type="submit" 
              className="btn btn-danger"
              disabled={loading || !deleteFormData.genreID.trim()}
            >
              {loading ? '删除中...' : '删除曲风'}
            </button>
          </form>
        </div>
      </div>

      {/* Common Genre IDs Reference */}
      <div style={{ 
        background: '#f8f9fa', 
        padding: '25px', 
        borderRadius: '8px', 
        marginTop: '40px',
        border: '1px solid #e9ecef'
      }}>
        <h3 style={{ marginBottom: '15px', color: '#495057' }}>📚 常见曲风ID参考</h3>
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', 
          gap: '15px',
          marginBottom: '15px'
        }}>
          <div style={{ padding: '10px', background: 'white', borderRadius: '4px', border: '1px solid #dee2e6' }}>
            <strong>genre-001:</strong> Pop (流行音乐)
          </div>
          <div style={{ padding: '10px', background: 'white', borderRadius: '4px', border: '1px solid #dee2e6' }}>
            <strong>genre-002:</strong> Rock (摇滚音乐)
          </div>
          <div style={{ padding: '10px', background: 'white', borderRadius: '4px', border: '1px solid #dee2e6' }}>
            <strong>genre-003:</strong> Jazz (爵士音乐)
          </div>
        </div>
        <p style={{ fontSize: '12px', color: '#6c757d', margin: '0' }}>
          ⚠️ 这些是系统中预设的曲风ID，删除前请确认没有歌曲在使用这些曲风。
        </p>
      </div>
    </div>
  );
};

export default GenreManagement;