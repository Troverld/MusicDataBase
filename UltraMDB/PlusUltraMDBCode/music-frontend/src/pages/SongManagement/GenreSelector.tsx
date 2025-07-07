import React, { useState, useEffect, useRef } from 'react';
import { useGenres } from '../../hooks/useGenres';

interface GenreSelectorProps {
  selectedGenresSet: Set<string>;
  onGenresChange: (genresSet: Set<string>) => void;
}

const GenreSelector: React.FC<GenreSelectorProps> = ({
  selectedGenresSet,
  onGenresChange
}) => {
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const { genres } = useGenres();
  const dropdownRef = useRef<HTMLDivElement>(null);

  // 点击外部关闭下拉框
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false);
      }
    };

    if (dropdownOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [dropdownOpen]);

  // 切换曲风选中状态
  const handleGenreToggle = (genreId: string) => {
    if (!genreId) return;
    
    onGenresChange(prevSet => {
      const newSet = new Set(prevSet);
      const wasSelected = newSet.has(genreId);
      
      if (wasSelected) {
        newSet.delete(genreId);
      } else {
        newSet.add(genreId);
      }
      
      return newSet;
    });
  };

  // 移除曲风
  const handleGenreRemove = (genreId: string) => {
    onGenresChange(prevSet => {
      const newSet = new Set(prevSet);
      newSet.delete(genreId);
      return newSet;
    });
  };

  // 清空所有选中的曲风
  const handleClearAllGenres = () => {
    onGenresChange(new Set());
  };

  // 切换下拉框显示状态
  const toggleDropdown = () => {
    setDropdownOpen(!dropdownOpen);
  };

  // 获取选中曲风的显示信息
  const getSelectedGenresList = () => {
    return Array.from(selectedGenresSet).map(id => {
      const genre = genres.find(g => g.genreID === id);
      return { id, name: genre ? genre.name : id };
    });
  };

  const selectedGenresList = getSelectedGenresList();

  return (
    <div className="form-group">
      <label>
        曲风选择
        {selectedGenresSet.size > 0 && (
          <span className="multi-select-counter">
            {selectedGenresSet.size}
          </span>
        )}
      </label>
      <div className="multi-select-dropdown" ref={dropdownRef}>
        <div 
          className={`multi-select-trigger ${dropdownOpen ? 'open' : ''}`}
          onClick={toggleDropdown}
          tabIndex={0}
        >
          {selectedGenresSet.size === 0 ? (
            <span className="multi-select-placeholder">请选择曲风...</span>
          ) : (
            <div className="multi-select-values">
              {selectedGenresList.slice(0, 5).map(({ id, name }) => (
                <span key={id} className="multi-select-tag">
                  <span className="multi-select-tag-text" title={name}>
                    {name}
                  </span>
                  <span 
                    className="multi-select-tag-remove"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleGenreRemove(id);
                    }}
                    title="移除"
                  >
                    ×
                  </span>
                </span>
              ))}
              {selectedGenresSet.size > 5 && (
                <span className="multi-select-tag" style={{ backgroundColor: '#f8f9fa', color: '#666' }}>
                  +{selectedGenresSet.size - 5}
                </span>
              )}
            </div>
          )}
        </div>
        
        {dropdownOpen && (
          <div className="multi-select-dropdown-menu">
            {selectedGenresSet.size > 0 && (
              <div 
                className="multi-select-option"
                onClick={(e) => {
                  e.stopPropagation();
                  handleClearAllGenres();
                }}
                style={{ 
                  borderBottom: '2px solid #dee2e6',
                  backgroundColor: '#fff3cd',
                  fontWeight: 'bold'
                }}
              >
                <span style={{ fontSize: '14px' }}>🗑️</span>
                <div className="multi-select-option-content">
                  <div className="multi-select-option-name">
                    清空所有选择 ({selectedGenresSet.size} 项)
                  </div>
                </div>
              </div>
            )}
            
            {genres.length === 0 ? (
              <div className="multi-select-empty">
                暂无可用曲风，请先到曲风管理页面添加曲风
              </div>
            ) : (
              genres.map((genre) => {
                const isSelected = selectedGenresSet.has(genre.genreID);
                return (
                  <div 
                    key={genre.genreID} 
                    className={`multi-select-option ${isSelected ? 'selected' : ''}`}
                    onMouseDown={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      handleGenreToggle(genre.genreID);
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => {}}
                      onMouseDown={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                      }}
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                      }}
                      readOnly
                      style={{ pointerEvents: 'none' }}
                    />
                    <div className="multi-select-option-content">
                      <div className="multi-select-option-name">
                        {genre.name}
                        {isSelected && <span style={{ marginLeft: '8px', color: '#007bff', fontWeight: 'bold' }}>✓</span>}
                      </div>
                      {genre.description && (
                        <div className="multi-select-option-description">
                          {genre.description}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>
      
      {/* 已选择曲风的详细信息 */}
      {selectedGenresSet.size > 0 && (
        <div style={{ 
          marginTop: '8px', 
          padding: '8px 12px',
          backgroundColor: '#e8f5e8',
          borderLeft: '4px solid #28a745',
          borderRadius: '0 4px 4px 0',
          fontSize: '12px',
          color: '#155724'
        }}>
          <strong>已选择 {selectedGenresSet.size} 个曲风:</strong> {selectedGenresList.slice(0, 3).map(g => g.name).join(', ')}
          {selectedGenresSet.size > 3 && ` 等共${selectedGenresSet.size}个曲风`}
        </div>
      )}
    </div>
  );
};

export default GenreSelector;