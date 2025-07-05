import React, { useState, useEffect } from 'react';
import { Song, CreatorID_Type } from '../types';
import { useGenres } from '../hooks/useGenres';
import { useArtistBand } from '../hooks/useArtistBand';
import { usePermissions, useSongPermission } from '../hooks/usePermissions';

// 处理新的 creators 结构（CreatorID_Type[]） - 使用类型信息
const formatCreatorList = (creators: CreatorID_Type[], nameMap: { [key: string]: string }) => {
  if (!creators || creators.length === 0) return '无';
  
  const names = creators.map(creator => {
    // 使用组合键：类型-ID
    const key = `${creator.creatorType}-${creator.id}`;
    return nameMap[key] || nameMap[creator.id] || creator.id;
  }).filter(name => name);
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
  const showDeleteButton = !permissionLoading && isAdmin;

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

  // 获取所有相关的艺术家和乐队ID，区分有类型信息和无类型信息的
  const getAllCreatorInfo = (songs: Song[]) => {
    const typedCreators = new Set<string>(); // 有类型信息的创作者（creators字段）
    const untypedIds = new Set<string>();    // 无类型信息的ID（其他字段）
    
    songs.forEach(song => {
      // 处理 creators 结构（CreatorID_Type[]） - 有类型信息
      if (song.creators) {
        song.creators.forEach(creator => {
          if (creator.id && creator.id.trim()) {
            typedCreators.add(`${creator.creatorType}-${creator.id}`);
          }
        });
      }
      
      // 处理其他字段（仍然是字符串数组） - 无类型信息
      [...(song.performers || []), 
       ...(song.lyricists || []), ...(song.composers || []), 
       ...(song.arrangers || []), ...(song.instrumentalists || [])]
        .forEach(id => {
          if (id && typeof id === 'string' && id.trim()) {
            untypedIds.add(id);
          }
        });
    });
    
    return {
      typedCreators: Array.from(typedCreators),
      untypedIds: Array.from(untypedIds)
    };
  };

  // 加载所有创作者名称
  useEffect(() => {
    const loadCreatorNames = async () => {
      if (songs.length === 0) return;
      
      const { typedCreators, untypedIds } = getAllCreatorInfo(songs);
      const newNameMap: { [key: string]: string } = {};

      try {
        // 处理有类型信息的创作者
        for (const typedCreator of typedCreators) {
          const [creatorType, id] = typedCreator.split('-', 2);
          if (!id || !creatorType) continue;
          
          try {
            const type = creatorType.toLowerCase() === 'artist' ? 'artist' : 'band';
            const result = await getArtistBandsByIds([{ id, type }]);
            if (result.length > 0) {
              newNameMap[typedCreator] = result[0].name;
              newNameMap[id] = result[0].name; // 也存储不带类型前缀的版本
            }
          } catch (error) {
            console.warn(`Failed to resolve typed creator ${typedCreator}:`, error);
            newNameMap[typedCreator] = id;
            newNameMap[id] = id;
          }
        }

        // 处理无类型信息的ID（需要猜测类型）
        for (const id of untypedIds) {
          if (newNameMap[id]) continue; // 如果已经从有类型信息的创作者中获取了，跳过
          
          try {
            // 首先尝试作为艺术家ID获取
            const artistResult = await getArtistBandsByIds([{id, type: 'artist'}]);
            if (artistResult.length > 0) {
              newNameMap[id] = artistResult[0].name;
              continue;
            }
            
            // 如果不是艺术家，尝试作为乐队ID获取
            const bandResult = await getArtistBandsByIds([{id, type: 'band'}]);
            if (bandResult.length > 0) {
              newNameMap[id] = bandResult[0].name;
              continue;
            }
            
            // 如果都不是，返回ID本身（可能已经是名称）
            newNameMap[id] = id;
          } catch (error) {
            console.warn(`Failed to resolve untyped creator ${id}:`, error);
            newNameMap[id] = id;
          }
        }
        
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