import React from 'react';
import { Artist, Song } from '../../types';
import SongList from '../../components/SongList';
import './ArtistContent.css';

interface ArtistContentProps {
  artist: Artist | null;
  showSongs: boolean;
  artistSongs: Song[];
  onEditSong: (song: Song) => void;
  onDeleteSong: (songID: string) => Promise<void>;
}

const ArtistContent: React.FC<ArtistContentProps> = ({
  artist,
  showSongs,
  artistSongs,
  onEditSong,
  onDeleteSong
}) => {
  return (
    <div className="artist-main-content">
      {/* 简介卡片 */}
      <div className="content-card">
        <div className="content-card-header">
          <h2 className="content-card-title">艺术家简介</h2>
        </div>
        <div className={artist?.bio ? 'bio-content' : 'bio-content bio-empty'}>
          {artist?.bio || '这位艺术家还没有添加简介...'}
        </div>
      </div>

      {/* 歌曲列表 */}
      {showSongs && (
        <div className="content-card">
          <div className="content-card-header">
            <h2 className="content-card-title">
              作品列表 ({artistSongs.length})
            </h2>
          </div>
          {artistSongs.length > 0 ? (
            <SongList 
              songs={artistSongs} 
              onEdit={onEditSong}
              onDelete={onDeleteSong}
            />
          ) : (
            <div className="empty-state-card">
              <div className="empty-state-icon">🎵</div>
              <div className="empty-state-text">暂无作品</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default ArtistContent;
