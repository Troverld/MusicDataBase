import React from 'react';
import { ArtistBandItem } from '../../hooks/useArtistBand';

interface SelectedItemProps {
  item: ArtistBandItem;
  index: number;
  totalItems: number;
  memberNamesMap: { [key: string]: string };
  onRemove: (item: ArtistBandItem) => void;
}

const SelectedItem: React.FC<SelectedItemProps> = ({
  item,
  index,
  totalItems,
  memberNamesMap,
  onRemove
}) => {
  const truncateBio = (bio: string, maxLength: number = 150) => {
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
      style={{
        padding: '12px',
        borderBottom: index < totalItems - 1 ? '1px solid #f0f0f0' : 'none',
        backgroundColor: index % 2 === 0 ? '#f9f9f9' : 'white'
      }}
    >
      <div style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'flex-start',
        marginBottom: '5px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '14px' }}>{getTypeIcon(item.type)}</span>
          <strong>{item.name}</strong>
          <span style={{ 
            fontSize: '11px', 
            color: '#666',
            backgroundColor: '#e9ecef',
            padding: '2px 6px',
            borderRadius: '10px'
          }}>
            {getTypeText(item.type)}
          </span>
        </div>
        <button
          type="button"
          onClick={() => onRemove(item)}
          style={{
            background: 'none',
            border: 'none',
            color: '#dc3545',
            cursor: 'pointer',
            fontSize: '16px',
            fontWeight: 'bold',
            width: '24px',
            height: '24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: '50%'
          }}
          title="移除"
        >
          ×
        </button>
      </div>
      
      <div style={{ 
        fontSize: '12px', 
        color: '#666',
        lineHeight: '1.4',
        marginLeft: '22px'
      }}>
        {truncateBio(item.bio)}
      </div>
      
      {item.type === 'band' && item.members && item.members.length > 0 && (
        <div style={{ 
          fontSize: '11px', 
          color: '#999',
          marginLeft: '22px',
          marginTop: '5px'
        }}>
          成员: {item.members.map(memberId => memberNamesMap[memberId] || memberId).join(', ')}
        </div>
      )}
    </div>
  );
};

export default SelectedItem;