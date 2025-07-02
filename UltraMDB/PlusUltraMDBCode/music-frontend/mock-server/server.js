const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = 10011;

// Middleware
app.use(cors());
app.use(bodyParser.json());

// Load mock database
let db = JSON.parse(fs.readFileSync(path.join(__dirname, 'db.json'), 'utf8'));

// Initialize artists array if it doesn't exist
if (!db.artists) {
  db.artists = [];
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
}

// Helper function to generate IDs
const generateId = () => `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

// Helper function to generate artist IDs
const generateArtistId = () => `artist-${String(db.artists.length + 1).padStart(3, '0')}`;

// Log all requests
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);
  console.log('Request Body:', JSON.stringify(req.body, null, 2));
  next();
});

// API Routes

// User Registration
app.post('/api/UserRegisterMessage', (req, res) => {
  const { userName, password } = req.body;
  
  // Check if user already exists
  if (db.users.find(u => u.userName === userName)) {
    return res.json([null, "用户名已存在"]);
  }
  
  // Create new user
  const userId = generateId();
  db.users.push({
    userID: userId,
    userName,
    password,
    account: userName
  });
  
  // Save to db
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([userId, "注册成功"]);
});

// User Login
app.post('/api/UserLoginMessage', (req, res) => {
  const { userName, password } = req.body;
  
  const user = db.users.find(u => u.userName === userName && u.password === password);
  
  if (user) {
    const token = `token-${generateId()}`;
    const userID = user.userID || generateId();
    user.userToken = token;
    user.userID = userID;
    
    // 保存更新
    fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
    
    // 返回 [[userID, token], message] 格式
    res.json([[userID, token], "登录成功"]);
  } else {
    res.json([null, "用户名或密码错误"]);
  }
});

// User Logout
app.post('/api/UserLogoutMessage', (req, res) => {
  const { userID, userToken } = req.body;
  
  const user = db.users.find(u => u.userID === userID && u.userToken === userToken);
  
  if (user) {
    delete user.userToken;
    fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
    res.json([true, "登出成功"]);
  } else {
    res.json([false, "无效的用户令牌"]);
  }
});

// Upload New Song
app.post('/api/UploadNewSong', (req, res) => {
  const { userID, userToken, name, releaseTime, creators, performers, genres, lyricists, composers, arrangers, instrumentalists } = req.body;
  
  // Verify user
  const user = db.users.find(u => u.userToken === userToken);
  if (!user) {
    return res.json([null, "用户认证失败"]);
  }
  
  // Create new song
  const songId = generateId();
  const newSong = {
    songID: songId,
    name,
    releaseTime,
    creators: creators || [],
    performers: performers || [],
    genres: genres || [],
    lyricists: lyricists || [],
    composers: composers || [],
    arrangers: arrangers || [],
    instrumentalists: instrumentalists || [],
    uploadedBy: userID,
    uploadTime: Date.now()
  };
  
  db.songs.push(newSong);
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([songId, "歌曲上传成功"]);
});

// Update Song Metadata
app.post('/api/UpdateSongMetadata', (req, res) => {
  const { userID, userToken, songID, ...updateData } = req.body;
  
  // Verify user
  const user = db.users.find(u => u.userToken === userToken);
  if (!user) {
    return res.json([false, "用户认证失败"]);
  }
  
  // Find song
  const songIndex = db.songs.findIndex(s => s.songID === songID);
  if (songIndex === -1) {
    return res.json([false, "歌曲不存在"]);
  }
  
  // Update song
  Object.keys(updateData).forEach(key => {
    if (updateData[key] !== undefined && updateData[key] !== null) {
      db.songs[songIndex][key] = updateData[key];
    }
  });
  
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([true, "歌曲更新成功"]);
});

// Delete Song (Admin only)
app.post('/api/DeleteSong', (req, res) => {
  const { adminID, adminToken, songID } = req.body;
  
  // Verify admin (for demo, any logged-in user can delete)
  const user = db.users.find(u => u.userToken === adminToken);
  if (!user) {
    return res.json([false, "管理员认证失败"]);
  }
  
  // Delete song
  const songIndex = db.songs.findIndex(s => s.songID === songID);
  if (songIndex === -1) {
    return res.json([false, "歌曲不存在"]);
  }
  
  db.songs.splice(songIndex, 1);
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([true, "歌曲删除成功"]);
});

// Search Songs by Name
app.post('/api/SearchSongsByName', (req, res) => {
  const { userID, userToken, keywords } = req.body;
  
  // Verify user
  const user = db.users.find(u => u.userToken === userToken);
  if (!user) {
    return res.json([null, "用户认证失败"]);
  }
  
  // Search songs
  const matchedSongs = db.songs.filter(song => 
    song.name.toLowerCase().includes(keywords.toLowerCase())
  );
  
  const songIds = matchedSongs.map(s => s.songID);
  
  res.json([songIds, "搜索成功"]);
});

// Get Song By ID
app.post('/api/GetSongByID', (req, res) => {
  const { userID, userToken, songID } = req.body;
  
  // Verify user
  const user = db.users.find(u => u.userToken === userToken);
  if (!user) {
    return res.json([null, "用户认证失败"]);
  }
  
  // Find song
  const song = db.songs.find(s => s.songID === songID);
  if (!song) {
    return res.json([null, "歌曲不存在"]);
  }
  
  res.json([song, "获取歌曲成功"]);
});

// Get Genre List
app.post('/api/GetGenreList', (req, res) => {
  const { userID, userToken } = req.body;
  
  // Verify user
  const user = db.users.find(u => u.userToken === userToken);
  if (!user) {
    return res.json([null, "用户认证失败"]);
  }
  
  // Return all genres
  res.json([db.genres, "获取曲风列表成功"]);
});

// Create New Genre
app.post('/api/CreateNewGenre', (req, res) => {
  const { adminID, adminToken, name, description } = req.body;
  
  // Verify admin (for demo, any logged-in user can create genres)
  const user = db.users.find(u => u.userToken === adminToken);
  if (!user) {
    return res.json([null, "管理员认证失败"]);
  }
  
  // Check if genre name already exists
  if (db.genres.find(g => g.name.toLowerCase() === name.toLowerCase())) {
    return res.json([null, "曲风名称已存在"]);
  }
  
  // Create new genre
  const genreId = `genre-${String(db.genres.length + 1).padStart(3, '0')}`;
  const newGenre = {
    genreID: genreId,
    name,
    description
  };
  
  db.genres.push(newGenre);
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([genreId, "曲风创建成功"]);
});

// Delete Genre
app.post('/api/DeleteGenre', (req, res) => {
  const { adminID, adminToken, genreID } = req.body;
  
  // Verify admin (for demo, any logged-in user can delete genres)
  const user = db.users.find(u => u.userToken === adminToken);
  if (!user) {
    return res.json([false, "管理员认证失败"]);
  }
  
  // Find genre
  const genreIndex = db.genres.findIndex(g => g.genreID === genreID);
  if (genreIndex === -1) {
    return res.json([false, "曲风不存在"]);
  }
  
  // Check if any songs use this genre
  const songsUsingGenre = db.songs.filter(song => 
    song.genres && song.genres.includes(genreID)
  );
  
  if (songsUsingGenre.length > 0) {
    return res.json([false, `无法删除曲风：有 ${songsUsingGenre.length} 首歌曲正在使用此曲风`]);
  }
  
  // Delete genre
  db.genres.splice(genreIndex, 1);
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([true, "曲风删除成功"]);
});

// ============ ARTIST MANAGEMENT APIs ============

// Create Artist
app.post('/api/CreateArtistMessage', (req, res) => {
  const { adminID, adminToken, name, bio } = req.body;
  
  // Verify admin (for demo, any logged-in user can create artists)
  const user = db.users.find(u => u.userToken === adminToken);
  if (!user) {
    return res.json([null, "管理员认证失败"]);
  }
  
  // Check if artist name already exists
  if (db.artists.find(a => a.name.toLowerCase() === name.toLowerCase())) {
    return res.json([null, "艺术家名称已存在"]);
  }
  
  // Create new artist
  const artistId = generateArtistId();
  const newArtist = {
    artistID: artistId,
    name,
    bio,
    managers: [] // Initialize empty managers array
  };
  
  db.artists.push(newArtist);
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([artistId, "艺术家创建成功"]);
});

// Update Artist
app.post('/api/UpdateArtistMessage', (req, res) => {
  const { userID, userToken, artistID, name, bio } = req.body;
  
  // Verify user
  const user = db.users.find(u => u.userToken === userToken);
  if (!user) {
    return res.json([false, "用户认证失败"]);
  }
  
  // Find artist
  const artistIndex = db.artists.findIndex(a => a.artistID === artistID);
  if (artistIndex === -1) {
    return res.json([false, "艺术家不存在"]);
  }
  
  // Update artist fields if provided
  if (name) {
    // Check if new name conflicts with existing artists (excluding current one)
    const existingArtist = db.artists.find(a => 
      a.artistID !== artistID && a.name.toLowerCase() === name.toLowerCase()
    );
    if (existingArtist) {
      return res.json([false, "艺术家名称已存在"]);
    }
    db.artists[artistIndex].name = name;
  }
  
  if (bio) {
    db.artists[artistIndex].bio = bio;
  }
  
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([true, "艺术家信息更新成功"]);
});

// Delete Artist
app.post('/api/DeleteArtistMessage', (req, res) => {
  const { adminID, adminToken, artistID } = req.body;
  
  // Verify admin (for demo, any logged-in user can delete artists)
  const user = db.users.find(u => u.userToken === adminToken);
  if (!user) {
    return res.json([false, "管理员认证失败"]);
  }
  
  // Find artist
  const artistIndex = db.artists.findIndex(a => a.artistID === artistID);
  if (artistIndex === -1) {
    return res.json([false, "艺术家不存在"]);
  }
  
  // Check if any songs reference this artist
  const songsReferencingArtist = db.songs.filter(song => 
    (song.creators && song.creators.includes(artistID)) ||
    (song.performers && song.performers.includes(artistID)) ||
    (song.lyricists && song.lyricists.includes(artistID)) ||
    (song.composers && song.composers.includes(artistID)) ||
    (song.arrangers && song.arrangers.includes(artistID)) ||
    (song.instrumentalists && song.instrumentalists.includes(artistID))
  );
  
  if (songsReferencingArtist.length > 0) {
    return res.json([false, `无法删除艺术家：有 ${songsReferencingArtist.length} 首歌曲正在引用此艺术家`]);
  }
  
  // Delete artist
  db.artists.splice(artistIndex, 1);
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([true, "艺术家删除成功"]);
});

// Get Artist By ID
app.post('/api/GetArtistByID', (req, res) => {
  const { userID, userToken, artistID } = req.body;
  
  // Verify user
  const user = db.users.find(u => u.userToken === userToken);
  if (!user) {
    return res.json([null, "用户认证失败"]);
  }
  
  // Find artist
  const artist = db.artists.find(a => a.artistID === artistID);
  if (!artist) {
    return res.json([null, "艺术家不存在"]);
  }
  
  res.json([artist, "获取艺术家成功"]);
});

// Search Artist By Name
app.post('/api/SearchArtistByName', (req, res) => {
  const { userID, userToken, artistName } = req.body;
  
  // Verify user
  const user = db.users.find(u => u.userToken === userToken);
  if (!user) {
    return res.json([null, "用户认证失败"]);
  }
  
  // Search artists by name (fuzzy match)
  const matchedArtists = db.artists.filter(artist => 
    artist.name.toLowerCase().includes(artistName.toLowerCase())
  );
  
  const artistIds = matchedArtists.map(a => a.artistID);
  
  if (artistIds.length === 0) {
    return res.json([null, "未找到匹配的艺术家"]);
  }
  
  res.json([artistIds, "搜索成功"]);
});

// Add Artist Manager
app.post('/api/AddArtistManager', (req, res) => {
  const { adminID, adminToken, userID, artistID } = req.body;
  
  // Verify admin
  const admin = db.users.find(u => u.userToken === adminToken && u.userID === adminID);
  if (!admin) {
    return res.json([false, "管理员认证失败"]);
  }
  
  // Check if target user exists
  const targetUser = db.users.find(u => u.userID === userID);
  if (!targetUser) {
    return res.json([false, "目标用户不存在"]);
  }
  
  // Find artist
  const artistIndex = db.artists.findIndex(a => a.artistID === artistID);
  if (artistIndex === -1) {
    return res.json([false, "艺术家不存在"]);
  }
  
  // Check if user is already a manager
  if (db.artists[artistIndex].managers.includes(userID)) {
    return res.json([false, "用户已经是该艺术家的管理者"]);
  }
  
  // Add user to managers list
  db.artists[artistIndex].managers.push(userID);
  fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
  
  res.json([true, "艺术家管理者添加成功"]);
});

// Validate Artist Ownership
app.post('/api/validArtistOwnership', (req, res) => {
  const { userID, userToken, artistID } = req.body;
  
  // Verify user
  const user = db.users.find(u => u.userToken === userToken && u.userID === userID);
  if (!user) {
    return res.json([false, "用户认证失败"]);
  }
  
  // Find artist
  const artist = db.artists.find(a => a.artistID === artistID);
  if (!artist) {
    return res.json([false, "艺术家不存在"]);
  }
  
  // Check if user is a manager of this artist (for demo, allow all authenticated users)
  const isManager = artist.managers.includes(userID);
  
  if (isManager) {
    res.json([true, "用户拥有艺术家管理权限"]);
  } else {
    // For demo purposes, allow all authenticated users to manage artists
    res.json([true, "用户拥有艺术家管理权限"]);
  }
});

// Get song details (additional endpoint for testing)
app.post('/api/GetSongDetails', (req, res) => {
  const { songIDs } = req.body;
  
  const songs = db.songs.filter(s => songIDs.includes(s.songID));
  res.json(songs);
});

// Start server
app.listen(PORT, () => {
  console.log(`Mock API Server running on http://localhost:${PORT}`);
  console.log('Available endpoints:');
  console.log('- POST /api/UserRegisterMessage');
  console.log('- POST /api/UserLoginMessage');
  console.log('- POST /api/UserLogoutMessage');
  console.log('- POST /api/UploadNewSong');
  console.log('- POST /api/UpdateSongMetadata');
  console.log('- POST /api/DeleteSong');
  console.log('- POST /api/SearchSongsByName');
  console.log('- POST /api/GetSongByID');
  console.log('- POST /api/GetGenreList');
  console.log('- POST /api/CreateNewGenre');
  console.log('- POST /api/DeleteGenre');
  console.log('');
  console.log('Artist Management APIs:');
  console.log('- POST /api/CreateArtistMessage');
  console.log('- POST /api/UpdateArtistMessage');
  console.log('- POST /api/DeleteArtistMessage');
  console.log('- POST /api/GetArtistByID');
  console.log('- POST /api/SearchArtistByName');
  console.log('- POST /api/AddArtistManager');
  console.log('- POST /api/validArtistOwnership');
});