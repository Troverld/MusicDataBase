const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = 10011;

// 中间件
app.use(cors());
app.use(express.json());

// 模拟数据存储
let users = [
  { userID: 'admin_001', userName: 'admin', password: 'admin123', userToken: null, role: 'admin' },
  { userID: 'user_001', userName: 'user1', password: 'user123', userToken: null, role: 'user' },
  { userID: 'user_002', userName: 'user2', password: 'user123', userToken: null, role: 'user' }
];

let artists = [
  { artistID: 'artist_001', name: '周杰伦', bio: '华语流行音乐天王，台湾歌手、词曲创作者、演员、导演。', managers: ['user_001'] },
  { artistID: 'artist_002', name: '邓紫棋', bio: '香港创作型女歌手，有着独特的嗓音和创作才华。', managers: ['user_002'] },
  { artistID: 'artist_003', name: '林俊杰', bio: '新加坡华语流行男歌手、词曲创作者、音乐制作人。', managers: [] }
];

let bands = [
  { 
    bandID: 'band_001', 
    name: 'Beyond', 
    members: ['artist_001', 'artist_002'], 
    bio: '香港著名摇滚乐队，成立于1983年。', 
    managers: ['user_001'] 
  },
  { 
    bandID: 'band_002', 
    name: '五月天', 
    members: ['artist_003'], 
    bio: '台湾摇滚乐团，成立于1997年。', 
    managers: [] 
  }
];

let genres = [
  { genreID: 'genre_001', name: '流行', description: '主流的现代流行音乐风格' },
  { genreID: 'genre_002', name: '摇滚', description: '以强烈节拍和电吉他为特色的音乐风格' },
  { genreID: 'genre_003', name: '爵士', description: '起源于美国的音乐风格，强调即兴演奏' },
  { genreID: 'genre_004', name: '古典', description: '传统的西方古典音乐' },
  { genreID: 'genre_005', name: '电子', description: '使用电子设备和技术制作的音乐' }
];

let songs = [
  {
    songID: 'song_001',
    name: '青花瓷',
    releaseTime: 1577836800000, // 2020-01-01
    creators: ['artist_001'],
    performers: ['artist_001'],
    lyricists: ['artist_001'],
    composers: ['artist_001'],
    arrangers: ['artist_001'],
    instrumentalists: ['artist_001'],
    genres: ['genre_001', 'genre_004'],
    uploadedBy: 'user_001',
    uploadTime: Date.now()
  },
  {
    songID: 'song_002',
    name: '泡沫',
    releaseTime: 1609459200000, // 2021-01-01
    creators: ['artist_002'],
    performers: ['artist_002'],
    lyricists: ['artist_002'],
    composers: ['artist_002'],
    arrangers: ['artist_002'],
    instrumentalists: [],
    genres: ['genre_001'],
    uploadedBy: 'user_002',
    uploadTime: Date.now()
  }
];

// 用户会话存储
let userSessions = new Map();

// 辅助函数
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

// 通用路由处理器
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
      
    // StatisticsService APIs
    case 'LogPlayback':
      return handleLogPlayback(data);
    case 'RateSong':
      return handleRateSong(data);
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
    case 'GetSongProfile':
      return handleGetSongProfile(data);
      
    default:
      throw new Error(`Unknown endpoint: ${endpoint}`);
  }
}

// OrganizeService 处理函数
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
  const newUser = { userID, userName, password, userToken: null };
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

// CreatorService 处理函数
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
  const { userID, userToken, artistName } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const matchedArtists = artists.filter(a => 
    a.name.toLowerCase().includes(artistName.toLowerCase())
  );
  
  const artistIDs = matchedArtists.map(a => a.artistID);
  return [artistIDs, `找到${artistIDs.length}个匹配的艺术家`];
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
  
  // 管理员拥有所有权限
  if (user.role === 'admin') {
    return [true, '管理员拥有艺术家管理权限'];
  }
  
  const artist = artists.find(a => a.artistID === artistID);
  if (!artist) {
    return [false, '艺术家不存在'];
  }
  
  // 检查用户是否是艺术家的管理者
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
  
  // 管理员拥有所有权限
  if (user.role === 'admin') {
    return [true, '管理员拥有乐队管理权限'];
  }
  
  const band = bands.find(b => b.bandID === bandID);
  if (!band) {
    return [false, '乐队不存在'];
  }
  
  // 检查用户是否是乐队的管理者
  if (band.managers && band.managers.includes(userID)) {
    return [true, '拥有乐队管理权限'];
  }
  
  return [false, '没有乐队管理权限'];
}

