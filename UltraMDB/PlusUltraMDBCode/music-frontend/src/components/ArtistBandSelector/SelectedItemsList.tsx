import React from 'react';
import { ArtistBandItem } from '../../hooks/useArtistBand';
import SelectedItem from './SelectedItem';

interface SelectedItemsListProps {
  selectedItems: ArtistBandItem[];
  searchType: 'artist' | 'band' | 'both';
  memberNamesMap: { [key: string]: string };
  onRemoveItem: (item: ArtistBandItem) => void;
  onClearAll: () => void;
}

const SelectedItemsList: React.FC<SelectedItemsListProps> = ({
  selectedItems,
  searchType,
  memberNamesMap,
  onRemoveItem,
  onClearAll
}) => {
  if (selectedItems.length === 0) return null;

  const getSearchTypeText = () => {
    switch (searchType) {
      case 'artist': return '艺术家';
      case 'band': return '乐队';
      default: return '艺术家/乐队';
    }
  };

  return (
    <div style={{ marginTop: '10px' }}>
      <div style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center',
        marginBottom: '10px'
      }}>
        <strong>已选择的{getSearchTypeText()}:</strong>
        <button
          type="button"
          onClick={onClearAll}
          style={{
            background: 'none',
            border: 'none',
            color: '#dc3545',
            cursor: 'pointer',
            fontSize: '12px',
            textDecoration: 'underline'
          }}
        >
          清空全部
        </button>
      </div>
      
      <div style={{ 
        border: '1px solid #ddd', 
        borderRadius: '4px',
        maxHeight: '300px',
        overflowY: 'auto'
      }}>
        {selectedItems.map((item, index) => (
          <SelectedItem
            key={`${item.type}-${item.id}`}
            item={item}
            index={index}
            totalItems={selectedItems.length}
            memberNamesMap={memberNamesMap}
            onRemove={onRemoveItem}
          />
        ))}
      </div>
    </div>
  );
};

export default SelectedItemsList;