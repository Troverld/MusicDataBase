import React from 'react';
import { Song } from '../types';

interface SongListProps {
  songs: Song[];
  onEdit: (song: Song) => void;
  onDelete: (songID: string) => void;
}

const SongList: React.FC<SongListProps> = ({ songs, onEdit, onDelete }) => {
  if (songs.length === 0) {
    return (
      <div className="empty-state">
        <p>No songs found</p>
      </div>
    );
  }

  return (
    <div className="song-list">
      {songs.map((song) => (
        <div key={song.songID} className="song-item">
          <h3>{song.name}</h3>
          <p>Release Time: {new Date(song.releaseTime).toLocaleDateString()}</p>
          <div>
            {song.genres.map((genre) => (
              <span key={genre} className="chip">{genre}</span>
            ))}
          </div>
          <div className="song-actions">
            <button className="btn btn-secondary" onClick={() => onEdit(song)}>
              Edit
            </button>
            <button className="btn btn-danger" onClick={() => onDelete(song.songID)}>
              Delete
            </button>
          </div>
        </div>
      ))}
    </div>
  );
};

export default SongList;