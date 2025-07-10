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

  return (
    <div className="modern-song-card">
      {/* 卡片主体 */}
      <div className="song-card-content">
        {/* 头部区域 */}
        <div className="song-card-header">
          <div className="song-title-section">
            <h3 className="song-title">{song.name}</h3>
            <div className="song-metadata">
              <span className="release-date">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM7 10h5v5H7z"/>
                </svg>
                {formatDate(song.releaseTime)}
              </span>
            </div>
          </div>
          <div className="song-play-section">
            <PlayButton
              songID={song.songID}
              songName={song.name}
              size="medium"
              onPlayStart={handlePlayStart}
              onPlayError={handlePlayError}
            />
          </div>
        </div>

        {/* 创作者信息区域 */}
        <div className="song-creators-section">
          <div className="creator-group">
            <div className="creator-item">
              <span className="creator-label">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C13.1 2 14 2.9 14 4C14 5.1 13.1 6 12 6C10.9 6 10 5.1 10 4C10 2.9 10.9 2 12 2ZM21 9V7L15 7V9C15 10.1 15.9 11 17 11V20C17 20.6 16.6 21 16 21H15C14.4 21 14 20.6 14 20V11H10V20C10 20.6 9.6 21 9 21H8C7.4 21 7 20.6 7 20V11C8.1 11 9 10.1 9 9V7H3V9C3 10.1 3.9 11 5 11V20C5 21.1 5.9 22 7 22H17C18.1 22 19 21.1 19 20V11C20.1 11 21 10.1 21 9Z"/>
                </svg>
                创作者
              </span>
              <span className="creator-value">{formatCreatorList(song.creators, nameMap)}</span>
            </div>
            
            <div className="creator-item">
              <span className="creator-label">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 3V13.55C11.41 13.21 10.73 13 10 13C7.79 13 6 14.79 6 17S7.79 21 10 21 14 19.21 14 17V7H18V3H12Z"/>
                </svg>
                演唱者
              </span>
              <span className="creator-value">{formatStringCreatorList(song.performers, nameMap)}</span>
            </div>
          </div>

          {/* 专业角色信息（可折叠） */}
          {(song.lyricists?.length || song.composers?.length || song.arrangers?.length || song.instrumentalists?.length) && (
            <div className="professional-roles">
              {song.lyricists && song.lyricists.length > 0 && (
                <div className="role-item">
                  <span className="role-label">作词</span>
                  <span className="role-value">{formatStringCreatorList(song.lyricists, nameMap)}</span>
                </div>
              )}
              
              {song.composers && song.composers.length > 0 && (
                <div className="role-item">
                  <span className="role-label">作曲</span>
                  <span className="role-value">{formatStringCreatorList(song.composers, nameMap)}</span>
                </div>
              )}
              
              {song.arrangers && song.arrangers.length > 0 && (
                <div className="role-item">
                  <span className="role-label">编曲</span>
                  <span className="role-value">{formatStringCreatorList(song.arrangers, nameMap)}</span>
                </div>
              )}
              
              {song.instrumentalists && song.instrumentalists.length > 0 && (
                <div className="role-item">
                  <span className="role-label">演奏</span>
                  <span className="role-value">{formatStringCreatorList(song.instrumentalists, nameMap)}</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* 曲风标签区域 */}
        <div className="song-genres-section">
          <div className="genres-container">
            {formatGenres(song.genres).length > 0 ? (
              formatGenres(song.genres).map((genreName: string, index: number) => (
                <span key={index} className="modern-genre-tag">
                  {genreName}
                </span>
              ))
            ) : (
              <span className="modern-genre-tag empty">无曲风标签</span>
            )}
          </div>
        </div>

        {/* 评分区域 */}
        <div className="song-rating-section">
          <SongRating
            ref={songRatingRef}
            songID={song.songID}
            showUserRating={true}
            showAverageRating={true}
            showPopularity={true}
            compact={false}
          />
        </div>

        {/* 上传信息 */}
        {song.uploadTime && (
          <div className="upload-info">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12S6.48 22 12 22 22 17.52 22 12 17.48 2 12 2ZM13 17H11V15H13V17ZM13 13H11V7H13V13Z"/>
            </svg>
            上传于 {formatDate(song.uploadTime)}
          </div>
        )}
      </div>

      {/* 权限和操作区域 */}
      <div className="song-card-footer">
        {permissionLoading && (
          <div className="permission-loading">
            <div className="loading-spinner-small"></div>
            <span>检查权限中...</span>
          </div>
        )}

        {!permissionLoading && !canEdit && !isAdmin && (
          <div className="permission-info">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 1L3 5V11C3 16.55 6.84 21.74 12 23C17.16 21.74 21 16.55 21 11V5L12 1ZM10 17L6 13L7.41 11.59L10 14.17L16.59 7.58L18 9L10 17Z"/>
            </svg>
            仅查看模式
          </div>
        )}

        <div className="action-buttons">
          {showEditButton && (
            <button 
              className="modern-btn modern-btn-secondary" 
              onClick={() => onEdit(song)}
              disabled={permissionLoading}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M3 17.25V21H6.75L17.81 9.94L14.06 6.19L3 17.25ZM20.71 7.04C21.1 6.65 21.1 6.02 20.71 5.63L18.37 3.29C17.98 2.9 17.35 2.9 16.96 3.29L15.13 5.12L18.88 8.87L20.71 7.04Z"/>
              </svg>
              编辑
            </button>
          )}
          {showDeleteButton && (
            <button 
              className="modern-btn modern-btn-danger" 
              onClick={() => onDelete(song.songID)}
              disabled={permissionLoading}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M6 19C6 20.1 6.9 21 8 21H16C17.1 21 18 20.1 18 19V7H6V19ZM19 4H15.5L14.5 3H9.5L8.5 4H5V6H19V4Z"/>
              </svg>
              删除
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default ModernSongCard;