// MusicService 处理函数
function handleUploadNewSong(data) {
  const { userID, userToken, name, releaseTime, creators, performers, lyricists, composers, arrangers, instrumentalists, genres } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  const songID = generateID('song');
  const newSong = {
    songID,
    name,
    releaseTime,
    creators: creators || [],
    performers: performers || [],
    lyricists: lyricists || [],
    composers: composers || [],
    arrangers: arrangers || [],
    instrumentalists: instrumentalists || [],
    genres: genres || [],
    uploadedBy: userID,
    uploadTime: Date.now()
  };
  
  songs.push(newSong);
  return [songID, '歌曲上传成功'];
}

function handleUpdateSongMetadata(data) {
  const { userID, userToken, songID, name, releaseTime, creators, performers, lyricists, composers, arrangers, instrumentalists, genres } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const user = users.find(u => u.userID === userID);
  if (!user) {
    return [false, '用户不存在'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [false, '歌曲不存在'];
  }
  
  // 检查权限：管理员、上传者或相关创作者的管理者才能编辑
  let hasPermission = false;
  
  if (user.role === 'admin') {
    hasPermission = true;
  } else if (song.uploadedBy === userID) {
    hasPermission = true;
  } else {
    // 检查用户是否管理任何相关的艺术家或乐队
    const allCreators = [
      ...(song.creators || []),
      ...(song.performers || []),
      ...(song.lyricists || []),
      ...(song.composers || []),
      ...(song.arrangers || []),
      ...(song.instrumentalists || [])
    ];
    
    for (const creatorID of allCreators) {
      const artist = artists.find(a => a.artistID === creatorID);
      if (artist && artist.managers && artist.managers.includes(userID)) {
        hasPermission = true;
        break;
      }
      
      const band = bands.find(b => b.bandID === creatorID);
      if (band && band.managers && band.managers.includes(userID)) {
        hasPermission = true;
        break;
      }
    }
  }
  
  if (!hasPermission) {
    return [false, '没有编辑该歌曲的权限'];
  }
  
  if (name !== undefined) song.name = name;
  if (releaseTime !== undefined) song.releaseTime = releaseTime;
  if (creators !== undefined) song.creators = creators;
  if (performers !== undefined) song.performers = performers;
  if (lyricists !== undefined) song.lyricists = lyricists;
  if (composers !== undefined) song.composers = composers;
  if (arrangers !== undefined) song.arrangers = arrangers;
  if (instrumentalists !== undefined) song.instrumentalists = instrumentalists;
  if (genres !== undefined) song.genres = genres;
  
  return [true, '歌曲信息更新成功'];
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
  const { userID, userToken, entityID, entityType, genres: genreFilter } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  let filteredSongs = songs;
  
  if (entityID && entityType) {
    if (entityType === 'artist') {
      filteredSongs = filteredSongs.filter(s => 
        s.creators.includes(entityID) || 
        s.performers.includes(entityID) ||
        s.lyricists.includes(entityID) ||
        s.composers.includes(entityID) ||
        s.arrangers.includes(entityID) ||
        s.instrumentalists.includes(entityID)
      );
    } else if (entityType === 'band') {
      filteredSongs = filteredSongs.filter(s => 
        s.creators.includes(entityID) || 
        s.performers.includes(entityID)
      );
    }
  }
  
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
  if (!user) {
    return [false, '用户不存在'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [false, '歌曲不存在'];
  }
  
  // 管理员拥有所有权限
  if (user.role === 'admin') {
    return [true, '管理员拥有歌曲管理权限'];
  }
  
  // 检查用户是否是歌曲的上传者
  if (song.uploadedBy === userID) {
    return [true, '拥有歌曲管理权限（上传者）'];
  }
  
  // 检查用户是否是歌曲创作者之一
  const allCreators = [
    ...(song.creators || []),
    ...(song.performers || []),
    ...(song.lyricists || []),
    ...(song.composers || []),
    ...(song.arrangers || []),
    ...(song.instrumentalists || [])
  ];
  
  // 检查用户是否管理任何相关的艺术家
  for (const creatorID of allCreators) {
    const artist = artists.find(a => a.artistID === creatorID);
    if (artist && artist.managers && artist.managers.includes(userID)) {
      return [true, '拥有歌曲管理权限（艺术家管理者）'];
    }
    
    const band = bands.find(b => b.bandID === creatorID);
    if (band && band.managers && band.managers.includes(userID)) {
      return [true, '拥有歌曲管理权限（乐队管理者）'];
    }
  }
  
  return [false, '没有歌曲管理权限'];
}

// StatisticsService 处理函数
function handleLogPlayback(data) {
  const { userID, userToken, songID } = data;
  
  if (!validateUser(userID, userToken)) {
    return [false, '用户验证失败'];
  }
  
  const song = songs.find(s => s.songID === songID);
  if (!song) {
    return [false, '歌曲不存在'];
  }
  
  // 模拟记录播放日志
  console.log(`用户 ${userID} 播放了歌曲 ${songID}`);
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
  
  // 模拟记录评分
  console.log(`用户 ${userID} 给歌曲 ${songID} 评分 ${rating}`);
  return [true, '评分成功'];
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
  
  // 模拟平均评分
  const averageRating = 4.2 + Math.random() * 0.6; // 4.2-4.8之间
  const ratingCount = Math.floor(Math.random() * 1000) + 50; // 50-1050之间
  
  return [[averageRating, ratingCount], '获取平均评分成功'];
}

function handleGetUserPortrait(data) {
  const { userID, userToken } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  // 模拟用户画像
  const profile = genres.map(genre => ({
    genreID: genre.genreID,
    preference: Math.random() // 0-1之间的偏好度
  }));
  
  return [profile, '获取用户画像成功'];
}

function handleGetUserSongRecommendations(data) {
  const { userID, userToken, pageNumber = 1, pageSize = 20 } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  // 模拟推荐歌曲（随机选择一些歌曲）
  const shuffledSongs = [...songs].sort(() => Math.random() - 0.5);
  const startIndex = (pageNumber - 1) * pageSize;
  const endIndex = startIndex + pageSize;
  const recommendedSongs = shuffledSongs.slice(startIndex, endIndex);
  const songIDs = recommendedSongs.map(s => s.songID);
  
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
  
  // 模拟推荐下一首歌（随机选择）
  const otherSongs = songs.filter(s => s.songID !== currentSongID);
  if (otherSongs.length === 0) {
    return [null, '没有其他歌曲可推荐'];
  }
  
  const randomSong = otherSongs[Math.floor(Math.random() * otherSongs.length)];
  return [randomSong.songID, '获取下一首推荐歌曲成功'];
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
  
  // 模拟热度值
  const popularity = Math.random() * 1000 + 100; // 100-1100之间
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
  
  // 模拟相似歌曲（随机选择一些歌曲）
  const otherSongs = songs.filter(s => s.songID !== songID);
  const shuffledSongs = otherSongs.sort(() => Math.random() - 0.5);
  const similarSongs = shuffledSongs.slice(0, limit);
  const songIDs = similarSongs.map(s => s.songID);
  
  return [songIDs, '获取相似歌曲成功'];
}

function handleGetSimilarCreators(data) {
  const { userID, userToken, creatorID, creatorType, limit = 10 } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  let allCreators = [];
  
  if (creatorType === 'artist' || creatorType === 'both') {
    allCreators = allCreators.concat(artists.map(a => ({ id: a.artistID, type: 'artist' })));
  }
  
  if (creatorType === 'band' || creatorType === 'both') {
    allCreators = allCreators.concat(bands.map(b => ({ id: b.bandID, type: 'band' })));
  }
  
  // 过滤掉当前创作者
  const otherCreators = allCreators.filter(c => c.id !== creatorID);
  
  // 随机选择相似创作者
  const shuffledCreators = otherCreators.sort(() => Math.random() - 0.5);
  const similarCreators = shuffledCreators.slice(0, limit);
  const creatorTuples = similarCreators.map(c => [c.id, c.type]);
  
  return [creatorTuples, '获取相似创作者成功'];
}

function handleGetCreatorCreationTendency(data) {
  const { userID, userToken, creatorID, creatorType } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  let creator = null;
  if (creatorType === 'artist') {
    creator = artists.find(a => a.artistID === creatorID);
  } else if (creatorType === 'band') {
    creator = bands.find(b => b.bandID === creatorID);
  }
  
  if (!creator) {
    return [null, '创作者不存在'];
  }
  
  // 模拟创作倾向
  const tendency = genres.map(genre => ({
    genreID: genre.genreID,
    tendency: Math.random() // 0-1之间的倾向度
  }));
  
  // 归一化处理
  const total = tendency.reduce((sum, t) => sum + t.tendency, 0);
  const normalizedTendency = tendency.map(t => ({
    genreID: t.genreID,
    tendency: t.tendency / total
  }));
  
  return [normalizedTendency, '获取创作倾向成功'];
}

function handleGetCreatorGenreStrength(data) {
  const { userID, userToken, creatorID, creatorType } = data;
  
  if (!validateUser(userID, userToken)) {
    return [null, '用户验证失败'];
  }
  
  let creator = null;
  if (creatorType === 'artist') {
    creator = artists.find(a => a.artistID === creatorID);
  } else if (creatorType === 'band') {
    creator = bands.find(b => b.bandID === creatorID);
  }
  
  if (!creator) {
    return [null, '创作者不存在'];
  }
  
  // 模拟曲风实力
  const strength = genres.map(genre => ({
    genreID: genre.genreID,
    strength: Math.random() * 1000 + 100 // 100-1100之间的实力值
  }));
  
  return [strength, '获取曲风实力成功'];
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
  
  // 模拟歌曲画像
  const profile = genres.map(genre => ({
    genreID: genre.genreID,
    score: Math.random() // 0-1之间的分数
  }));
  
  return [profile, '获取歌曲画像成功'];
}

// 启动服务器
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