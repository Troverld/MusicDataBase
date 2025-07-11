import React, { useRef } from 'react';
import { Song } from '../../types';
import { usePermissions, useSongPermission } from '../../hooks/usePermissions';
import SongRating from '../SongRating';
import PlayButton from '../PlayButton';
import { formatCreatorList, formatStringCreatorList } from './utils';
import './ModernSongCard.css';

interface ModernSongCardProps {
  song: Song;
  onEdit: (song: Song) => void;
  onDelete: (songID: string) => void;
  getGenreNamesByIds: (genreIds: string[]) => string[];
  nameMap: { [key: string]: string };
}

const ModernSongCard: React.FC<ModernSongCardProps> = ({
  song,
  onEdit,
  onDelete,
  getGenreNamesByIds,
  nameMap
}) => {
  const songRatingRef = useRef<any>(null);
  const { isAdmin } = usePermissions();
  const { canEdit, loading: permissionLoading } = useSongPermission(song.songID);

  // 格式化日期
  const formatDate = (timestamp: number) => {
    return new Date(timestamp).toLocaleDateString('zh-CN');
  };

  // 格式化曲风
  const formatGenres = (genreIds: string[]) => {
    if (!genreIds || genreIds.length === 0) return [];
    return getGenreNamesByIds(genreIds);
  };

  // 播放事件处理
  const handlePlayStart = () => {
    console.log(`开始播放歌曲: ${song.name}`);
  };

  const handlePlayError = (error: string) => {
    console.error('播放失败:', error);
  };

  // 权限检查
  const showEditButton = canEdit || isAdmin;
  const showDeleteButton = isAdmin;

  // 收集所有角色信息 - 使用更合适的图标
  const roles = [];

  if (song.creators && song.creators.length > 0) {
    roles.push({
      label: '创作者',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
          <polyline points="10 9 9 9 8 9"/>
        </svg>
      ),
      value: formatCreatorList(song.creators, nameMap)
    });
  }

  if (song.performers && song.performers.length > 0) {
    roles.push({
      label: '演唱者',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/>
          <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
          <line x1="12" y1="19" x2="12" y2="23"/>
          <line x1="8" y1="23" x2="16" y2="23"/>
        </svg>
      ),
      value: formatStringCreatorList(song.performers, nameMap)
    });
  }

  if (song.lyricists && song.lyricists.length > 0) {
    roles.push({
      label: '作词',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"/>
        </svg>
      ),
      value: formatStringCreatorList(song.lyricists, nameMap)
    });
  }

  if (song.composers && song.composers.length > 0) {
    roles.push({
      label: '作曲',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M9 18V5l12-2v13"/>
          <circle cx="6" cy="18" r="3"/>
          <circle cx="18" cy="16" r="3"/>
        </svg>
      ),
      value: formatStringCreatorList(song.composers, nameMap)
    });
  }

  if (song.arrangers && song.arrangers.length > 0) {
    roles.push({
      label: '编曲',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
        </svg>
      ),
      value: formatStringCreatorList(song.arrangers, nameMap)
    });
  }

  if (song.instrumentalists && song.instrumentalists.length > 0) {
    roles.push({
      label: '演奏',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M11.5 21H6.5C5.88 21 5.33 20.76 4.88 20.29L2.29 17.7C1.86 17.27 1.86 16.74 2.29 16.29L14.29 4.29C14.68 3.9 15.31 3.9 15.7 4.29L19.71 8.29C20.1 8.68 20.1 9.31 19.71 9.7L7.7 21.7C7.27 21.89 6.9 22 6.5 22"/>
          <path d="M6 12L10 16"/>
        </svg>
      ),
      value: formatStringCreatorList(song.instrumentalists, nameMap)
    });
  }

  return (
    <div className="modern-song-card">
      <div className="song-card-main">
        {/* 左侧主要信息区 */}
        <div className="song-main-info">
          <div className="song-header">
            <h3 className="song-title">{song.name}</h3>
            <div className="song-meta">
              <span className="release-date">
                {formatDate(song.releaseTime)}
              </span>
              {formatGenres(song.genres).length > 0 && (
                <div className="genre-pills">
                  {formatGenres(song.genres).map((genre, idx) => (
                    <span key={idx} className="genre-pill">{genre}</span>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* 精简的角色信息 */}
          <div className="song-credits">
            {roles.map((role, index) => (
              <div key={index} className="credit-item">
                <span className="credit-icon">{role.icon}</span>
                <span className="credit-text">
                  <span className="credit-label">{role.label}</span>
                  <span className="credit-value">{role.value}</span>
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* 右侧操作区 */}
        <div className="song-actions">
          <PlayButton
            songID={song.songID}
            songName={song.name}
            size="large"
            onPlayStart={handlePlayStart}
            onPlayError={handlePlayError}
          />
          
          {/* 评分信息 - 启用热度显示 */}
          <div className="song-stats">
            <SongRating
              ref={songRatingRef}
              songID={song.songID}
              showUserRating={true}
              showAverageRating={true}
              showPopularity={true}
              compact={true}
            />
          </div>

          {/* 操作按钮 */}
          {(showEditButton || showDeleteButton) && (
            <div className="action-buttons">
              {showEditButton && (
                <button 
                  className="action-btn edit-btn" 
                  onClick={() => onEdit(song)}
                  disabled={permissionLoading}
                  title="编辑"
                >
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                </button>
              )}
              {showDeleteButton && (
                <button 
                  className="action-btn delete-btn" 
                  onClick={() => onDelete(song.songID)}
                  disabled={permissionLoading}
                  title="删除"
                >
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ModernSongCard;