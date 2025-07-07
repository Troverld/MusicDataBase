import { Song, CreatorID_Type } from '../../types';

// 处理新的 creators 结构（CreatorID_Type[]） - 直接使用类型信息
export const formatCreatorList = (creators: CreatorID_Type[], nameMap: { [key: string]: string }) => {
  if (!creators || creators.length === 0) return '无';
  
  console.log('formatCreatorList - creators:', creators);
  console.log('formatCreatorList - nameMap:', nameMap);
  
  const names = creators.map(creator => {
    console.log('Processing creator:', creator, 'type:', typeof creator.creatorType, 'value:', creator.creatorType);
    
    // 添加数据验证
    if (!creator || !creator.id || !creator.creatorType) {
      console.warn('Invalid creator object:', creator);
      return creator?.id || 'Unknown';
    }
    
    // 直接使用 creatorType，它应该是 'artist' | 'band' 类型
    const key = `${creator.creatorType}-${creator.id}`;
    const result = nameMap[key] || nameMap[creator.id] || creator.id;
    
    console.log(`Creator mapping: ${key} -> ${result}`);
    return result;
  }).filter(name => name);
  
  console.log('formatCreatorList result:', names);
  return names.length > 0 ? names.join(', ') : '无';
};

// 处理传统的字符串数组格式的创作者列表
export const formatStringCreatorList = (creatorIds: string[], nameMap: { [key: string]: string }) => {
  if (!creatorIds || creatorIds.length === 0) return '无';
  
  const names = creatorIds.map(id => nameMap[id] || id).filter(name => name);
  return names.length > 0 ? names.join(', ') : '无';
};

// 获取所有相关的艺术家和乐队ID，区分有类型信息和无类型信息的
export const getAllCreatorInfo = (songs: Song[]) => {
  const typedCreators = new Set<string>(); // 有类型信息的创作者（creators字段）
  const untypedIds = new Set<string>();    // 无类型信息的ID（其他字段）
  
  songs.forEach(song => {
    console.log('Processing song:', song.name, 'creators:', song.creators);
    
    // 处理 creators 结构（CreatorID_Type[]） - 有类型信息
    if (song.creators) {
      song.creators.forEach(creator => {
        console.log('Processing creator in getAllCreatorInfo:', creator);
        if (creator.id && creator.id.trim()) {
          // 添加带类型前缀的key
          const key = `${creator.creatorType}-${creator.id}`;
          console.log('Adding typed creator key:', key);
          typedCreators.add(key);
          
          // 同时添加不带前缀的ID，以便后续查找
          untypedIds.add(creator.id);
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
  
  console.log('getAllCreatorInfo result:', {
    typedCreators: Array.from(typedCreators),
    untypedIds: Array.from(untypedIds)
  });
  
  return {
    typedCreators: Array.from(typedCreators),
    untypedIds: Array.from(untypedIds)
  };
};