import React from 'react';
import { Artist } from '../../types';
import ArtistItem from './ArtistItem';
import ModernEmptyState from '../../components/ModernEmptyState';

interface ArtistListProps {
  artists: Artist[];
  onEdit: (artist: Artist) => void;
  onDelete: (artistID: string) => void;
  searchKeyword: string;
}

const ArtistList: React.FC<ArtistListProps> = ({
  artists,
  onEdit,
  onDelete,
  searchKeyword
}) => {
  if (artists.length === 0) {
    const artistIcon = (
      <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
        <circle cx="12" cy="7" r="4"></circle>
      </svg>
    );

    return (
      <ModernEmptyState
        icon={artistIcon}
        title={searchKeyword.trim() ? "未找到匹配的艺术家" : "暂无艺术家"}
        description={searchKeyword.trim() ? "请尝试其他搜索关键词" : "还没有任何艺术家信息，开始创建第一个艺术家吧"}
      />
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