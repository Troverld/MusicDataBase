import React, { useState } from 'react';
import { artistService } from '../../services/artist.service';
import { Artist } from '../../types';

interface ArtistFormProps {
  editingArtist: Artist | null;
  onSuccess: (message: string) => void;
  onError: (message: string) => void;
  onClose: () => void;
}

const ArtistForm: React.FC<ArtistFormProps> = ({
  editingArtist,
  onSuccess,
  onError,
  onClose
}) => {
  const [formData, setFormData] = useState({
    name: editingArtist?.name || '',
    bio: editingArtist?.bio || ''
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.name.trim() || !formData.bio.trim()) {
      onError('艺术家名称和简介都不能为空');
      return;
    }

    try {
      if (editingArtist) {
        const [success, message] = await artistService.updateArtist(editingArtist.artistID, formData);
        if (success) {
          onSuccess('艺术家信息更新成功');
        } else {
          onError(message);
        }
      } else {
        const [artistID, message] = await artistService.createArtist(formData);
        if (artistID) {
          onSuccess(`艺术家创建成功！艺术家ID: ${artistID}`);
        } else {
          onError(message);
        }
      }
    } catch (err: any) {
      onError(err.message);
    }
  };

  return (
    <div className="modal" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>{editingArtist ? '编辑艺术家' : '创建新艺术家'}</h2>
          <button onClick={onClose}>×</button>
        </div>
        
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>艺术家名称*</label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => setFormData({...formData, name: e.target.value})}
              placeholder="请输入艺术家名称"
              required
            />
          </div>
          
          <div className="form-group">
            <label>艺术家简介*</label>
            <textarea
              value={formData.bio}
              onChange={(e) => setFormData({...formData, bio: e.target.value})}
              placeholder="请输入艺术家简介、背景信息等..."
              required
              rows={6}
              style={{ resize: 'vertical', minHeight: '120px' }}
            />
          </div>
          
          <div style={{ 
            display: 'flex', 
            gap: '10px', 
            justifyContent: 'flex-end',
            marginTop: '20px'
          }}>
            <button 
              type="button" 
              className="btn btn-secondary"
              onClick={onClose}
            >
              取消
            </button>
            <button 
              type="submit" 
              className="btn btn-primary"
              disabled={!formData.name.trim() || !formData.bio.trim()}
            >
              {editingArtist ? '更新艺术家' : '创建艺术家'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ArtistForm;