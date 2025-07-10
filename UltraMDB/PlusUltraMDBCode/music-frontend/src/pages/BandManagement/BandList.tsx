import React from 'react';
import { Band } from '../../types';
import BandItem from './BandItem';
import ModernEmptyState from '../../components/ModernEmptyState';

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
    const bandIcon = (
      <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1">
        <path d="M9 12l2 2 4-4"></path>
        <path d="M21 12c.552 0 1-.48 1-1.067 0-.586-.448-1.066-1-1.066s-1 .48-1 1.066c0 .587.448 1.067 1 1.067z"></path>
        <path d="M3 12c.552 0 1-.48 1-1.067 0-.586-.448-1.066-1-1.066s-1 .48-1 1.066c0 .587.448 1.067 1 1.067z"></path>
        <path d="M15 6c.552 0 1-.48 1-1.067 0-.586-.448-1.066-1-1.066s-1 .48-1 1.066c0 .587.448 1.067 1 1.067z"></path>
        <path d="M9 18c.552 0 1-.48 1-1.067 0-.586-.448-1.066-1-1.066s-1 .48-1 1.066c0 .587.448 1.067 1 1.067z"></path>
      </svg>
    );

    return (
      <ModernEmptyState
        icon={bandIcon}
        title={searchKeyword.trim() ? "未找到匹配的乐队" : "暂无乐队"}
        description={searchKeyword.trim() ? "请尝试其他搜索关键词" : "还没有任何乐队信息，开始创建第一个乐队吧"}
      />
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