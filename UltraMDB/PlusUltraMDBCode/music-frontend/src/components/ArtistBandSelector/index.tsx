import React, { useState, useEffect, useRef } from 'react';
import { useArtistBand, ArtistBandItem } from '../../hooks/useArtistBand';
import SearchDropdown from './SearchDropdown';
import SelectedItemsList from './SelectedItemsList';

interface ArtistBandSelectorProps {
  selectedItems: ArtistBandItem[];
  onSelectionChange: (items: ArtistBandItem[]) => void;
  searchType?: 'artist' | 'band' | 'both';
  placeholder?: string;
  label?: string;
  disabled?: boolean;
  maxSelections?: number;
}

const ArtistBandSelector: React.FC<ArtistBandSelectorProps> = ({
  selectedItems,
  onSelectionChange,
  searchType = 'both',
  placeholder = '搜索艺术家或乐队...',
  label = '艺术家/乐队选择',
  disabled = false,
  maxSelections
}) => {
  // 搜索相关状态
  const [searchTerm, setSearchTerm] = useState('');
  const [searchResults, setSearchResults] = useState<ArtistBandItem[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [localError, setLocalError] = useState('');
  
  // 成员名称映射状态
  const [memberNamesMap, setMemberNamesMap] = useState<{ [key: string]: string }>({});
  
  const { searchArtistBand, loading, error, convertIdsToArtistBandItems } = useArtistBand();
  const dropdownRef = useRef<HTMLDivElement>(null);
  const searchTimeoutRef = useRef<NodeJS.Timeout>();

  // 加载乐队成员名称
  useEffect(() => {
    const loadMemberNames = async () => {
      const bandItems = selectedItems.filter(item => item.type === 'band' && item.members && item.members.length > 0);
      
      if (bandItems.length === 0) return;
      
      const allMemberIds = new Set<string>();
      bandItems.forEach(band => {
        band.members?.forEach(memberId => allMemberIds.add(memberId));
      });
      
      if (allMemberIds.size === 0) return;
      
      try {
        const memberIds = Array.from(allMemberIds);
        const memberItems = await convertIdsToArtistBandItems(memberIds);
        
        const namesMap: { [key: string]: string } = {};
        memberItems.forEach(item => {
          namesMap[item.id] = item.name;
        });
        
        setMemberNamesMap(namesMap);
      } catch (error) {
        console.error('Failed to load member names:', error);
      }
    };
    
    loadMemberNames();
  }, [selectedItems, convertIdsToArtistBandItems]);

  // 点击外部关闭下拉框
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowDropdown(false);
      }
    };

    if (showDropdown) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [showDropdown]);

  // 搜索去抖动
  useEffect(() => {
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current);
    }

    if (searchTerm.trim()) {
      setSearchLoading(true);
      searchTimeoutRef.current = setTimeout(async () => {
        try {
          const results = await searchArtistBand(searchTerm, searchType);
          const filteredResults = results.filter(
            result => !selectedItems.some(selected => 
              selected.id === result.id && selected.type === result.type
            )
          );
          setSearchResults(filteredResults);
          setShowDropdown(true);
        } catch (err: any) {
          setLocalError(err.message || '搜索失败');
        } finally {
          setSearchLoading(false);
        }
      }, 300);
    } else {
      setSearchResults([]);
      setShowDropdown(false);
    }

    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current);
      }
    };
  }, [searchTerm, searchArtistBand, searchType, selectedItems]);

  // 添加项目到选中列表
  const handleAddItem = (item: ArtistBandItem) => {
    if (maxSelections && selectedItems.length >= maxSelections) {
      setLocalError(`最多只能选择 ${maxSelections} 个项目`);
      return;
    }

    const newSelection = [...selectedItems, item];
    onSelectionChange(newSelection);
    setSearchTerm('');
    setShowDropdown(false);
    setLocalError('');
  };

  // 移除项目
  const handleRemoveItem = (itemToRemove: ArtistBandItem) => {
    const newSelection = selectedItems.filter(item => 
      !(item.id === itemToRemove.id && item.type === itemToRemove.type)
    );
    onSelectionChange(newSelection);
  };

  // 清空所有选择
  const handleClearAll = () => {
    onSelectionChange([]);
    setSearchTerm('');
    setShowDropdown(false);
  };

  return (
    <div className="form-group">
      <label>
        {label}
        {selectedItems.length > 0 && (
          <span className="multi-select-counter">
            {selectedItems.length}
            {maxSelections && `/${maxSelections}`}
          </span>
        )}
      </label>
      
      {/* 错误信息 */}
      {(error || localError) && (
        <div className="error-message" style={{ marginBottom: '10px' }}>
          {error || localError}
        </div>
      )}
      
      {/* 搜索框 */}
      <div className="multi-select-dropdown" ref={dropdownRef}>
        <div className="form-group" style={{ marginBottom: '10px' }}>
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder={placeholder}
            disabled={disabled || (maxSelections ? selectedItems.length >= maxSelections : false)}
            style={{
              width: '100%',
              padding: '10px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              fontSize: '14px'
            }}
          />
        </div>
        
        {/* 搜索结果下拉框 */}
        <SearchDropdown
          searchResults={searchResults}
          searchLoading={searchLoading}
          searchTerm={searchTerm}
          showDropdown={showDropdown}
          onItemSelect={handleAddItem}
          dropdownRef={dropdownRef}
        />
      </div>

      {/* 已选择的项目 */}
      <SelectedItemsList
        selectedItems={selectedItems}
        searchType={searchType}
        memberNamesMap={memberNamesMap}
        onRemoveItem={handleRemoveItem}
        onClearAll={handleClearAll}
      />
    </div>
  );
};

export default ArtistBandSelector;