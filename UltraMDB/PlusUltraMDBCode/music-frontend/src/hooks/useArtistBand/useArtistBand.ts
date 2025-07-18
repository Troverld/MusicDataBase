import { useArtistBandSearch } from './useArtistBandSearch';
import { useArtistBandConvert } from './useArtistBandConvert';

export const useArtistBand = () => {
  // 获取搜索相关功能
  const {
    loading,
    error,
    searchArtistBand,
    getArtistById,
    getBandById,
    getArtistBandsByIds
  } = useArtistBandSearch();

  // 获取转换相关功能
  const {
    convertIdsToArtistBandItems,
    convertCreatorsToSelectedItems,
    convertIdsToSelectedItems,
    convertArtistBandItemsToIds,
    convertArtistBandItemsToNames,
    convertIdsToNames
  } = useArtistBandConvert({
    getArtistById,
    getBandById,
    searchArtistBand,
    getArtistBandsByIds
  });

  // 返回所有功能
  return {
    loading,
    error,
    searchArtistBand,
    getArtistById,
    getBandById,
    getArtistBandsByIds,
    convertIdsToArtistBandItems,
    convertCreatorsToSelectedItems, // 专门处理 CreatorID_Type[]
    convertIdsToSelectedItems,      // 处理传统字符串ID数组
    convertArtistBandItemsToIds,
    convertArtistBandItemsToNames,
    convertIdsToNames
  };
};