import React, { useState } from 'react';
import { genreService } from '../services/genre.service';
import { useGenres } from '../hooks/useGenres';
import { usePermissions } from '../hooks/usePermissions';

const GenreManagement: React.FC = () => {
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  
  // 权限检查
  const { isAdmin, isUser, loading: permissionLoading } = usePermissions();
  
  // 使用曲风Hook获取最新的曲风列表
  const { genres, fetchGenres } = useGenres();
  
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
    
    if (!isAdmin) {
      setError('您没有创建曲风的权限，仅管理员可以执行此操作');
      return;
    }
    
    setLoading(true);
    clearMessages();

    try {
      const [genreID, message] = await genreService.createGenre(addFormData);
      
      if (genreID) {
        setSuccess(`曲风创建成功！曲风ID: ${genreID}`);
        setAddFormData({ name: '', description: '' });
        // 刷新曲风列表
        await fetchGenres();
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
    
    if (!isAdmin) {
      setError('您没有删除曲风的权限，仅管理员可以执行此操作');
      return;
    }
    
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
        // 刷新曲风列表
        await fetchGenres();
      } else {
        setError(message || '删除曲风失败');
      }
    } catch (err: any) {
      setError(err.message || '删除曲风失败');
    } finally {
      setLoading(false);
    }
  };

  // 如果权限还在加载中
  if (permissionLoading) {
    return (
      <div className="container">
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <div className="loading-spinner"></div>
          正在检查权限...
        </div>
      </div>
    );
  }

  // 如果用户没有基本的查看权限
  if (!isUser && !isAdmin) {
    return (
      <div className="container">
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <h2>访问受限</h2>
          <p>您需要登录并验证用户身份才能查看曲风信息。</p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1>曲风管理</h1>
      <p style={{ color: '#666', marginBottom: '30px', fontSize: '16px' }}>
        {isAdmin 
          ? '管理系统中的音乐曲风，添加新曲风或删除现有曲风。您拥有管理员权限，可以创建和删除曲风。'
          : '查看系统中的音乐曲风分类。您可以浏览所有曲风信息，但需要管理员权限才能进行修改。'
        }
      </p>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      {/* 权限提示 */}
      {!isAdmin && (
        <div className="permission-warning" style={{ marginBottom: '20px' }}>
          ⚠️ 您当前以普通用户身份访问，只能查看曲风信息。如需创建或删除曲风，请联系管理员。
        </div>
      )}
      
      {/* 管理员功能区域 */}
      {isAdmin && (
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: '1fr 1fr', 
          gap: '40px', 
          marginTop: '20px' 
        }}>
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
        </div>
      )}

      {/* Current Genres List - 所有用户都可以查看 */}
      <div style={{ 
        background: 'white', 
        padding: '30px', 
        borderRadius: '8px', 
        marginTop: isAdmin ? '40px' : '20px',
        boxShadow: '0 2px 10px rgba(0, 0, 0, 0.1)'
      }}>
        <h2 style={{ marginBottom: '20px', color: '#333' }}>📋 当前系统曲风列表</h2>
        {genres.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px' }}>
            <p style={{ color: '#666', marginBottom: '10px' }}>
              暂无曲风数据
            </p>
            {isAdmin ? (
              <p style={{ color: '#999', fontSize: '14px' }}>
                请使用上方表单添加新曲风
              </p>
            ) : (
              <p style={{ color: '#999', fontSize: '14px' }}>
                请联系管理员添加曲风
              </p>
            )}
          </div>
        ) : (
          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', 
            gap: '15px'
          }}>
            {genres.map((genre) => (
              <div 
                key={genre.genreID} 
                style={{ 
                  padding: '15px', 
                  background: '#f8f9fa', 
                  borderRadius: '6px', 
                  border: '1px solid #e9ecef',
                  transition: 'transform 0.2s ease, box-shadow 0.2s ease'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = 'translateY(-2px)';
                  e.currentTarget.style.boxShadow = '0 4px 12px rgba(0, 0, 0, 0.15)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.boxShadow = 'none';
                }}
              >
                <div style={{ 
                  display: 'flex', 
                  justifyContent: 'space-between', 
                  alignItems: 'flex-start',
                  marginBottom: '8px'
                }}>
                  <h4 style={{ margin: 0, color: '#495057' }}>{genre.name}</h4>
                  <span style={{ 
                    fontSize: '12px', 
                    color: '#6c757d', 
                    background: '#e9ecef',
                    padding: '2px 6px',
                    borderRadius: '3px',
                    fontFamily: 'monospace'
                  }}>
                    {genre.genreID}
                  </span>
                </div>
                <p style={{ 
                  margin: 0, 
                  fontSize: '14px', 
                  color: '#6c757d',
                  lineHeight: '1.4'
                }}>
                  {genre.description}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Management Tips */}
      <div style={{ 
        background: '#f8f9fa', 
        padding: '25px', 
        borderRadius: '8px', 
        marginTop: '40px',
        border: '1px solid #e9ecef'
      }}>
        <h3 style={{ marginBottom: '15px', color: '#495057' }}>💡 曲风管理提示</h3>
        <div style={{ fontSize: '14px', color: '#6c757d', lineHeight: '1.6' }}>
          <p><strong>权限说明:</strong> {isAdmin ? '您拥有管理员权限，可以创建和删除曲风。' : '您当前是普通用户，只能查看曲风信息。'}</p>
          {isAdmin && (
            <>
              <p><strong>添加曲风：</strong> 系统会自动生成唯一的曲风ID，您只需填写名称和描述即可。</p>
              <p><strong>删除曲风：</strong> 从下拉框中选择要删除的曲风，删除前请确认没有歌曲正在使用该曲风。</p>
            </>
          )}
          <p><strong>曲风使用：</strong> 用户在上传或编辑歌曲时，可以从当前曲风列表中多选曲风进行标记。</p>
          <p><strong>数据一致性：</strong> 所有曲风操作都会立即同步到歌曲管理界面，确保数据一致性。</p>
          {!isAdmin && (
            <p><strong>权限申请:</strong> 如需管理曲风，请联系系统管理员申请相应权限。</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default GenreManagement;