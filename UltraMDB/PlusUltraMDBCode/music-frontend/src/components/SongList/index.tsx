import React, { useState, useEffect } from 'react';
import { Song } from '../../types';
import { useGenres } from '../../hooks/useGenres';
import { useArtistBand } from '../../hooks/useArtistBand';
import SongItem from './SongItem';
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
                console.log(`Found ${id} as ${alternativeType}: ${altResult[0].name}`);
              } else {
                console.warn(`No result for typed creator ${typedCreator}`);
                // 尝试从ID中提取显示名称
                const displayName = id.split('_')[1] || id;
                newNameMap[typedCreator] = displayName;
                newNameMap[id] = displayName;
              }
            }
          } catch (error) {
            console.warn(`Failed to resolve typed creator ${typedCreator}:`, error);
            const displayName = id.split('_')[1] || id;
            newNameMap[typedCreator] = displayName;
            newNameMap[id] = displayName;
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