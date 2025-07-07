import React, { useState, useEffect, useRef } from 'react';
import { useArtistBand, ArtistBandItem } from '../hooks/useArtistBand';

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
  const [searchTerm, setSearchTerm] = useState('');
  const [searchResults, setSearchResults] = useState<ArtistBandItem[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [localError, setLocalError] = useState('');
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
        // 将成员ID转换为名称
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
          // 过滤掉已经选中的项目
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

  // 截取 bio 前缀
  const truncateBio = (bio: string, maxLength: number = 100) => {
    if (bio.length <= maxLength) return bio;
    return bio.substring(0, maxLength) + '...';
  };

  // 获取类型图标
  const getTypeIcon = (type: 'artist' | 'band') => {
    return type === 'artist' ? '🎤' : '🎸';
  };

  // 获取类型文本
  const getTypeText = (type: 'artist' | 'band') => {
    return type === 'artist' ? '艺术家' : '乐队';
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
        {showDropdown && (
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
                <div
                  key={`${item.type}-${item.id}`}
                  className="multi-select-option"
                  onClick={() => handleAddItem(item)}
                  style={{ cursor: 'pointer' }}
                >
                  <div style={{ fontSize: '16px', marginRight: '8px' }}>
                    {getTypeIcon(item.type)}
                  </div>
                  <div className="multi-select-option-content">
                    <div className="multi-select-option-name">
                      {item.name}
                      <span style={{ 
                        marginLeft: '8px', 
                        fontSize: '11px', 
                        color: '#666',
                        backgroundColor: '#f0f0f0',
                        padding: '2px 6px',
                        borderRadius: '10px'
                      }}>
                        {getTypeText(item.type)}
                      </span>
                    </div>
                    <div className="multi-select-option-description">
                      {truncateBio(item.bio, 80)}
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {/* 已选择的项目 */}
      {selectedItems.length > 0 && (
        <div style={{ marginTop: '10px' }}>
          <div style={{ 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center',
            marginBottom: '10px'
          }}>
            <strong>已选择的{searchType === 'artist' ? '艺术家' : searchType === 'band' ? '乐队' : '艺术家/乐队'}:</strong>
            <button
              type="button"
              onClick={handleClearAll}
              style={{
                background: 'none',
                border: 'none',
                color: '#dc3545',
                cursor: 'pointer',
                fontSize: '12px',
                textDecoration: 'underline'
              }}
            >
              清空全部
            </button>
          </div>
          
          <div style={{ 
            border: '1px solid #ddd', 
            borderRadius: '4px',
            maxHeight: '300px',
            overflowY: 'auto'
          }}>
            {selectedItems.map((item, index) => (
              <div
                key={`${item.type}-${item.id}`}
                style={{
                  padding: '12px',
                  borderBottom: index < selectedItems.length - 1 ? '1px solid #f0f0f0' : 'none',
                  backgroundColor: index % 2 === 0 ? '#f9f9f9' : 'white'
                }}
              >
                <div style={{ 
                  display: 'flex', 
                  justifyContent: 'space-between', 
                  alignItems: 'flex-start',
                  marginBottom: '5px'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '14px' }}>{getTypeIcon(item.type)}</span>
                    <strong>{item.name}</strong>
                    <span style={{ 
                      fontSize: '11px', 
                      color: '#666',
                      backgroundColor: '#e9ecef',
                      padding: '2px 6px',
                      borderRadius: '10px'
                    }}>
                      {getTypeText(item.type)}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleRemoveItem(item)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: '#dc3545',
                      cursor: 'pointer',
                      fontSize: '16px',
                      fontWeight: 'bold',
                      width: '24px',
                      height: '24px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      borderRadius: '50%'
                    }}
                    title="移除"
                  >
                    ×
                  </button>
                </div>
                <div style={{ 
                  fontSize: '12px', 
                  color: '#666',
                  lineHeight: '1.4',
                  marginLeft: '22px'
                }}>
                  {truncateBio(item.bio, 150)}
                </div>
                {item.type === 'band' && item.members && item.members.length > 0 && (
                  <div style={{ 
                    fontSize: '11px', 
                    color: '#999',
                    marginLeft: '22px',
                    marginTop: '5px'
                  }}>
                    成员: {item.members.map(memberId => memberNamesMap[memberId] || memberId).join(', ')}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default ArtistBandSelector;