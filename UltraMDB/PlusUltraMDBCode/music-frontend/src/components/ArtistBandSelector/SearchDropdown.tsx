import React from 'react';
import { ArtistBandItem } from '../../hooks/useArtistBand';
import SearchResultItem from './SearchResultItem';

interface SearchDropdownProps {
  searchResults: ArtistBandItem[];
  searchLoading: boolean;
  searchTerm: string;
  showDropdown: boolean;
  onItemSelect: (item: ArtistBandItem) => void;
  dropdownRef: React.RefObject<HTMLDivElement>;
}

const SearchDropdown: React.FC<SearchDropdownProps> = ({
  searchResults,
  searchLoading,
  searchTerm,
  showDropdown,
  onItemSelect,
  dropdownRef
}) => {
  if (!showDropdown) return null;

  return (
    <div className="multi-select-dropdown-menu" style={{ position: 'relative', marginTop: '0' }}>
      {searchLoading ? (
        <div className="multi-select-empty">
          正在搜索...
        </div>
      ) : searchResults.length === 0 ? (
        <div className="multi-select-empty">
          {searchTerm.trim() ? '未找到匹配的结果' : '请输入搜索关键词'}
        </div>
      ) : (
        searchResults.map((item) => (
          <SearchResultItem
            key={`${item.type}-${item.id}`}
            item={item}
            onSelect={onItemSelect}
          />
        ))
      )}
    </div>
  );
};

export default SearchDropdown;