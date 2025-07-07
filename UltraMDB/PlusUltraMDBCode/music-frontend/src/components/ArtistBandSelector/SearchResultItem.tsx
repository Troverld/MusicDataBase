import React from 'react';
import { ArtistBandItem } from '../../hooks/useArtistBand';

interface SearchResultItemProps {
  item: ArtistBandItem;
  onSelect: (item: ArtistBandItem) => void;
}

const SearchResultItem: React.FC<SearchResultItemProps> = ({ item, onSelect }) => {
  const truncateBio = (bio: string, maxLength: number = 100) => {
    if (bio.length <= maxLength) return bio;
    return bio.substring(0, maxLength) + '...';
  };

  const getTypeIcon = (type: 'artist' | 'band') => {
    return type === 'artist' ? '🎤' : '🎸';
  };

  const getTypeText = (type: 'artist' | 'band') => {
    return type === 'artist' ? '艺术家' : '乐队';
  };

  return (
    <div
      className="multi-select-option"
      onClick={() => onSelect(item)}
      style={{ cursor: 'pointer' }}
    >
      <div style={{ fontSize: '16px', marginRight: '8px' }}>
        {getTypeIcon(item.type)}
      </div>
      <div className="multi-select-option-content">
        <div className="multi-select-option-name">
          {item.name}
          <span style={{ 
            marginLeft: '8px', 
            fontSize: '11px', 
            color: '#666',
            backgroundColor: '#f0f0f0',
            padding: '2px 6px',
            borderRadius: '10px'
          }}>
            {getTypeText(item.type)}
          </span>
        </div>
        <div className="multi-select-option-description">
          {truncateBio(item.bio, 80)}
        </div>
      </div>
    </div>
  );
};

export default SearchResultItem;