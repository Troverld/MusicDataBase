import React, { useState } from 'react';

interface StarRatingProps {
  rating: number;
  onRatingChange?: (rating: number) => void;
  disabled?: boolean;
  interactive?: boolean;
  size?: 'small' | 'medium' | 'large';
  showNumber?: boolean;
  precision?: number; // 交互时的精度，默认为1（整数），可设置为0.5或0.1等
}

const StarRating: React.FC<StarRatingProps> = ({
  rating,
  onRatingChange,
  disabled = false,
  interactive = false,
  size = 'medium',
  showNumber = true,
  precision = 1
}) => {
  const [hoverRating, setHoverRating] = useState(0);

  const getSizeClass = () => {
    switch (size) {
      case 'small': return 'star-small';
      case 'large': return 'star-large';
      default: return 'star-medium';
    }
  };

  // 根据精度四舍五入评分值
  const roundToPrecision = (value: number): number => {
    return Math.round(value / precision) * precision;
  };

  const handleStarClick = (starValue: number) => {
    if (!disabled && interactive && onRatingChange) {
      const roundedRating = roundToPrecision(starValue);
      onRatingChange(Math.max(0, Math.min(5, roundedRating)));
    }
  };

  const handleStarHover = (starValue: number) => {
    if (!disabled && interactive) {
      const roundedRating = roundToPrecision(starValue);
      setHoverRating(Math.max(0, Math.min(5, roundedRating)));
    }
  };

  // 处理鼠标在星星内的精确位置（用于更精细的交互）
  const handleMouseMove = (event: React.MouseEvent<HTMLSpanElement>, starIndex: number) => {
    if (!disabled && interactive && precision < 1) {
      const rect = event.currentTarget.getBoundingClientRect();
      const x = event.clientX - rect.left;
      const percentage = x / rect.width;
      const starValue = starIndex + Math.max(0.1, Math.min(1, percentage));
      const roundedRating = roundToPrecision(starValue);
      setHoverRating(Math.max(0, Math.min(5, roundedRating)));
    }
  };

  const handleMouseLeave = () => {
    if (!disabled && interactive) {
      setHoverRating(0);
    }
  };

  const displayRating = hoverRating || rating;

  // 获取星星的填充百分比 (0-100)
  const getStarFillPercentage = (starIndex: number): number => {
    const starValue = starIndex + 1;
    const remainingRating = displayRating - starIndex;
    
    if (remainingRating <= 0) {
      return 0; // 完全空
    } else if (remainingRating >= 1) {
      return 100; // 完全填充
    } else {
      return Math.round(remainingRating * 100); // 部分填充
    }
  };

  // 生成渐变样式 - 统一使用渐变方式确保所有颜色一致性
  const getStarGradientStyle = (fillPercentage: number) => {
    // 所有星星都使用渐变方式，确保灰色部分颜色完全一致
    const gradientBg = fillPercentage === 0 
      ? `linear-gradient(90deg, #ddd 100%, #ddd 100%)`  // 0%时用渐变确保灰色一致
      : `linear-gradient(90deg, #ffd700 ${fillPercentage}%, #ddd ${fillPercentage}%)`;
    
    return {
      background: gradientBg,
      WebkitBackgroundClip: 'text',
      WebkitTextFillColor: 'transparent',
      backgroundClip: 'text',
      color: 'transparent'
    };
  };

  return (
    <div 
      className={`star-rating ${getSizeClass()} ${interactive ? 'interactive' : ''} ${disabled ? 'disabled' : ''}`}
      onMouseLeave={handleMouseLeave}
    >
      <div className="stars">
        {[0, 1, 2, 3, 4].map((starIndex) => {
          const starValue = starIndex + 1;
          const fillPercentage = getStarFillPercentage(starIndex);
          const isHovered = hoverRating >= starValue;
          
          return (
            <span
              key={starIndex}
              className={`star rational-star ${fillPercentage > 0 ? 'has-fill' : 'empty'} ${isHovered ? 'hover' : ''}`}
              onClick={() => handleStarClick(starValue)}
              onMouseEnter={() => handleStarHover(starValue)}
              onMouseMove={(e) => handleMouseMove(e, starIndex)}
              style={{
                cursor: interactive && !disabled ? 'pointer' : 'default',
                ...getStarGradientStyle(fillPercentage)
              }}
              data-fill={Math.round(fillPercentage / 10) * 10} // 降级方案的data属性，四舍五入到最近的10%
              title={interactive ? `评分 ${starValue}` : `${fillPercentage}% 填充`}
            >
              ★
            </span>
          );
        })}
      </div>
      {showNumber && rating > 0 && (
        <span className="rating-number">
          {rating.toFixed(rating % 1 === 0 ? 0 : 1)}
        </span>
      )}
    </div>
  );
};

export default StarRating;