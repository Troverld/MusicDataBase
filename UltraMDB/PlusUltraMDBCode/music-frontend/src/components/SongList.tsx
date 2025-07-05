import React, { useState, useEffect } from 'react';
import { Song, CreatorID_Type } from '../types';
import { useGenres } from '../hooks/useGenres';
import { useArtistBand } from '../hooks/useArtistBand';
import { usePermissions, useSongPermission } from '../hooks/usePermissions';

// 处理新的 creators 结构（CreatorID_Type[]）
const formatCreatorList = (creators: CreatorID_Type[], nameMap: { [key: string]: string }) => {
  if (!creators || creators.length === 0) return '无';
  
  const names = creators.map(creator => nameMap[creator.id] || creator.id).filter(name => name);
  return names.length > 0 ? names.join(', ') : '无';
};

// 处理传统的字符串数组格式的创作者列表
const formatStringCreatorList = (creatorIds: string[], nameMap: { [key: string]: string }) => {
  if (!creatorIds || creatorIds.length === 0) return '无';
  
  const names = creatorIds.map(id => nameMap[id] || id).filter(name => name);
  return names.length > 0 ? names.join(', ') : '无';
};

interface SongListProps {
  songs: Song[];
  onEdit: (song: Song) => void;
  onDelete: (songID: string) => void;
}

interface SongItemProps {
  song: Song;
  onEdit: (song: Song) => void;
  onDelete: (songID: string) => void;
  getGenreNamesByIds: (ids: string[]) => string[];
  nameMap: { [key: string]: string };
}

const SongItem: React.FC<SongItemProps> = ({ 
  song, 
  onEdit, 
  onDelete, 
  getGenreNamesByIds, 
  nameMap 
}) => {
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
  const showDeleteButton = !permissionLoading && isAdmin; // 只有管理员可以删除

  return (
    <div className="song-item">
      <h3>{song.name}</h3>
      
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

      {song.uploadTime && (
        <p style={{ fontSize: '12px', color: '#666', marginTop: '10px' }}>
          上传时间: {formatDate(song.uploadTime)}
        </p>
      )}

      {/* 权限相关的提示信息 */}
      {permissionLoading && (
        <div className="permission-warning">
          <div className="loading-spinner"></div>
          正在检查权限...
        </div>
      )}

      {!permissionLoading && !canEdit && !isAdmin && (
        <div className="permission-denied">
          ⚠️ 您没有编辑此歌曲的权限
        </div>
      )}

      <div className="song-actions">
        {showEditButton && (
          <button 
            className="btn btn-secondary" 
            onClick={() => onEdit(song)}
            disabled={permissionLoading}
          >
            编辑
          </button>
        )}
        {showDeleteButton && (
          <button 
            className="btn btn-danger" 
            onClick={() => onDelete(song.songID)}
            disabled={permissionLoading}
          >
            删除
          </button>
        )}
        {!showEditButton && !showDeleteButton && !permissionLoading && (
          <span style={{ color: '#666', fontSize: '14px' }}>
            仅查看模式
          </span>
        )}
      </div>
    </div>
  );
};

const SongList: React.FC<SongListProps> = ({ songs, onEdit, onDelete }) => {
  const { getGenreNamesByIds } = useGenres();
  const { getArtistBandsByIds } = useArtistBand();
  
  // 存储ID到名称的映射
  const [nameMap, setNameMap] = useState<{ [key: string]: string }>({});

  // 获取所有相关的艺术家和乐队ID
  const getAllCreatorIds = (songs: Song[]) => {
    const allIds = new Set<string>();
    
    songs.forEach(song => {
      // 处理 creators 结构（CreatorID_Type[]）
      if (song.creators) {
        song.creators.forEach(creator => {
          if (creator.id && creator.id.trim()) {
            allIds.add(creator.id);
          }
        });
      }
      
      // 处理其他字段（仍然是字符串数组）
      [...(song.performers || []), 
       ...(song.lyricists || []), ...(song.composers || []), 
       ...(song.arrangers || []), ...(song.instrumentalists || [])]
        .forEach(id => {
          if (id && typeof id === 'string' && id.trim()) {
            allIds.add(id);
          }
        });
    });
    
    return Array.from(allIds);
  };

  // 加载所有创作者名称
  useEffect(() => {
    const loadCreatorNames = async () => {
      if (songs.length === 0) return;
      
      const creatorIds = getAllCreatorIds(songs);
      if (creatorIds.length === 0) return;

      try {
        // 尝试将ID识别为艺术家或乐队
        const creatorItems = await Promise.all(
          creatorIds.map(async (id) => {
            try {
              // 首先尝试作为艺术家ID获取
              const artistResult = await getArtistBandsByIds([{id, type: 'artist'}]);
              if (artistResult.length > 0) {
                return { id, name: artistResult[0].name };
              }
              
              // 如果不是艺术家，尝试作为乐队ID获取
              const bandResult = await getArtistBandsByIds([{id, type: 'band'}]);
              if (bandResult.length > 0) {
                return { id, name: bandResult[0].name };
              }
              
              // 如果都不是，返回ID本身（可能已经是名称）
              return { id, name: id };
            } catch (error) {
              console.warn(`Failed to resolve creator ${id}:`, error);
              return { id, name: id };
            }
          })
        );

        const newNameMap: { [key: string]: string } = {};
        creatorItems.forEach(item => {
          newNameMap[item.id] = item.name;
        });
        
        setNameMap(newNameMap);
      } catch (error) {
        console.error('Failed to load creator names:', error);
      }
    };

    loadCreatorNames();
  }, [songs, getArtistBandsByIds]);

  if (songs.length === 0) {
    return (
      <div className="empty-state">
        <p>未找到歌曲</p>
      </div>
    );
  }

  return (
    <div className="song-list">
      {songs.map((song) => (
        <SongItem
          key={song.songID}
          song={song}
          onEdit={onEdit}
          onDelete={onDelete}
          getGenreNamesByIds={getGenreNamesByIds}
          nameMap={nameMap}
        />
      ))}
    </div>
  );
};

export default SongList;