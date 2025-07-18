import React from 'react';
import { SortedGenreData, UserCharacteristics } from './types';
import './GenreDetailsList.css';

interface GenreDetailsListProps {
  sortedData: SortedGenreData[];
  total: number;
  getColor: (index: number) => string;
  characteristics: UserCharacteristics | null;
}

const GenreDetailsList: React.FC<GenreDetailsListProps> = ({ 
  sortedData, 
  total, 
  getColor,
  characteristics 
}) => {
  return (
    <div className="details-section">
      <h2>完整偏好列表</h2>
      
      <div className="genre-list-new">
        {sortedData.map((item, index) => (
          <div key={item.genreID} className="genre-item-new">
            <div className="genre-rank">#{index + 1}</div>
            <div className="genre-content">
              <div className="genre-header">
                <span className="genre-name">{item.name}</span>
                <span className="genre-value">
                  {((item.value / total) * 100).toFixed(1)}%
                </span>
              </div>
              <div className="genre-bar">
                <div 
                  className="genre-bar-fill"
                  style={{ 
                    width: `${(item.value / sortedData[0].value) * 100}%`,
                    backgroundColor: getColor(index)
                  }}
                />
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* 建议卡片 */}
      {characteristics && (
        <div className="suggestion-card">
          <h3>💡 探索建议</h3>
          <p>
            基于你对 <strong>{characteristics.mainStyle}</strong> 的偏好，
            你可能也会喜欢相关的音乐风格。继续探索不同类型的音乐，
            让你的音乐世界更加丰富多彩！
          </p>
        </div>
      )}
    </div>
  );
};

export default GenreDetailsList;
