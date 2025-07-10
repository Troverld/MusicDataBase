import React, { useState } from 'react';
import './ModernSearchBox.css';

interface ModernSearchBoxProps {
  searchKeyword: string;
  onSearchKeywordChange: (keyword: string) => void;
  onSearch: () => void;
  loading: boolean;
  placeholder?: string;
  className?: string;
}

const ModernSearchBox: React.FC<ModernSearchBoxProps> = ({
  searchKeyword,
  onSearchKeywordChange,
  onSearch,
  loading,
  placeholder = "搜索...",
  className = ""
}) => {
  const [isFocused, setIsFocused] = useState(false);

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      onSearch();
    }
  };

  const handleClear = () => {
    onSearchKeywordChange('');
  };

  return (
    <div className={`modern-search-container ${className}`}>
      <div className={`modern-search-box ${isFocused ? 'focused' : ''} ${loading ? 'loading' : ''}`}>
        <div className="search-input-wrapper">
          <svg 
            className="search-icon" 
            width="20" 
            height="20" 
            viewBox="0 0 24 24" 
            fill="none" 
            stroke="currentColor" 
            strokeWidth="2"
          >
            <circle cx="11" cy="11" r="8"></circle>
            <path d="m21 21-4.35-4.35"></path>
          </svg>
          
          <input
            type="text"
            className="modern-search-input"
            placeholder={placeholder}
            value={searchKeyword}
            onChange={(e) => onSearchKeywordChange(e.target.value)}
            onKeyPress={handleKeyPress}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            disabled={loading}
          />
          
          {searchKeyword && !loading && (
            <button
              type="button"
              className="clear-button"
              onClick={handleClear}
              aria-label="清除搜索"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          )}
          
          {loading && (
            <div className="loading-spinner">
              <svg width="16" height="16" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" fill="none" strokeDasharray="60" strokeDashoffset="60">
                  <animate attributeName="stroke-dashoffset" dur="2s" values="60;0" repeatCount="indefinite"/>
                </circle>
              </svg>
            </div>
          )}
        </div>
        
        <button
          type="button"
          className={`modern-search-button ${loading ? 'loading' : ''}`}
          onClick={onSearch}
          disabled={loading}
          aria-label="搜索"
        >
          {loading ? (
            <div className="button-loading-spinner">
              <svg width="18" height="18" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" fill="none" strokeDasharray="60" strokeDashoffset="60">
                  <animate attributeName="stroke-dashoffset" dur="1.5s" values="60;0" repeatCount="indefinite"/>
                </circle>
              </svg>
            </div>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8"></circle>
              <path d="m21 21-4.35-4.35"></path>
            </svg>
          )}
          <span className="button-text">{loading ? '搜索中' : '搜索'}</span>
        </button>
      </div>
    </div>
  );
};

export default ModernSearchBox;