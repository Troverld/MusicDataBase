const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = 10011;

// 中间件
app.use(cors());
app.use(express.json());

// ==================== 数据存储 ====================
let users = [
  { userID: 'admin_001', userName: 'admin', password: 'admin123', userToken: null, role: 'admin' },
];

let artists = [
];

let bands = [
];

let genres = [
];

let songs = [

];

// 用户会话存储
let userSessions = new Map();

// 统计数据存储
let songRatings = new Map(); // Map<userID-songID, rating>
let playbackLogs = []; // Array of {userID, songID, timestamp}
let userProfiles = new Map(); // Map<userID, Profile>

// ==================== 辅助函数 ====================
const generateToken = () => `token_${uuidv4().replace(/-/g, '')}`;
const generateID = (prefix) => `${prefix}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

const validateUser = (userID, userToken) => {
  return userSessions.has(userID) && userSessions.get(userID) === userToken;
};

const validateAdmin = (adminID, adminToken) => {
  if (!validateUser(adminID, adminToken)) {
    return false;
  }
  
  const user = users.find(u => u.userID === adminID);
  return user && user.role === 'admin';
};

const getUserRole = (userID) => {
  const user = users.find(u => u.userID === userID);
  return user ? user.role : null;
};

// 计算用户画像
const calculateUserProfile = (userID) => {
  // 基于用户的播放记录和评分计算用户画像
  const userLogs = playbackLogs.filter(log => log.userID === userID);
  const genreCount = new Map();
  
  // 统计每个曲风的播放次数
  userLogs.forEach(log => {
    const song = songs.find(s => s.songID === log.songID);
    if (song && song.genres) {
      song.genres.forEach(genreID => {
        genreCount.set(genreID, (genreCount.get(genreID) || 0) + 1);
      });
    }
  });
  
  // 转换为Profile格式
  const total = Array.from(genreCount.values()).reduce((sum, count) => sum + count, 0);
  const vector = genres.map(genre => ({
    GenreID: genre.genreID,
    value: total > 0 ? (genreCount.get(genre.genreID) || 0) / total : 0
  }));
  
  return {
    vector,
    norm: true
  };
};

// ==================== 通用路由处理器 ====================
app.post('/api/:endpoint', (req, res) => {
  const { endpoint } = req.params;
  const data = req.body;
  
  console.log(`[${new Date().toISOString()}] ${endpoint}:`, JSON.stringify(data, null, 2));
  
  try {
    const result = handleAPICall(endpoint, data);
    res.json(result);
  } catch (error) {
    console.error(`Error handling ${endpoint}:`, error);
    res.status(500).json([null, error.message || 'Internal server error']);
  }
});

app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    data: {
      users: users.length,
      artists: artists.length,
      bands: bands.length,
      songs: songs.length,
      genres: genres.length,
      activeSessions: userSessions.size,
      totalRatings: songRatings.size,
      totalPlaybacks: playbackLogs.length
    }
  });
});

app.options('*', cors());

// ==================== API 处理函数 ====================
function handleAPICall(endpoint, data) {
  switch (endpoint) {
    // OrganizeService APIs
    case 'UserLoginMessage':
      return handleUserLogin(data);
    case 'UserRegisterMessage':
      return handleUserRegister(data);
    case 'UserLogoutMessage':
      return handleUserLogout(data);
    case 'validateUserMapping':
      return handleValidateUserMapping(data);
    case 'validateAdminMapping':
      return handleValidateAdminMapping(data);
      
    // CreatorService APIs
    case 'CreateArtistMessage':
      return handleCreateArtist(data);
    case 'UpdateArtistMessage':
      return handleUpdateArtist(data);
    case 'DeleteArtistMessage':
      return handleDeleteArtist(data);
    case 'GetArtistByID':
      return handleGetArtistByID(data);
    case 'SearchArtistByName':
      return handleSearchArtistByName(data);
    case 'CreateBandMessage':
      return handleCreateBand(data);
    case 'UpdateBandMessage':
      return handleUpdateBand(data);
    case 'DeleteBandMessage':
      return handleDeleteBand(data);
    case 'GetBandByID':
      return handleGetBandByID(data);
    case 'SearchBandByName':
      return handleSearchBandByName(data);
    case 'AddArtistManager':
      return handleAddArtistManager(data);
    case 'AddBandManager':
      return handleAddBandManager(data);
    case 'validArtistOwnership':
      return handleValidArtistOwnership(data);
    case 'validBandOwnership':
      return handleValidBandOwnership(data);
    case 'GetAllCreators':
      return handleGetAllCreators(data);
    case 'SearchAllBelongingBands':
      return handleSearchAllBelongingBands(data);
      
    // MusicService APIs
    case 'UploadNewSong':
      return handleUploadNewSong(data);
    case 'UpdateSongMetadata':
      return handleUpdateSongMetadata(data);
    case 'DeleteSong':
      return handleDeleteSong(data);
    case 'SearchSongsByName':
      return handleSearchSongsByName(data);
    case 'GetSongByID':
      return handleGetSongByID(data);
    case 'CreateNewGenre':
      return handleCreateNewGenre(data);
    case 'DeleteGenre':
      return handleDeleteGenre(data);
    case 'GetGenreList':
      return handleGetGenreList(data);
    case 'FilterSongsByEntity':
      return handleFilterSongsByEntity(data);
    case 'ValidateSongOwnership':
      return handleValidateSongOwnership(data);
    case 'GetSongList':
      return handleGetSongList(data);
    case 'GetSongProfile':
      return handleGetSongProfile(data);
    case 'GetMultSongsProfiles':
      return handleGetMultSongsProfiles(data);
    case 'SearchSongsByNamePaged':
      return handleSearchSongsByNamePaged(data);
      
    // StatisticsService APIs
    case 'LogPlayback':
      return handleLogPlayback(data);
    case 'RateSong':
      return handleRateSong(data);
    case 'UnrateSong':
      return handleUnrateSong(data);
    case 'GetSongRate':
      return handleGetSongRate(data);
    case 'GetAverageRating':
      return handleGetAverageRating(data);
    case 'GetUserPortrait':
      return handleGetUserPortrait(data);
    case 'GetUserSongRecommendations':
      return handleGetUserSongRecommendations(data);
    case 'GetNextSongRecommendation':
      return handleGetNextSongRecommendation(data);
    case 'GetSongPopularity':
      return handleGetSongPopularity(data);
    case 'GetSimilarSongs':
      return handleGetSimilarSongs(data);
    case 'GetSimilarCreators':
      return handleGetSimilarCreators(data);
    case 'GetCreatorCreationTendency':
      return handleGetCreatorCreationTendency(data);
    case 'GetCreatorGenreStrength':
      return handleGetCreatorGenreStrength(data);
    case 'PurgeSongStatistics':
      return handlePurgeSongStatistics(data);
      
    default:
      throw new Error(`Unknown endpoint: ${endpoint}`);
  }
}

// ==================== OrganizeService 处理函数 ====================
function handleUserLogin(data) {
  const { userName, password } = data;
  const user = users.find(u => u.userName === userName && u.password === password);
  
  if (user) {
    const token = generateToken();
    user.userToken = token;
    userSessions.set(user.userID, token);
    return [[user.userID, token], '登录成功'];
  } else {
    return [null, '用户名或密码错误'];
  }
}

function handleUserRegister(data) {
  const { userName, password } = data;
  
  if (users.find(u => u.userName === userName)) {
    return [null, '用户名已存在'];
  }
  
  const userID = generateID('user');
  const newUser = { userID, userName, password, userToken: null, role: 'user' };
  users.push(newUser);
  
  return [userID, '注册成功'];
}

function handleUserLogout(data) {
  const { userID, userToken } = data;
  
  if (validateUser(userID, userToken)) {
    userSessions.delete(userID);
    const user = users.find(u => u.userID === userID);
    if (user) user.userToken = null;
    return [true, '登出成功'];
  }
  
  return [false, '无效的用户令牌'];
}

function handleValidateUserMapping(data) {
  const { userID, userToken } = data;
  return [validateUser(userID, userToken), validateUser(userID, userToken) ? '用户验证成功' : '用户验证失败'];
}

function handleValidateAdminMapping(data) {
  const { adminID, adminToken } = data;
  return [validateAdmin(adminID, adminToken), validateAdmin(adminID, adminToken) ? '管理员验证成功' : '管理员验证失败'];
}

// ==================== CreatorService 处理函数 ====================
function handleCreateArtist(data) {
  const { adminID, adminToken, name, bio } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [null, '管理员验证失败'];
  }
  
  if (artists.find(a => a.name === name)) {
    return [null, '艺术家名称已存在'];
  }
  
  const artistID = generateID('artist');
  const newArtist = { artistID, name, bio, managers: [] };
  artists.push(newArtist);
  
  return [artistID, '艺术家创建成功'];
}

function handleUpdateArtist(data) {
  const { userID, userToken, artistID, name, bio } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const artist = artists.find(a => a.artistID === artistID);
  if (!artist) {
    return [false, '艺术家不存在'];
  }
  
  // 检查权限
  const user = users.find(u => u.userID === userID);
  if (user.role !== 'admin' && !artist.managers.includes(userID)) {
    return [false, '没有权限编辑该艺术家'];
  }
  
  if (name !== undefined) artist.name = name;
  if (bio !== undefined) artist.bio = bio;
  
  return [true, '艺术家信息更新成功'];
}

function handleDeleteArtist(data) {
  const { adminID, adminToken, artistID } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [false, '管理员验证失败'];
  }
  
  const index = artists.findIndex(a => a.artistID === artistID);
  if (index === -1) {
    return [false, '艺术家不存在'];
  }
  
  artists.splice(index, 1);
  return [true, '艺术家删除成功'];
}

function handleGetArtistByID(data) {
  const { userID, userToken, artistID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const artist = artists.find(a => a.artistID === artistID);
  if (!artist) {
    return [null, '艺术家不存在'];
  }
  
  return [artist, '获取艺术家信息成功'];
}

function handleSearchArtistByName(data) {
  const { userID, userToken, name } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const matchedArtists = artists.filter(a => 
    a.name.toLowerCase().includes(name.toLowerCase())
  );
  
  const artistIDs = matchedArtists.map(a => a.artistID);
  return [artistIDs, `找到${artistIDs.length}个匹配的艺术家`];
}

function handleGetAllCreators(data) {
  const { userID, userToken } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const allCreators = [
    ...artists.map(a => ({ creatorType: 'artist', id: a.artistID })),
    ...bands.map(b => ({ creatorType: 'band', id: b.bandID }))
  ];
  
  return [allCreators, '获取所有创作者成功'];
}

function handleSearchAllBelongingBands(data) {
  const { userID, userToken, artistID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const belongingBands = bands
    .filter(b => b.members.includes(artistID))
    .map(b => b.bandID);
  
  return [belongingBands, belongingBands.length > 0 ? '查询成功' : '该艺术家不属于任何乐队'];
}

function handleCreateBand(data) {
  const { adminID, adminToken, name, members, bio } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [null, '管理员验证失败'];
  }
  
  if (bands.find(b => b.name === name)) {
    return [null, '乐队名称已存在'];
  }
  
  const bandID = generateID('band');
  const newBand = { bandID, name, members: members || [], bio, managers: [] };
  bands.push(newBand);
  
  return [bandID, '乐队创建成功'];
}

function handleUpdateBand(data) {
  const { userID, userToken, bandID, name, members, bio } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const band = bands.find(b => b.bandID === bandID);
  if (!band) {
    return [false, '乐队不存在'];
  }
  
  // 检查权限
  const user = users.find(u => u.userID === userID);
  if (user.role !== 'admin' && !band.managers.includes(userID)) {
    return [false, '没有权限编辑该乐队'];
  }
  
  if (name !== undefined) band.name = name;
  if (members !== undefined) band.members = members;
  if (bio !== undefined) band.bio = bio;
  
  return [true, '乐队信息更新成功'];
}

function handleDeleteBand(data) {
  const { adminID, adminToken, bandID } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [false, '管理员验证失败'];
  }
  
  const index = bands.findIndex(b => b.bandID === bandID);
  if (index === -1) {
    return [false, '乐队不存在'];
  }
  
  bands.splice(index, 1);
  return [true, '乐队删除成功'];
}

function handleGetBandByID(data) {
  const { userID, userToken, bandID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const band = bands.find(b => b.bandID === bandID);
  if (!band) {
    return [null, '乐队不存在'];
  }
  
  return [band, '获取乐队信息成功'];
}

function handleSearchBandByName(data) {
  const { userID, userToken, BandName } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const matchedBands = bands.filter(b => 
    b.name.toLowerCase().includes(BandName.toLowerCase())
  );
  
  const bandIDs = matchedBands.map(b => b.bandID);
  return [bandIDs, `找到${bandIDs.length}个匹配的乐队`];
}

function handleAddArtistManager(data) {
  const { adminID, adminToken, userID, artistID } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [false, '管理员验证失败'];
  }
  
  const artist = artists.find(a => a.artistID === artistID);
  if (!artist) {
    return [false, '艺术家不存在'];
  }
  
  if (!artist.managers.includes(userID)) {
    artist.managers.push(userID);
  }
  
  return [true, '管理者添加成功'];
}

function handleAddBandManager(data) {
  const { adminID, adminToken, userID, bandID } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [false, '管理员验证失败'];
  }
  
  const band = bands.find(b => b.bandID === bandID);
  if (!band) {
    return [false, '乐队不存在'];
  }
  
  if (!band.managers.includes(userID)) {
    band.managers.push(userID);
  }
  
  return [true, '管理者添加成功'];
}

function handleValidArtistOwnership(data) {
  const { userID, userToken, artistID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const user = users.find(u => u.userID === userID);
  if (!user) {
    return [false, '用户不存在'];
  }
  
  if (user.role === 'admin') {
    return [true, '管理员拥有艺术家管理权限'];
  }
  
  const artist = artists.find(a => a.artistID === artistID);
  if (!artist) {
    return [false, '艺术家不存在'];
  }
  
  if (artist.managers && artist.managers.includes(userID)) {
    return [true, '拥有艺术家管理权限'];
  }
  
  return [false, '没有艺术家管理权限'];
}

function handleValidBandOwnership(data) {
  const { userID, userToken, bandID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const user = users.find(u => u.userID === userID);
  if (!user) {
    return [false, '用户不存在'];
  }
  
  if (user.role === 'admin') {
    return [true, '管理员拥有乐队管理权限'];
  }
  
  const band = bands.find(b => b.bandID === bandID);
  if (!band) {
    return [false, '乐队不存在'];
  }
  
  if (band.managers && band.managers.includes(userID)) {
    return [true, '拥有乐队管理权限'];
  }
  
  return [false, '没有乐队管理权限'];
}

// ==================== MusicService 处理函数 ====================
function handleUploadNewSong(data) {
  const { userID, userToken, name, releaseTime, creators, performers, lyricists, composers, arrangers, instrumentalists, genres } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const songID = generateID('song');
  const newSong = {
    songID,
    name,
    releaseTime: typeof releaseTime === 'number' ? releaseTime : new Date(releaseTime).getTime(),
    creators: creators || [],
    performers: performers || [],
    lyricists: lyricists || [],
    composers: composers || [],
    arrangers: arrangers || [],
    instrumentalists: instrumentalists || [],
    genres: genres || [],
    uploaderID: userID
  };
  
  songs.push(newSong);
  return [songID, '歌曲上传成功'];
}

function handleUpdateSongMetadata(data) {
  const { userID, userToken, songID, name, releaseTime, creators, performers, lyricists, composers, arrangers, instrumentalists, genres } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [false, '歌曲不存在'];
  }
  
  // 检查权限
  const user = users.find(u => u.userID === userID);
  let hasPermission = false;
  
  if (user.role === 'admin') {
    hasPermission = true;
  } else if (song.uploaderID === userID) {
    hasPermission = true;
  } else {
    // 检查用户是否管理相关的创作者
    const allCreators = song.creators || [];
    for (const creator of allCreators) {
      if (creator.creatorType === 'artist') {
        const artist = artists.find(a => a.artistID === creator.id);
        if (artist && artist.managers && artist.managers.includes(userID)) {
          hasPermission = true;
          break;
        }
      } else if (creator.creatorType === 'band') {
        const band = bands.find(b => b.bandID === creator.id);
        if (band && band.managers && band.managers.includes(userID)) {
          hasPermission = true;
          break;
        }
      }
    }
  }
  
  if (!hasPermission) {
    return [false, '没有编辑该歌曲的权限'];
  }
  
  // 更新歌曲信息
  if (name !== undefined) song.name = name;
  if (releaseTime !== undefined) {
    song.releaseTime = typeof releaseTime === 'number' ? releaseTime : new Date(releaseTime).getTime();
  }
  if (creators !== undefined) song.creators = creators;
  if (performers !== undefined) song.performers = performers;
  if (lyricists !== undefined) song.lyricists = lyricists;
  if (composers !== undefined) song.composers = composers;
  if (arrangers !== undefined) song.arrangers = arrangers;
  if (instrumentalists !== undefined) song.instrumentalists = instrumentalists;
  if (genres !== undefined) song.genres = genres;
  
  return [true, '歌曲信息更新成功'];
}

function handleSearchSongsByNamePaged(data) {
  const { userID, userToken, keywords, pageNumber, pageSize } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  // 按关键词过滤歌曲
  const matchedSongs = songs.filter(s => 
    s.name.toLowerCase().includes(keywords.toLowerCase())
  );
  
  // 计算总页数
  const totalPages = Math.ceil(matchedSongs.length / pageSize);
  
  if (totalPages === 0) {
    return [{ songIds: [], totalPages: 0 }, '未找到匹配的歌曲'];
  }
  
  // 验证页码
  if (pageNumber < 1 || pageNumber > totalPages) {
    return [{ songIds: [], totalPages }, `页码超出范围，总共${totalPages}页`];
  }
  
  // 计算分页
  const startIndex = (pageNumber - 1) * pageSize;
  const endIndex = startIndex + pageSize;
  const pagedSongs = matchedSongs.slice(startIndex, endIndex);
  
  const songIDs = pagedSongs.map(s => s.songID);
  
  return [
    { songIds: songIDs, totalPages }, 
    `第${pageNumber}页，共${totalPages}页，找到${songIDs.length}首歌曲`
  ];
}

function handleDeleteSong(data) {
  const { adminID, adminToken, songID } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [false, '管理员验证失败'];
  }
  
  const index = songs.findIndex(s => s.songID === songID);
  if (index === -1) {
    return [false, '歌曲不存在'];
  }
  
  songs.splice(index, 1);
  
  // 同时删除相关的统计数据
  handlePurgeSongStatistics({ adminID, adminToken, songID });
  
  return [true, '歌曲删除成功'];
}

function handleSearchSongsByName(data) {
  const { userID, userToken, keywords } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const matchedSongs = songs.filter(s => 
    s.name.toLowerCase().includes(keywords.toLowerCase())
  );
  
  const songIDs = matchedSongs.map(s => s.songID);
  return [songIDs, `找到${songIDs.length}首匹配的歌曲`];
}

function handleGetSongByID(data) {
  const { userID, userToken, songID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [null, '歌曲不存在'];
  }
  
  return [song, '获取歌曲信息成功'];
}

function handleGetSongList(data) {
  const { userID, userToken } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const songIDs = songs.map(s => s.songID);
  return [songIDs, '获取歌曲列表成功'];
}

function handleCreateNewGenre(data) {
  const { adminID, adminToken, name, description } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [null, '管理员验证失败'];
  }
  
  if (genres.find(g => g.name === name)) {
    return [null, '曲风名称已存在'];
  }
  
  const genreID = generateID('genre');
  const newGenre = { genreID, name, description };
  genres.push(newGenre);
  
  return [genreID, '曲风创建成功'];
}

function handleDeleteGenre(data) {
  const { adminID, adminToken, genreID } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [false, '管理员验证失败'];
  }
  
  const index = genres.findIndex(g => g.genreID === genreID);
  if (index === -1) {
    return [false, '曲风不存在'];
  }
  
  genres.splice(index, 1);
  return [true, '曲风删除成功'];
}

function handleGetGenreList(data) {
  const { userID, userToken } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  return [genres, '获取曲风列表成功'];
}

function handleFilterSongsByEntity(data) {
  const { userID, userToken, creator, genres: genreFilter } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  let filteredSongs = songs;
  
  // 按创作者过滤
  if (creator && creator.creatorType && creator.id) {
    filteredSongs = filteredSongs.filter(s => 
      s.creators.some(c => c.creatorType === creator.creatorType && c.id === creator.id)
    );
  }
  
  // 按曲风过滤
  if (genreFilter) {
    filteredSongs = filteredSongs.filter(s => 
      s.genres.includes(genreFilter)
    );
  }
  
  const songIDs = filteredSongs.map(s => s.songID);
  return [songIDs, `找到${songIDs.length}首符合条件的歌曲`];
}

function handleValidateSongOwnership(data) {
  const { userID, userToken, songID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const user = users.find(u => u.userID === userID);
  const song = songs.find(s => s.songID === songID);
  
  if (!user || !song) {
    return [false, '用户或歌曲不存在'];
  }
  
  if (user.role === 'admin') {
    return [true, '管理员拥有歌曲管理权限'];
  }
  
  if (song.uploaderID === userID) {
    return [true, '拥有歌曲管理权限（上传者）'];
  }
  
  // 检查用户是否管理相关的创作者
  const allCreators = song.creators || [];
  for (const creator of allCreators) {
    if (creator.creatorType === 'artist') {
      const artist = artists.find(a => a.artistID === creator.id);
      if (artist && artist.managers && artist.managers.includes(userID)) {
        return [true, '拥有歌曲管理权限（艺术家管理者）'];
      }
    } else if (creator.creatorType === 'band') {
      const band = bands.find(b => b.bandID === creator.id);
      if (band && band.managers && band.managers.includes(userID)) {
        return [true, '拥有歌曲管理权限（乐队管理者）'];
      }
    }
  }
  
  return [false, '没有歌曲管理权限'];
}

function handleGetSongProfile(data) {
  const { userID, userToken, songID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [null, '歌曲不存在'];
  }
  
  // 计算歌曲画像：基于曲风生成一个Profile
  const profile = {
    vector: genres.map(genre => ({
      GenreID: genre.genreID,
      value: song.genres.includes(genre.genreID) ? 1.0 : 0.0
    })),
    norm: false
  };
  
  return [profile, '获取歌曲画像成功'];
}

function handleGetMultSongsProfiles(data) {
  const { userID, userToken, songIDs } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const profiles = songIDs.map(songID => {
    const song = songs.find(s => s.songID === songID);
    if (!song) return null;
    
    const profile = {
      vector: genres.map(genre => ({
        GenreID: genre.genreID,
        value: song.genres.includes(genre.genreID) ? 1.0 : 0.0
      })),
      norm: false
    };
    
    return [songID, profile];
  }).filter(p => p !== null);
  
  return [profiles, '获取歌曲画像成功'];
}

// ==================== StatisticsService 处理函数 ====================
function handleLogPlayback(data) {
  const { userID, userToken, songID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [false, '歌曲不存在'];
  }
  
  // 记录播放日志
  playbackLogs.push({
    userID,
    songID,
    timestamp: Date.now()
  });
  
  // 更新用户画像
  userProfiles.set(userID, calculateUserProfile(userID));
  
  return [true, '播放记录成功'];
}

function handleRateSong(data) {
  const { userID, userToken, songID, rating } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  if (rating < 1 || rating > 5) {
    return [false, '评分必须在1-5之间'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [false, '歌曲不存在'];
  }
  
  // 记录评分
  const ratingKey = `${userID}-${songID}`;
  songRatings.set(ratingKey, rating);
  
  return [true, '评分成功'];
}

function handleUnrateSong(data) {
  const { userID, userToken, songID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const ratingKey = `${userID}-${songID}`;
  songRatings.delete(ratingKey);
  
  return [true, '撤销评分成功'];
}

function handleGetSongRate(data) {
  const { userID, userToken, targetUserID, songID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [0, '用户验证失败'];
  }
  
  const ratingKey = `${targetUserID}-${songID}`;
  const rating = songRatings.get(ratingKey) || 0;
  
  return [rating, rating > 0 ? '获取评分成功' : '该用户未对此歌曲评分'];
}

function handleGetAverageRating(data) {
  const { userID, userToken, songID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [[0, 0], '用户验证失败'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [[0, 0], '歌曲不存在'];
  }
  
  // 计算平均评分
  let totalRating = 0;
  let ratingCount = 0;
  
  songRatings.forEach((rating, key) => {
    if (key.endsWith(`-${songID}`)) {
      totalRating += rating;
      ratingCount++;
    }
  });
  
  const averageRating = ratingCount > 0 ? totalRating / ratingCount : 0;
  
  return [[averageRating, ratingCount], '获取平均评分成功'];
}

function handleGetUserPortrait(data) {
  const { userID, userToken } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  // 获取或计算用户画像
  let profile = userProfiles.get(userID);
  if (!profile) {
    profile = calculateUserProfile(userID);
    userProfiles.set(userID, profile);
  }
  
  return [profile, '获取用户画像成功'];
}

function handleGetUserSongRecommendations(data) {
  const { userID, userToken, pageNumber = 1, pageSize = 20 } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  // 基于用户画像推荐歌曲
  const userProfile = userProfiles.get(userID) || calculateUserProfile(userID);
  
  // 计算每首歌曲与用户画像的匹配度
  const scoredSongs = songs.map(song => {
    let score = 0;
    song.genres.forEach(genreID => {
      const genreValue = userProfile.vector.find(v => v.GenreID === genreID);
      if (genreValue) {
        score += genreValue.value;
      }
    });
    return { song, score };
  });
  
  // 按匹配度排序
  scoredSongs.sort((a, b) => b.score - a.score);
  
  // 分页返回
  const startIndex = (pageNumber - 1) * pageSize;
  const endIndex = startIndex + pageSize;
  const recommendedSongs = scoredSongs.slice(startIndex, endIndex);
  const songIDs = recommendedSongs.map(s => s.song.songID);
  
  return [songIDs, '获取推荐歌曲成功'];
}

function handleGetNextSongRecommendation(data) {
  const { userID, userToken, currentSongID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const currentSong = songs.find(s => s.songID === currentSongID);
  if (!currentSong) {
    return [null, '当前歌曲不存在'];
  }
  
  // 找到具有相似曲风的歌曲
  const similarSongs = songs.filter(s => {
    if (s.songID === currentSongID) return false;
    // 检查是否有共同的曲风
    return s.genres.some(g => currentSong.genres.includes(g));
  });
  
  if (similarSongs.length === 0) {
    // 如果没有相似的，随机推荐
    const otherSongs = songs.filter(s => s.songID !== currentSongID);
    if (otherSongs.length === 0) {
      return [null, '没有其他歌曲可推荐'];
    }
    const randomSong = otherSongs[Math.floor(Math.random() * otherSongs.length)];
    return [randomSong.songID, '获取下一首推荐歌曲成功'];
  }
  
  const nextSong = similarSongs[Math.floor(Math.random() * similarSongs.length)];
  return [nextSong.songID, '获取下一首推荐歌曲成功'];
}

function handleGetSongPopularity(data) {
  const { userID, userToken, songID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [null, '歌曲不存在'];
  }
  
  // 计算热度：播放次数 * 10 + 评分数 * 20 + 平均评分 * 100
  const playCount = playbackLogs.filter(log => log.songID === songID).length;
  
  let ratingCount = 0;
  let totalRating = 0;
  songRatings.forEach((rating, key) => {
    if (key.endsWith(`-${songID}`)) {
      totalRating += rating;
      ratingCount++;
    }
  });
  
  const avgRating = ratingCount > 0 ? totalRating / ratingCount : 0;
  const popularity = playCount * 10 + ratingCount * 20 + avgRating * 100;
  
  return [popularity, '获取歌曲热度成功'];
}

function handleGetSimilarSongs(data) {
  const { userID, userToken, songID, limit = 10 } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [null, '歌曲不存在'];
  }
  
  // 计算相似度：基于共同的曲风和创作者
  const scoredSongs = songs
    .filter(s => s.songID !== songID)
    .map(s => {
      let score = 0;
      
      // 共同曲风
      const commonGenres = s.genres.filter(g => song.genres.includes(g));
      score += commonGenres.length * 10;
      
      // 共同创作者
      const commonCreators = s.creators.filter(c1 => 
        song.creators.some(c2 => c1.creatorType === c2.creatorType && c1.id === c2.id)
      );
      score += commonCreators.length * 20;
      
      return { songID: s.songID, score };
    })
    .filter(s => s.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit);
  
  const songIDs = scoredSongs.map(s => s.songID);
  return [songIDs, '获取相似歌曲成功'];
}

function handleGetSimilarCreators(data) {
  const { userID, userToken, creatorID, limit = 10 } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  // 找到该创作者的所有歌曲
  const creatorSongs = songs.filter(s => 
    s.creators.some(c => c.id === creatorID.id && c.creatorType === creatorID.creatorType)
  );
  
  if (creatorSongs.length === 0) {
    return [[], '该创作者没有歌曲'];
  }
  
  // 统计该创作者的曲风分布
  const genreCount = new Map();
  creatorSongs.forEach(song => {
    song.genres.forEach(genreID => {
      genreCount.set(genreID, (genreCount.get(genreID) || 0) + 1);
    });
  });
  
  // 计算其他创作者的相似度
  const allCreators = [
    ...artists.map(a => ({ creatorType: 'artist', id: a.artistID })),
    ...bands.map(b => ({ creatorType: 'band', id: b.bandID }))
  ].filter(c => !(c.id === creatorID.id && c.creatorType === creatorID.creatorType));
  
  const scoredCreators = allCreators.map(creator => {
    const creatorSongs = songs.filter(s => 
      s.creators.some(c => c.id === creator.id && c.creatorType === creator.creatorType)
    );
    
    let score = 0;
    creatorSongs.forEach(song => {
      song.genres.forEach(genreID => {
        if (genreCount.has(genreID)) {
          score += genreCount.get(genreID);
        }
      });
    });
    
    return { creator, score };
  })
  .filter(s => s.score > 0)
  .sort((a, b) => b.score - a.score)
  .slice(0, limit);
  
  const creatorTuples = scoredCreators.map(s => [s.creator.id, s.creator.creatorType]);
  return [creatorTuples, '获取相似创作者成功'];
}

function handleGetCreatorCreationTendency(data) {
  const { userID, userToken, creator } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  // 找到该创作者的所有歌曲
  const creatorSongs = songs.filter(s => 
    s.creators.some(c => c.id === creator.id && c.creatorType === creator.creatorType)
  );
  
  if (creatorSongs.length === 0) {
    // 返回零向量
    const profile = {
      vector: genres.map(genre => ({
        GenreID: genre.genreID,
        value: 0
      })),
      norm: true
    };
    return [profile, '该创作者没有歌曲'];
  }
  
  // 统计曲风分布
  const genreCount = new Map();
  let totalCount = 0;
  
  creatorSongs.forEach(song => {
    song.genres.forEach(genreID => {
      genreCount.set(genreID, (genreCount.get(genreID) || 0) + 1);
      totalCount++;
    });
  });
  
  // 归一化
  const profile = {
    vector: genres.map(genre => ({
      GenreID: genre.genreID,
      value: totalCount > 0 ? (genreCount.get(genre.genreID) || 0) / totalCount : 0
    })),
    norm: true
  };
  
  return [profile, '获取创作倾向成功'];
}

function handleGetCreatorGenreStrength(data) {
  const { userID, userToken, creator } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  // 找到该创作者的所有歌曲
  const creatorSongs = songs.filter(s => 
    s.creators.some(c => c.id === creator.id && c.creatorType === creator.creatorType)
  );
  
  // 计算每个曲风的实力：歌曲数量 * 100 + 总热度
  const genreStrength = new Map();
  
  genres.forEach(genre => {
    const genreSongs = creatorSongs.filter(s => s.genres.includes(genre.genreID));
    let strength = genreSongs.length * 100;
    
    // 加上歌曲热度
    genreSongs.forEach(song => {
      const [popularity] = handleGetSongPopularity({ userID, userToken, songID: song.songID });
      if (typeof popularity === 'number') {
        strength += popularity;
      }
    });
    
    genreStrength.set(genre.genreID, strength);
  });
  
  const profile = {
    vector: genres.map(genre => ({
      GenreID: genre.genreID,
      value: genreStrength.get(genre.genreID) || 0
    })),
    norm: false
  };
  
  return [profile, '获取曲风实力成功'];
}

function handlePurgeSongStatistics(data) {
  const { adminID, adminToken, songID } = data;
  
  if (!validateAdmin(adminID, adminToken)) {
    return [false, '管理员验证失败'];
  }
  
  // 删除所有相关的评分
  const keysToDelete = [];
  songRatings.forEach((rating, key) => {
    if (key.endsWith(`-${songID}`)) {
      keysToDelete.push(key);
    }
  });
  keysToDelete.forEach(key => songRatings.delete(key));
  
  // 删除所有相关的播放记录
  playbackLogs = playbackLogs.filter(log => log.songID !== songID);
  
  return [true, '歌曲统计数据清理成功'];
}

// ==================== 启动服务器 ====================
app.listen(PORT, () => {
  console.log(`🎵 Music Management Mock Server is running on port ${PORT}`);
  console.log(`📊 Initial data:`);
  console.log(`   - Users: ${users.length} (${users.filter(u => u.role === 'admin').length} admins, ${users.filter(u => u.role === 'user').length} regular users)`);
  console.log(`   - Artists: ${artists.length}`);
  console.log(`   - Bands: ${bands.length}`);
  console.log(`   - Songs: ${songs.length}`);
  console.log(`   - Genres: ${genres.length}`);
  console.log(`🔐 Permission system:`);
  console.log(`   - Admin users can: create/update/delete all resources`);
  console.log(`   - Regular users can: view all, edit/delete only managed resources`);
  console.log(`📈 Statistics features:`);
  console.log(`   - Song ratings (1-5 stars)`);
  console.log(`   - Playback tracking`);
  console.log(`   - User portraits based on listening history`);
  console.log(`   - Song popularity calculation`);
  console.log(`   - Personalized recommendations`);
  console.log(`🚀 Server ready for API calls!`);
});

// 优雅关闭
process.on('SIGTERM', () => {
  console.log('🛑 Shutting down mock server gracefully...');
  process.exit(0);
});

process.on('SIGINT', () => {
  console.log('🛑 Shutting down mock server gracefully...');
  process.exit(0);
});
