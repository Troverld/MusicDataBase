import React from 'react';
import './Pagination.css';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  loading?: boolean;
}

const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  onPageChange,
  loading = false
}) => {
  if (totalPages <= 1) {
    return null;
  }

  const renderPageNumbers = () => {
    const pages: (number | string)[] = [];
    const maxVisiblePages = 7;
    
    if (totalPages <= maxVisiblePages) {
      // 如果总页数少于等于最大显示页数，显示所有页面
      for (let i = 1; i <= totalPages; i++) {
        pages.push(i);
      }
    } else {
      // 总是显示第一页
      pages.push(1);
      
      // 计算当前页附近的页面范围
      let startPage = Math.max(2, currentPage - 2);
      let endPage = Math.min(totalPages - 1, currentPage + 2);
      
      // 如果开始页面大于2，添加省略号
      if (startPage > 2) {
        pages.push('...');
      }
      
      // 添加中间页面
      for (let i = startPage; i <= endPage; i++) {
        pages.push(i);
      }
      
      // 如果结束页面小于总页数-1，添加省略号
      if (endPage < totalPages - 1) {
        pages.push('...');
      }
      
      // 总是显示最后一页
      if (totalPages > 1) {
        pages.push(totalPages);
      }
    }
    
    return pages;
  };

  return (
    <div className="pagination-container">
      <div className="pagination-info">
        第 {currentPage} 页，共 {totalPages} 页
      </div>
      
      <div className="pagination-controls">
        {/* 上一页 */}
        <button
          className="pagination-btn"
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage <= 1 || loading}
        >
          上一页
        </button>
        
        {/* 页码 */}
        {renderPageNumbers().map((page, index) => (
          <React.Fragment key={index}>
            {page === '...' ? (
              <span className="pagination-ellipsis">...</span>
            ) : (
              <button
                className={`pagination-btn ${page === currentPage ? 'active' : ''}`}
                onClick={() => onPageChange(page as number)}
                disabled={loading}
              >
                {page}
              </button>
            )}
          </React.Fragment>
        ))}
        
        {/* 下一页 */}
        <button
          className="pagination-btn"
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage >= totalPages || loading}
        >
          下一页
        </button>
      </div>
    </div>
  );
};

export default Pagination;