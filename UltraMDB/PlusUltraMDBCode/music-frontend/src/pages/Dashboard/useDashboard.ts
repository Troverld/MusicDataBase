import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { getUser } from '../../utils/storage';
import { usePermissions } from '../../hooks/usePermissions';
import { statisticsService } from '../../services/statistics.service';
import { musicService } from '../../services/music.service';
import { genreService } from '../../services/genre.service';
import { useArtistBand } from '../../hooks/useArtistBand';
import { Song, Genre, Profile } from '../../types';

export type SongWithPopularity = Song & { popularity: number };

export const useDashboard = () => {
  const user = getUser();
  const navigate = useNavigate();
  const { isUser, isAdmin, loading: permissionLoading } = usePermissions();
  const { getArtistBandsByIds } = useArtistBand();
  
  const [userProfile, setUserProfile] = useState<Profile | null>(null);
  const [recommendedSongs, setRecommendedSongs] = useState<Song[]>([]);
  const [popularSongs, setPopularSongs] = useState<SongWithPopularity[]>([]);
  const [genres, setGenres] = useState<Genre[]>([]);
  const [creatorNames, setCreatorNames] = useState<{ [key: string]: string }>({});
  const [loading, setLoading] = useState(true);
  const hasFetchedData = useRef(false);

  useEffect(() => {
    if (!permissionLoading && user?.userID && (isUser || isAdmin) && !hasFetchedData.current) {
      hasFetchedData.current = true;
      fetchDashboardData();
    } else if (!permissionLoading && (!user?.userID || (!isUser && !isAdmin))) {
      setLoading(false);
    }
  }, [user?.userID, isUser, isAdmin, permissionLoading]);

  const fetchDashboardData = async () => {
    try {
      // 获取曲风列表
      try {
        const [genreList] = await genreService.getAllGenres();
        if (genreList) {
          setGenres(genreList);
        }
      } catch (error) {
        console.error('Failed to fetch genres:', error);
      }

      // 获取用户画像 - 修复：不传递参数
      try {
        const [portrait, portraitMessage] = await statisticsService.getUserPortrait();
        if (portrait) {
          setUserProfile(portrait);
        }
      } catch (error) {
        console.error('Failed to fetch user portrait:', error);
      }

      // 获取推荐歌曲
      let allSongs: Song[] = [];
      try {
        const [recommendations, recMessage] = await statisticsService.getUserSongRecommendations(1, 6);
        if (recommendations && recommendations.length > 0) {
          const songDetails = await Promise.all(
            recommendations.map(async (songID): Promise<Song | null> => {
              try {
                const [song, message] = await musicService.getSongById(songID);
                return song;
              } catch (error) {
                console.error(`Failed to fetch song ${songID}:`, error);
                return null;
              }
            })
          );
          const validSongs = songDetails.filter((song): song is Song => song !== null);
          setRecommendedSongs(validSongs);
          allSongs = [...allSongs, ...validSongs];
        }
      } catch (error) {
        console.error('Failed to fetch recommendations:', error);
      }

      // 获取热门歌曲
      try {
        const [searchResults, searchMessage] = await musicService.searchSongs('');
        if (searchResults && searchResults.length > 0) {
          const songIds = searchResults.slice(0, 10);
          
          const songsWithPopularity = await Promise.all(
            songIds.map(async (songID): Promise<SongWithPopularity | null> => {
              try {
                const [songResult, popularityResult] = await Promise.all([
                  musicService.getSongById(songID),
                  statisticsService.getSongPopularity(songID)
                ]);
                
                const [song] = songResult;
                const [popularity] = popularityResult;
                
                if (song) {
                  return { ...song, popularity: popularity || 0 };
                }
                return null;
              } catch (error) {
                console.error(`Failed to fetch song ${songID}:`, error);
                return null;
              }
            })
          );
          
          const validSongs = songsWithPopularity.filter((song): song is SongWithPopularity => song !== null);
          const sortedSongs = validSongs
            .sort((a, b) => b.popularity - a.popularity)
            .slice(0, 6);
          
          setPopularSongs(sortedSongs);
          allSongs = [...allSongs, ...sortedSongs];
        }
      } catch (error) {
        console.error('Failed to fetch popular songs:', error);
      }

      // 获取所有歌曲的创作者名称
      await fetchCreatorNames(allSongs);
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchCreatorNames = async (songs: Song[]) => {
    try {
      const creatorSet = new Map<string, { id: string; type: 'artist' | 'band' }>();
      
      songs.forEach(song => {
        if (song.creators) {
          song.creators.forEach(creator => {
            if (creator.id && creator.creatorType) {
              const key = `${creator.creatorType}-${creator.id}`;
              creatorSet.set(key, { id: creator.id, type: creator.creatorType });
            }
          });
        }
      });

      const creators = Array.from(creatorSet.values());
      if (creators.length > 0) {
        const results = await getArtistBandsByIds(creators);
        const nameMap: { [key: string]: string } = {};
        
        results.forEach(result => {
          const key = `${result.type}-${result.id}`;
          nameMap[key] = result.name;
        });
        
        setCreatorNames(nameMap);
      }
    } catch (error) {
      console.error('Failed to fetch creator names:', error);
    }
  };

  const getTopGenres = () => {
    if (!userProfile || !userProfile.vector) return [];
    return userProfile.vector
      .sort((a, b) => b.value - a.value)
      .slice(0, 3)
      .map(dim => {
        const genre = genres.find(g => g.genreID === dim.GenreID);
        return {
          ...dim,
          name: genre ? genre.name : dim.GenreID
        };
      });
  };

  const formatCreators = (song: Song): string => {
    if (!song.creators || song.creators.length === 0) return '未知';
    
    const names = song.creators.map(creator => {
      const key = `${creator.creatorType}-${creator.id}`;
      return creatorNames[key] || creator.id;
    });
    
    return names.join(', ');
  };

  const formatGenres = (genreIds: string[]): string => {
    if (!genreIds || genreIds.length === 0) return '未分类';
    
    const names = genreIds.map(id => {
      const genre = genres.find(g => g.genreID === id);
      return genre ? genre.name : id;
    });
    
    return names.join(' · ');
  };

  return {
    // 状态
    user,
    userProfile,
    recommendedSongs,
    popularSongs,
    genres,
    creatorNames,
    loading: permissionLoading || loading,
    isUser,
    isAdmin,
    
    // 方法
    navigate,
    getTopGenres,
    formatCreators,
    formatGenres
  };
};
