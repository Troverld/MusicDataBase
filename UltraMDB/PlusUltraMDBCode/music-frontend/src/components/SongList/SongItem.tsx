import React from 'react';
import { Song } from '../../types';
import { formatCreatorList, formatStringCreatorList } from './utils';
import { usePermissions } from '../../hooks/usePermissions';
import { useSongPermission } from '../../hooks/useSongPermission';
import { getGenreNamesByIds } from '../../utils/genreMapping';
import SongRating from '../SongRating';
import PlayButton from '../PlayButton';

interface SongItemProps {
  song: Song;
  nameMap: { [key: string]: string };
  onEdit?: (song: Song) => void;
  onDelete?: (songID: string, songName: string) => void;
}

const SongItem: React.FC<SongItemProps> = ({ song, nameMap, onEdit, onDelete }) => {
  const { isAdmin } = usePermissions();
  const { canEdit, loading: permissionLoading } = useSongPermission(song.songID);

  const formatDate = (timestamp: number) => {
    return new Date(timestamp).toLocaleDateString('zh-CN');
  };

  const formatGenres = (genreIds: string[]): string[] => {
    if (genreIds.length === 0) return [];
    return getGenreNamesByIds(genreIds);
  };

  const showEditButton = !permissionLoading && (canEdit || isAdmin);
  const showDeleteButton = !permissionLoading && isAdmin;

  // 播放成功回调
  const handlePlayStart = () => {
    console.log(`Started playing: ${song.name}`);
  };

  // 播放错误回调
  const handlePlayError = (error: string) => {
    console.error(`Play error for ${song.name}:`, error);
  };

  // 添加调试信息
  console.log('SongItem - song:', song.name, 'creators:', song.creators);

  return (
    <div className="song-item">
      <div className="song-header">
        <h3>{song.name}</h3>
        <div className="song-actions">
          <PlayButton
            songID={song.songID}
            songName={song.name}
            size="medium"
            onPlayStart={handlePlayStart}
            onPlayError={handlePlayError}
          />
        </div>
      </div>
      
      <div style={{ marginBottom: '10px' }}>
        <p><strong>发布时间:</strong> {formatDate(song.releaseTime)}</p>
        <p><strong>创作者:</strong> {formatCreatorList(song.creators, nameMap)}</p>
        <p><strong>演唱者:</strong> {formatStringCreatorList(song.performers, nameMap)}</p>
      </div>

      {song.lyricists && song.lyricists.length > 0 && (
        <p><strong>作词:</strong> {formatStringCreatorList(song.lyricists, nameMap)}</p>
      )}
      
      {song.composers && song.composers.length > 0 && (
        <p><strong>作曲:</strong> {formatStringCreatorList(song.composers, nameMap)}</p>
      )}
      
      {song.arrangers && song.arrangers.length > 0 && (
        <p><strong>编曲:</strong> {formatStringCreatorList(song.arrangers, nameMap)}</p>
      )}
      
      {song.instrumentalists && song.instrumentalists.length > 0 && (
        <p><strong>演奏:</strong> {formatStringCreatorList(song.instrumentalists, nameMap)}</p>
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

      {/* 歌曲评分组件 */}
      <div style={{ marginTop: '15px' }}>
        <SongRating
          songID={song.songID}
          showUserRating={true}
          showAverageRating={true}
          showPopularity={true}
          compact={false}
        />
      </div>

      {song.uploadTime && (
        <p style={{ fontSize: '12px', color: '#666', marginTop: '10px' }}>
          上传时间: {formatDate(song.uploadTime)}
        </p>
      )}

      {permissionLoading && (
        <div className="permission-warning">
          <div className="loading-spinner"></div>
          正在检查权限...
        </div>
      )}

      {!permissionLoading && !canEdit && !isAdmin && (
        <div className="permission-warning">
          <span>⚠️ 您没有编辑此歌曲的权限</span>
        </div>
      )}

      <div className="song-buttons">
        {showEditButton && (
          <button 
            className="btn btn-outline" 
            onClick={() => onEdit && onEdit(song)}
          >
            编辑歌曲
          </button>
        )}
        
        {showDeleteButton && (
          <button 
            className="btn btn-danger" 
            onClick={() => onDelete && onDelete(song.songID, song.name)}
          >
            删除歌曲
          </button>
        )}
      </div>
    </div>
  );
};

export default SongItem;