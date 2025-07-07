import React, { useState } from 'react';
import { genreService } from '../../services/genre.service';
import { Genre } from '../../types';

interface DeleteGenreFormProps {
  genres: Genre[];
  loading: boolean;
  onSuccess: (message: string) => void;
  onError: (message: string) => void;
  onLoading: (loading: boolean) => void;
}

const DeleteGenreForm: React.FC<DeleteGenreFormProps> = ({
  genres,
  loading,
  onSuccess,
  onError,
  onLoading
}) => {
  const [deleteFormData, setDeleteFormData] = useState({
    genreID: ''
  });

  const handleDeleteSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!window.confirm('确定要删除这个曲风吗？此操作不可撤销。')) {
      return;
    }

    onLoading(true);

    try {
      const [success, message] = await genreService.deleteGenre(deleteFormData.genreID);
      
      if (success) {
        onSuccess('曲风删除成功！');
        setDeleteFormData({ genreID: '' });
      } else {
        onError(message || '删除曲风失败');
      }
    } catch (err: any) {
      onError(err.message || '删除曲风失败');
    } finally {
      onLoading(false);
    }
  };

  return (
    <div style={{ 
      background: 'white', 
      padding: '30px', 
      borderRadius: '8px', 
      boxShadow: '0 2px 10px rgba(0, 0, 0, 0.1)' 
    }}>
      <h2 style={{ marginBottom: '10px', color: '#333' }}>删除曲风</h2>
      <p style={{ fontSize: '14px', color: '#666', marginBottom: '20px' }}>
        从下拉框中选择要删除的曲风。注意：删除曲风可能会影响使用该曲风的歌曲。
      </p>
      
      <form onSubmit={handleDeleteSubmit}>
        <div className="form-group">
          <label>选择要删除的曲风*</label>
          {genres.length === 0 ? (
            <div style={{ 
              padding: '12px', 
              border: '1px solid #ddd', 
              borderRadius: '4px',
              backgroundColor: '#f8f9fa',
              color: '#666',
              textAlign: 'center'
            }}>
              暂无可删除的曲风
            </div>
          ) : (
            <select
              value={deleteFormData.genreID}
              onChange={(e) => setDeleteFormData({genreID: e.target.value})}
              required
              disabled={loading}
              className="fixed-select"
            >
              <option value="">请选择要删除的曲风...</option>
              {genres.map((genre) => (
                <option key={genre.genreID} value={genre.genreID}>
                  {genre.name} ({genre.genreID})
                </option>
              ))}
            </select>
          )}
          {deleteFormData.genreID && (
            <div style={{ 
              marginTop: '8px', 
              padding: '8px 12px',
              backgroundColor: '#fff3cd',
              border: '1px solid #ffeaa7',
              borderRadius: '4px'
            }}>
              <small style={{ color: '#856404' }}>
                ⚠️ 即将删除曲风: <strong>
                  {genres.find(g => g.genreID === deleteFormData.genreID)?.name}
                </strong>
              </small>
            </div>
          )}
        </div>
        
        <button 
          type="submit" 
          className="btn btn-danger"
          disabled={loading || !deleteFormData.genreID.trim() || genres.length === 0}
        >
          {loading ? '删除中...' : '删除选中曲风'}
        </button>
      </form>
    </div>
  );
};

export default DeleteGenreForm;