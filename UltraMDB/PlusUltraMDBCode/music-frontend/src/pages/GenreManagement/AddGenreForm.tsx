import React, { useState } from 'react';
import { genreService } from '../../services/genre.service';

interface AddGenreFormProps {
  loading: boolean;
  onSuccess: (message: string) => void;
  onError: (message: string) => void;
  onLoading: (loading: boolean) => void;
}

const AddGenreForm: React.FC<AddGenreFormProps> = ({
  loading,
  onSuccess,
  onError,
  onLoading
}) => {
  const [formData, setFormData] = useState({
    name: '',
    description: ''
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    onLoading(true);

    try {
      const [genreID, message] = await genreService.createGenre(formData);
      
      if (genreID) {
        onSuccess(`曲风创建成功！曲风ID: ${genreID}`);
        setFormData({ name: '', description: '' });
      } else {
        onError(message || '创建曲风失败');
      }
    } catch (err: any) {
      onError(err.message || '创建曲风失败');
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
      <h2 style={{ marginBottom: '20px', color: '#333' }}>添加新曲风</h2>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>曲风名称*</label>
          <input
            type="text"
            value={formData.name}
            onChange={(e) => setFormData({...formData, name: e.target.value})}
            placeholder="例如：流行、摇滚、爵士"
            required
            disabled={loading}
          />
        </div>
        
        <div className="form-group">
          <label>曲风描述*</label>
          <textarea
            value={formData.description}
            onChange={(e) => setFormData({...formData, description: e.target.value})}
            placeholder="描述这个曲风的特点和风格..."
            required
            disabled={loading}
            rows={4}
          />
        </div>
        
        <button 
          type="submit" 
          className="btn btn-primary"
          disabled={loading || !formData.name.trim() || !formData.description.trim()}
        >
          {loading ? '创建中...' : '创建曲风'}
        </button>
      </form>
    </div>
  );
};

export default AddGenreForm;