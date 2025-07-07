import React from 'react';
import { Genre } from '../../types';

interface GenreListProps {
  genres: Genre[];
  isAdmin: boolean;
}

const GenreList: React.FC<GenreListProps> = ({ genres, isAdmin }) => {
  return (
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
  );
};

export default GenreList;