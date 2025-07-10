import React, { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getUser } from '../utils/storage';
import { usePermissions } from '../hooks/usePermissions';
import { statisticsService } from '../services/statistics.service';
import { musicService } from '../services/music.service';
import { genreService } from '../services/genre.service';
import { useArtistBand } from '../hooks/useArtistBand';
import { Song, Genre, Profile } from '../types';
import PlayButton from '../components/PlayButton';

type SongWithPopularity = Song & { popularity: number };

const Dashboard: React.FC = () => {
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

  if (permissionLoading || loading) {
    return (
      <div className="dashboard-modern">
        <div className="dashboard-loading">
          <div className="loading-pulse"></div>
          <p>正在准备您的音乐世界...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-modern">
      {/* 极简头部 */}
      <header className="dashboard-header">
        <div className="header-content">
          <div className="greeting">
            <h1>Hi, {user?.account} 👋</h1>
            <p className="subtitle">
              {new Date().getHours() < 12 ? '早上好' : 
               new Date().getHours() < 18 ? '下午好' : '晚上好'}
              ，今天想听什么音乐？
            </p>
          </div>
          
          {/* 快捷操作 */}
          <div className="quick-actions">
            <Link to="/songs" className="quick-action-btn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M9 18V5l12-2v13"/>
                <circle cx="6" cy="18" r="3"/>
                <circle cx="18" cy="16" r="3"/>
              </svg>
              <span>浏览歌曲</span>
            </Link>
            <Link to="/profile" className="quick-action-btn">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <span>我的画像</span>
            </Link>
            {isAdmin && (
              <Link to="/genres" className="quick-action-btn admin">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="3"/>
                  <path d="M12 1v6m0 6v6m6-12h6M6 12H0m16.24-4.24l-4.24 4.24m-8 0L3.76 7.76m12.48 8.48l4.24 4.24m-16.72 0L7.76 16.24"/>
                </svg>
                <span>管理曲风</span>
              </Link>
            )}
          </div>
        </div>
      </header>

      {/* 主内容区 */}
      <main className="dashboard-main">
        {/* 音乐品味卡片 */}
        {isUser && userProfile && userProfile.vector.length > 0 && (
          <section className="taste-section">
            <div className="section-header">
              <h2>你的音乐品味</h2>
              <Link to="/profile" className="view-more">查看完整画像 →</Link>
            </div>
            <div className="taste-cards">
              {getTopGenres().map((genre, index) => (
                <div key={genre.GenreID} className="taste-card">
                  <div className="taste-rank">#{index + 1}</div>
                  <div className="taste-name">{genre.name}</div>
                  <div className="taste-score">
                    <div className="score-bar">
                      <div 
                        className="score-fill" 
                        style={{ 
                          width: `${genre.value * 100}%`,
                          backgroundColor: ['#6366f1', '#8b5cf6', '#ec4899'][index]
                        }}
                      />
                    </div>
                    <span className="score-text">{(genre.value * 100).toFixed(0)}%</span>
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* 个性化推荐 */}
        {recommendedSongs.length > 0 && (
          <section className="recommendations-section">
            <div className="section-header">
              <h2>为你推荐</h2>
              <Link to="/recommendations" className="view-more">更多推荐 →</Link>
            </div>
            <div className="songs-grid">
              {recommendedSongs.map((song) => (
                <article key={song.songID} className="song-card-minimal">
                  <div className="song-card-content">
                    <div className="song-main-info">
                      <h3 className="song-title">{song.name}</h3>
                      <p className="song-artist">{formatCreators(song)}</p>
                      <div className="song-tags">
                        {formatGenres(song.genres).split(' · ').map((genre, idx) => (
                          <span key={idx} className="genre-tag">{genre}</span>
                        ))}
                      </div>
                    </div>
                    <div className="song-play-action">
                      <PlayButton
                        songID={song.songID}
                        songName={song.name}
                        size="medium"
                        onPlayStart={() => console.log(`播放: ${song.name}`)}
                        onPlayError={(error) => console.error(`播放失败: ${error}`)}
                      />
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>
        )}

        {/* 热门歌曲 */}
        {popularSongs.length > 0 && (
          <section className="popular-section">
            <div className="section-header">
              <h2>当前热门</h2>
              <span className="section-subtitle">大家都在听</span>
            </div>
            <div className="popular-list">
              {popularSongs.map((song, index) => (
                <div key={song.songID} className="popular-item">
                  <div className="popular-rank">{index + 1}</div>
                  <div className="popular-main">
                    <div className="popular-info">
                      <h4>{song.name}</h4>
                      <p>{formatCreators(song)}</p>
                    </div>
                    <div className="popular-stats">
                      <span className="popularity-badge">
                        🔥 {song.popularity.toFixed(0)}
                      </span>
                    </div>
                  </div>
                  <PlayButton
                    songID={song.songID}
                    songName={song.name}
                    size="small"
                    onPlayStart={() => console.log(`播放: ${song.name}`)}
                    onPlayError={(error) => console.error(`播放失败: ${error}`)}
                  />
                </div>
              ))}
            </div>
          </section>
        )}

        {/* 未登录提示 */}
        {!isUser && !isAdmin && (
          <div className="empty-state-modern">
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M9 11V6a3 3 0 1 1 6 0v5m-6 8h6a2 2 0 0 0 2-2v-5a2 2 0 0 0-2-2H9a2 2 0 0 0-2 2v5a2 2 0 0 0 2 2z"/>
            </svg>
            <h3>解锁完整体验</h3>
            <p>登录后获得个性化推荐和音乐品味分析</p>
            <button onClick={() => navigate('/login')} className="login-btn-modern">
              立即登录
            </button>
          </div>
        )}
      </main>
    </div>
  );
};

export default Dashboard;