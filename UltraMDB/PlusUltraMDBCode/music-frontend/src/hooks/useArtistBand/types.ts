// 艺术家/乐队项目的统一接口
export interface ArtistBandItem {
    id: string;
    name: string;
    bio: string;
    type: 'artist' | 'band';
    members?: string[]; // 只有乐队才有
}
