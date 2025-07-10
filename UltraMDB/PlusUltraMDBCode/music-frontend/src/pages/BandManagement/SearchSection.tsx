import React from 'react';
import ModernSearchBox from '../../components/ModernSearchBox';

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
  return (
    <ModernSearchBox
      searchKeyword={searchKeyword}
      onSearchKeywordChange={onSearchKeywordChange}
      onSearch={onSearch}
      loading={loading}
      placeholder="搜索乐队..."
    />
  );
};

export default SearchSection;