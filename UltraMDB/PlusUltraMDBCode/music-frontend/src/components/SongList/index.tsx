import React, { useState, useEffect } from 'react';
import { Song } from '../../types';
import { useGenres } from '../../hooks/useGenres';
import { useArtistBand } from '../../hooks/useArtistBand';
import ModernSongCard from './ModernSongCard';
import { getAllCreatorInfo } from './utils';

interface SongListProps {
  songs: Song[];
  onEdit: (song: Song) => void;
  onDelete: (songID: string) => void;
}

const SongList: React.FC<SongListProps> = ({ songs, onEdit, onDelete }) => {
  const { getGenreNamesByIds } = useGenres();
  const { getArtistBandsByIds } = useArtistBand();
  
  // 存储ID到名称的映射
  const [nameMap, setNameMap] = useState<{ [key: string]: string }>({});

  // 加载所有创作者名称
  useEffect(() => {
    const loadCreatorNames = async () => {
      if (songs.length === 0) return;
      
      const { typedCreators, untypedIds } = getAllCreatorInfo(songs);
      const newNameMap: { [key: string]: string } = {};

      try {
        // 处理有类型信息的创作者
        for (const typedCreator of typedCreators) {
          const [creatorType, ...idParts] = typedCreator.split('-');
          const id = idParts.join('-'); // 处理ID中可能包含的连字符
          
          if (!id || !creatorType) {
            console.warn('Invalid typed creator format:', typedCreator);
            continue;
          }
          
          try {
            const type = creatorType as 'artist' | 'band';
            
            if (type !== 'artist' && type !== 'band') {
              console.warn(`Invalid creator type: ${creatorType} for ${typedCreator}`);
              continue;
            }
            
            console.log(`Calling getArtistBandsByIds for:`, { id, type });
            const result = await getArtistBandsByIds([{ id, type }]);
            console.log(`Result for ${typedCreator}:`, result);
            
            if (result.length > 0) {
              newNameMap[typedCreator] = result[0].name;
              newNameMap[id] = result[0].name; // 也存储不带类型前缀的版本
              console.log(`Mapped ${typedCreator} -> ${result[0].name}`);
            } else {
              // 如果找不到，尝试另一种类型
              const alternativeType = type === 'artist' ? 'band' : 'artist';
              const altResult = await getArtistBandsByIds([{ id, type: alternativeType }]);
              if (altResult.length > 0) {
                newNameMap[typedCreator] = altResult[0].name;
                newNameMap[id] = altResult[0].name;
                console.log(`Alternative mapping ${typedCreator} -> ${altResult[0].name}`);
              } else {
                console.warn(`No result found for ${typedCreator}`);
                // 使用ID的最后部分作为显示名称
                const displayName = id.split('_')[1] || id;
                newNameMap[typedCreator] = displayName;
                newNameMap[id] = displayName;
              }
            }
          } catch (error) {
            console.error(`Error processing ${typedCreator}:`, error);
            const displayName = id.split('_')[1] || id;
            newNameMap[typedCreator] = displayName;
            newNameMap[id] = displayName;
          }
        }
        
        // 处理未分类的ID
        for (const id of untypedIds) {
          if (newNameMap[id]) continue; // 已经处理过
          
          try {
            // 先尝试作为艺术家
            const artistResult = await getArtistBandsByIds([{ id, type: 'artist' }]);
            if (artistResult.length > 0) {
              newNameMap[id] = artistResult[0].name;
              continue;
            }
            
            // 再尝试作为乐队
            const bandResult = await getArtistBandsByIds([{ id, type: 'band' }]);
            if (bandResult.length > 0) {
              newNameMap[id] = bandResult[0].name;
              continue;
            }
            
            // 如果都不是，尝试从ID中提取显示名称
            console.warn(`No result for untyped creator ${id}`);
            const displayName = id.split('_')[1] || id;
            newNameMap[id] = displayName;
          } catch (error) {
            console.warn(`Failed to resolve untyped creator ${id}:`, error);
            const displayName = id.split('_')[1] || id;
            newNameMap[id] = displayName;
          }
        }
        
        console.log('Final nameMap:', newNameMap);
        setNameMap(newNameMap);
      } catch (error) {
        console.error('Failed to load creator names:', error);
      }
    };

    loadCreatorNames();
  }, [songs, getArtistBandsByIds]);

  if (songs.length === 0) {
    return (
      <div className="modern-empty-state">
        <div className="empty-state-content">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1">
            <circle cx="12" cy="12" r="3"></circle>
            <path d="M12 1v6m0 6v6"></path>
            <path d="m21 12-6 0m-6 0-6 0"></path>
          </svg>
          <h3>暂无歌曲</h3>
          <p>还没有找到任何歌曲，试试搜索或上传新歌曲吧</p>
        </div>
      </div>
    );
  }

  return (
    <div className="modern-song-list">
      {songs.map((song) => (
        <ModernSongCard
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