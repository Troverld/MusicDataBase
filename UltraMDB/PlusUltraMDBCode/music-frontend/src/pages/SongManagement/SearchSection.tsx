import React from 'react';

interface SearchSectionProps {
  searchKeyword: string;
  onSearchKeywordChange: (keyword: string) => void;
  onSearch: () => void;
  loading: boolean;
}

const SearchSection: React.FC<SearchSectionProps> = ({
  searchKeyword,
  onSearchKeywordChange,
  onSearch,
  loading
}) => {
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      onSearch();
    }
  };

  return (
    <div className="search-box">
      <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
        <input
          type="text"
          placeholder="搜索歌曲..."
          value={searchKeyword}
          onChange={(e) => onSearchKeywordChange(e.target.value)}
          onKeyPress={handleKeyPress}
          style={{ flex: 1 }}
        />
        <button 
          className="btn btn-primary" 
          onClick={onSearch}
          disabled={loading}
        >
          {loading ? '搜索中...' : '搜索'}
        </button>
      </div>
    </div>
  );
};

export default SearchSection;