import React from 'react';
import { Song } from '../types';
import { useGenres } from '../hooks/useGenres';

interface SongListProps {
  songs: Song[];
  onEdit: (song: Song) => void;
  onDelete: (songID: string) => void;
}

const SongList: React.FC<SongListProps> = ({ songs, onEdit, onDelete }) => {
  const { getGenreNamesByIds } = useGenres();

  if (songs.length === 0) {
    return (
      <div className="empty-state">
        <p>未找到歌曲</p>
      </div>
    );
  }

  const formatDate = (timestamp: number) => {
    return new Date(timestamp).toLocaleDateString('zh-CN');
  };

  const formatList = (items: string[]) => {
    return items.length > 0 ? items.join(', ') : '无';
  };

  const formatGenres = (genreIds: string[]): string[] => {
    if (genreIds.length === 0) return [];
    const genreNames = getGenreNamesByIds(genreIds);
    return genreNames;
  };

  return (
    <div className="song-list">
      {songs.map((song) => (
        <div key={song.songID} className="song-item">
          <h3>{song.name}</h3>
          
          <div style={{ marginBottom: '10px' }}>
            <p><strong>发布时间:</strong> {formatDate(song.releaseTime)}</p>
            <p><strong>创作者:</strong> {formatList(song.creators)}</p>
            <p><strong>演唱者:</strong> {formatList(song.performers)}</p>
          </div>

          {song.lyricists && song.lyricists.length > 0 && (
            <p><strong>作词:</strong> {formatList(song.lyricists)}</p>
          )}
          
          {song.composers && song.composers.length > 0 && (
            <p><strong>作曲:</strong> {formatList(song.composers)}</p>
          )}
          
          {song.arrangers && song.arrangers.length > 0 && (
            <p><strong>编曲:</strong> {formatList(song.arrangers)}</p>
          )}
          
          {song.instrumentalists && song.instrumentalists.length > 0 && (
            <p><strong>演奏:</strong> {formatList(song.instrumentalists)}</p>
          )}

          <div style={{ marginTop: '10px' }}>
            <strong>曲风:</strong>
            <div style={{ marginTop: '5px' }}>
              {formatGenres(song.genres).length > 0 ? (
                formatGenres(song.genres).map((genreName: string, index: number) => (
                  <span key={index} className="chip">{genreName}</span>
                ))
              ) : (
                <span className="chip" style={{ backgroundColor: '#f8f9fa', color: '#6c757d' }}>无</span>
              )}
            </div>
          </div>

          {song.uploadTime && (
            <p style={{ fontSize: '12px', color: '#666', marginTop: '10px' }}>
              上传时间: {formatDate(song.uploadTime)}
            </p>
          )}

          <div className="song-actions">
            <button className="btn btn-secondary" onClick={() => onEdit(song)}>
              编辑
            </button>
            <button className="btn btn-danger" onClick={() => onDelete(song.songID)}>
              删除
            </button>
          </div>
        </div>
      ))}
    </div>
  );
};

export default SongList;