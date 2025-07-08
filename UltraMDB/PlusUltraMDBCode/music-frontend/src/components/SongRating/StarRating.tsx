import React, { useState } from 'react';

interface StarRatingProps {
  rating: number;
  onRatingChange?: (rating: number) => void;
  disabled?: boolean;
  interactive?: boolean;
  size?: 'small' | 'medium' | 'large';
  showNumber?: boolean;
}

const StarRating: React.FC<StarRatingProps> = ({
  rating,
  onRatingChange,
  disabled = false,
  interactive = false,
  size = 'medium',
  showNumber = true
}) => {
  const [hoverRating, setHoverRating] = useState(0);

  const getSizeClass = () => {
    switch (size) {
      case 'small': return 'star-small';
      case 'large': return 'star-large';
      default: return 'star-medium';
    }
  };

  const handleStarClick = (starRating: number) => {
    if (!disabled && interactive && onRatingChange) {
      onRatingChange(starRating);
    }
  };

  const handleStarHover = (starRating: number) => {
    if (!disabled && interactive) {
      setHoverRating(starRating);
    }
  };

  const handleMouseLeave = () => {
    if (!disabled && interactive) {
      setHoverRating(0);
    }
  };

  const displayRating = hoverRating || rating;

  return (
    <div 
      className={`star-rating ${getSizeClass()} ${interactive ? 'interactive' : ''} ${disabled ? 'disabled' : ''}`}
      onMouseLeave={handleMouseLeave}
    >
      <div className="stars">
        {[1, 2, 3, 4, 5].map((star) => (
          <span
            key={star}
            className={`star ${star <= displayRating ? 'filled' : ''} ${hoverRating >= star ? 'hover' : ''}`}
            onClick={() => handleStarClick(star)}
            onMouseEnter={() => handleStarHover(star)}
            style={{ cursor: interactive && !disabled ? 'pointer' : 'default' }}
          >
            ⭐
          </span>
        ))}
      </div>
      {showNumber && rating > 0 && (
        <span className="rating-number">
          {rating.toFixed(1)}
        </span>
      )}
    </div>
  );
};

export default StarRating;