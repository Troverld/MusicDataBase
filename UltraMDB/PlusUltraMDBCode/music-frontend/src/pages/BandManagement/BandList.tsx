import React from 'react';
import { Band } from '../../types';
import ModernBandCard from '../../components/BandCard/ModernBandCard';
import ModernEmptyState from '../../components/ModernEmptyState';

// 新增接口定义
interface BandMemberDetails {
  id: string;
  name: string;
}

interface BandListProps {
  bands: Band[];
  loading: boolean;
  memberNamesDisplay: { [bandID: string]: string[] };
  memberDetailsDisplay?: { [bandID: string]: BandMemberDetails[] }; // 新增
  onEdit: (band: Band) => void;
  onDelete: (bandID: string) => void;
  searchKeyword: string;
}

const BandList: React.FC<BandListProps> = ({
  bands,
  loading,
  memberNamesDisplay,
  memberDetailsDisplay,
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
        <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
        <polyline points="10 17 15 12 10 7"/>
        <line x1="15" y1="12" x2="3" y2="12"/>
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
    <div className="band-cards-grid">
      {bands.map((band) => (
        <ModernBandCard
          key={band.bandID}
          band={band}
          memberNames={memberNamesDisplay[band.bandID] || []}
          memberDetails={memberDetailsDisplay?.[band.bandID]} // 传递成员详情
          onEdit={onEdit}
          onDelete={onDelete}
        />
      ))}
    </div>
  );
};

export default BandList;