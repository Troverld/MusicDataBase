import React from 'react';
import { Genre } from '../../types';

interface GenreListProps {
  genres: Genre[];
  isAdmin: boolean;
}

const GenreList: React.FC<GenreListProps> = ({ genres, isAdmin }) => {
  return (
    <div style={{
      maxWidth: '1200px',
      margin: '0 auto'
    }}>
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
            {isAdmin && (
              <p style={{ fontSize: '14px', color: '#999' }}>
                使用上方的"添加新曲风"功能来创建第一个曲风
              </p>
            )}
          </div>
        ) : (
          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))',
            gap: '15px'
          }}>
            {genres.map((genre) => (
              <div 
                key={genre.genreID} 
                style={{ 
                  padding: '15px', 
                  border: '1px solid #e3e3e3',
                  borderRadius: '6px',
                  backgroundColor: '#fafafa',
                  transition: 'all 0.2s ease',
                  cursor: 'default'
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.backgroundColor = '#f0f0f0';
                  e.currentTarget.style.borderColor = '#ccc';
                  e.currentTarget.style.transform = 'translateY(-2px)';
                  e.currentTarget.style.boxShadow = '0 4px 8px rgba(0,0,0,0.1)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = '#fafafa';
                  e.currentTarget.style.borderColor = '#e3e3e3';
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
    </div>
  );
};

export default GenreList;