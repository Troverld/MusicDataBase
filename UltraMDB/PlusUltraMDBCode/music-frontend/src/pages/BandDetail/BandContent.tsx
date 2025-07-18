import React from 'react';
import { Link } from 'react-router-dom';
import { Band, Song } from '../../types';
import { ArtistBandItem } from '../../hooks/useArtistBand';
import SongList from '../../components/SongList';
import './BandContent.css';

interface BandContentProps {
  band: Band | null;
  memberDetails: ArtistBandItem[];
  showSongs: boolean;
  bandSongs: Song[];
  onEditSong: (song: Song) => void;
  onDeleteSong: (songID: string) => Promise<void>;
  getMemberInitial: (name: string) => string;
}

const BandContent: React.FC<BandContentProps> = ({
  band,
  memberDetails,
  showSongs,
  bandSongs,
  onEditSong,
  onDeleteSong,
  getMemberInitial
}) => {
  return (
    <div className="band-main-content">
      {/* 简介卡片 */}
      <div className="content-card">
        <div className="content-card-header">
          <h2 className="content-card-title">乐队简介</h2>
        </div>
        <div className={band?.bio ? 'bio-content' : 'bio-content bio-empty'}>
          {band?.bio || '这个乐队还没有添加简介...'}
        </div>
      </div>

      {/* 成员列表 */}
      {memberDetails.length > 0 && (
        <div className="content-card">
          <div className="content-card-header">
            <h2 className="content-card-title">
              乐队成员 ({memberDetails.length})
            </h2>
          </div>
          <div className="members-grid">
            {memberDetails.map((member, index) => {
              const isValidArtist = !member.id.startsWith('not-found-') && 
                                  !member.id.startsWith('error-') && 
                                  !member.id.startsWith('placeholder-') &&
                                  member.type === 'artist';
              
              return isValidArtist ? (
                <Link 
                  key={index}
                  to={`/artists/${member.id}`}
                  className="member-card"
                >
                  <div className="member-avatar">
                    {getMemberInitial(member.name)}
                  </div>
                  <div className="member-info">
                    <div className="member-name">{member.name}</div>
                    <div className="member-role">乐队成员</div>
                    <div className="member-bio">
                      {member.bio || '暂无简介'}
                    </div>
                  </div>
                </Link>
              ) : (
                <div key={index} className="member-card placeholder">
                  <div className="member-avatar">
                    {getMemberInitial(member.name)}
                  </div>
                  <div className="member-info">
                    <div className="member-name">{member.name}</div>
                    <div className="member-role">未找到艺术家信息</div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 歌曲列表 */}
      {showSongs && (
        <div className="content-card">
          <div className="content-card-header">
            <h2 className="content-card-title">
              作品列表 ({bandSongs.length})
            </h2>
          </div>
          {bandSongs.length > 0 ? (
            <SongList 
              songs={bandSongs} 
              onEdit={onEditSong}
              onDelete={onDeleteSong}
            />
          ) : (
            <div className="empty-state-card">
              <div className="empty-state-icon">🎵</div>
              <div className="empty-state-text">暂无作品</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default BandContent;
