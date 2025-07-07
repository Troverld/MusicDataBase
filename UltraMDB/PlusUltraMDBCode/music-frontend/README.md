# Music Management System Frontend - 代码结构说明

## 项目结构

```
music-frontend/
├── src/
│   ├── types/              # TypeScript 类型定义
│   ├── services/           # API 服务层
│   ├── hooks/              # 自定义 React Hooks
│   ├── pages/              # 页面组件
│   ├── components/         # 可复用组件
│   ├── styles/             # 样式文件
│   ├── utils/              # 工具函数
│   ├── App.tsx             # 应用主组件和路由
│   └── index.tsx           # 应用入口
├── public/                 # 静态资源
├── package.json
└── tsconfig.json
```

## 核心类型定义 (`src/types/index.ts`)

### 认证相关
```typescript
interface User {
  userID: string;
  account: string;
  userToken?: string;
}

type LoginResponse = [[string, string] | null, string];  // [userID, userToken] | null, errorMsg
type RegisterResponse = [string | null, string];         // userID | null, errorMsg
```

### 音乐实体
```typescript
interface CreatorID_Type {
  creatorType: 'artist' | 'band';
  id: string;
}

interface Song {
  songID: string;
  name: string;
  releaseTime: number;
  creators: CreatorID_Type[];    // 新格式：支持艺术家和乐队
  performers: string[];
  genres: string[];
  // ... 其他可选字段
}

interface Artist {
  artistID: string;
  name: string;
  bio: string;
  managers?: string[];
}

interface Band {
  bandID: string;
  name: string;
  members: string[];
  bio: string;
  managers?: string[];
}
```

## 服务层 (`src/services/`)

### API 基础设施 (`api.ts`)
```typescript
// 服务端口映射
const SERVICE_PORTS = {
  organize: 10011,
  music: 10010,
  creator: 10012,
  track: 10013
};

// 统一 API 调用函数
callAPI<T>(endpoint: string, data: any): Promise<T>
```

### 业务服务

#### `auth.service.ts`
- `login(credentials)` - 用户登录
- `register(credentials)` - 用户注册
- `logout(userID, userToken)` - 用户登出

#### `music.service.ts`
- `uploadSong(songData)` - 上传新歌曲
- `updateSong(songID, songData)` - 更新歌曲信息
- `searchSongs(keywords)` - 搜索歌曲
- `getSongById(songID)` - 获取歌曲详情
- `filterSongsByEntity(creator?, genreID?)` - 按创作者/曲风筛选

#### `artist.service.ts` / `band.service.ts`
- `create*(data)` - 创建艺术家/乐队
- `update*(id, data)` - 更新信息
- `delete*(id)` - 删除
- `get*ById(id)` - 获取详情
- `search*ByName(name)` - 按名称搜索
- `get*sByIds(ids[])` - 批量获取

#### `permission.service.ts`
- `validateUser()` - 验证用户权限
- `validateAdmin()` - 验证管理员权限
- `validate*Ownership(id)` - 验证资源所有权

## 自定义 Hooks (`src/hooks/`)

### `usePermissions.ts`
```typescript
// 全局权限状态
usePermissions(): {
  isUser: boolean;
  isAdmin: boolean;
  loading: boolean;
  error: string;
}

// 资源级权限
useSongPermission(songID): { canEdit, loading, error }
useArtistPermission(artistID): { canEdit, loading, error }
useBandPermission(bandID): { canEdit, loading, error }
```

### `useArtistBand.ts`
```typescript
interface ArtistBandItem {
  id: string;
  name: string;
  bio: string;
  type: 'artist' | 'band';
  members?: string[];
}

// 主要函数
searchArtistBand(keyword, searchType): Promise<ArtistBandItem[]>
convertCreatorsToSelectedItems(creators: CreatorID_Type[]): Promise<ArtistBandItem[]>
getArtistBandsByIds(items: {id, type}[]): Promise<ArtistBandItem[]>
```

### `useGenres.ts`
```typescript
useGenres(): {
  genres: Genre[];
  loading: boolean;
  error: string;
  fetchGenres(): void;
  getGenreNamesByIds(ids[]): string[];
  getGenreIdsByNames(names[]): string[];
}
```

## 重要组件

### `ArtistBandSelector/` - 智能选择器
核心组件，用于在歌曲表单中选择艺术家或乐队：
- 支持搜索功能
- 显示选中项详情
- 支持单选/多选模式
- 自动处理 ID 与名称映射

### `SongList/` - 歌曲列表
- `formatCreatorList()` - 格式化创作者列表（处理 CreatorID_Type[]）
- `formatStringCreatorList()` - 格式化传统 string[] 格式
- `getAllCreatorInfo()` - 提取所有创作者信息用于批量查询

### `GenreSelector` - 曲风选择器
自定义多选下拉组件，支持：
- 展开/收起
- 批量选择/清空
- 选中项标签显示

## 页面结构

### 认证模块
- `/login` - 登录页面
- `/register` - 注册页面

### 管理模块
- `/` - 仪表板（Dashboard）
- `/songs` - 歌曲管理
- `/artists` - 艺术家管理
- `/artists/:artistID` - 艺术家详情
- `/bands` - 乐队管理  
- `/bands/:bandID` - 乐队详情
- `/genres` - 曲风管理

### 权限控制
- 管理员：完整的 CRUD 权限
- 普通用户：创建和编辑自己的内容
- 访客：需要登录才能访问

## 工具函数 (`src/utils/storage.ts`)

```typescript
// Token 管理
setToken(token: string): void
getToken(): string | null
removeToken(): void

// 用户信息管理
setUser(user: User): void
getUser(): User | null
removeUser(): void

// 清除认证
clearAuth(): void
```

## 样式架构

- `base.css` - 全局样式和重置
- `layout.css` - 布局相关（容器、导航栏、列表）
- `forms.css` - 表单元素样式
- `components.css` - 组件样式（多选框、标签等）
- `modal.css` - 模态框样式
