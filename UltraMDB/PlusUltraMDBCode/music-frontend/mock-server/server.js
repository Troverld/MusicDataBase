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

// Helper function to generate IDs
const generateId = () => `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

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
  const { userName, hashedPassword } = req.body;
  
  const user = db.users.find(u => u.userName === userName && u.password === hashedPassword);
  
  if (user) {
    const token = `token-${generateId()}`;
    user.userToken = token;
    user.userID = user.userID || generateId();
    
    // Save token
    fs.writeFileSync(path.join(__dirname, 'db.json'), JSON.stringify(db, null, 2));
    
    res.json([token, "登录成功"]);
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
});