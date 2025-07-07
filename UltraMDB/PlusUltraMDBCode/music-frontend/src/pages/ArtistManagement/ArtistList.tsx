import React from 'react';
import { Artist } from '../../types';
import ArtistItem from './ArtistItem';

interface ArtistListProps {
  artists: Artist[];
  loading: boolean;
  onEdit: (artist: Artist) => void;
  onDelete: (artistID: string) => void;
  searchKeyword: string;
}

const ArtistList: React.FC<ArtistListProps> = ({
  artists,
  loading,
  onEdit,
  onDelete,
  searchKeyword
}) => {
  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '40px' }}>
        <p>正在加载艺术家信息...</p>
      </div>
    );
  }

  if (artists.length === 0) {
    return (
      <div className="empty-state">
        <p>未找到艺术家</p>
        <p style={{ fontSize: '14px', color: '#999', marginTop: '10px' }}>
          {searchKeyword.trim() ? '请尝试其他搜索关键词' : '请使用搜索功能查找艺术家'}
        </p>
      </div>
    );
  }

  return (
    <div className="song-list">
      {artists.map((artist) => (
        <ArtistItem
          key={artist.artistID}
          artist={artist}
          onEdit={onEdit}
          onDelete={onDelete}
        />
      ))}
    </div>
  );
};

export default ArtistList;