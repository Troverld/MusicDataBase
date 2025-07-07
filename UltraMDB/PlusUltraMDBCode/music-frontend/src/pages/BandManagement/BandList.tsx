import React from 'react';
import { Band } from '../../types';
import BandItem from './BandItem';

interface BandListProps {
  bands: Band[];
  loading: boolean;
  memberNamesDisplay: { [bandID: string]: string[] };
  onEdit: (band: Band) => void;
  onDelete: (bandID: string) => void;
  searchKeyword: string;
}

const BandList: React.FC<BandListProps> = ({
  bands,
  loading,
  memberNamesDisplay,
  onEdit,
  onDelete,
  searchKeyword
}) => {
  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '40px' }}>
        <p>正在加载乐队信息...</p>
      </div>
    );
  }

  if (bands.length === 0) {
    return (
      <div className="empty-state">
        <p>未找到乐队</p>
        <p style={{ fontSize: '14px', color: '#999', marginTop: '10px' }}>
          {searchKeyword.trim() ? '请尝试其他搜索关键词' : '请使用搜索功能查找乐队'}
        </p>
      </div>
    );
  }

  return (
    <div className="song-list">
      {bands.map((band) => (
        <BandItem
          key={band.bandID}
          band={band}
          onEdit={onEdit}
          onDelete={onDelete}
          memberNamesDisplay={memberNamesDisplay}
        />
      ))}
    </div>
  );
};

export default BandList;