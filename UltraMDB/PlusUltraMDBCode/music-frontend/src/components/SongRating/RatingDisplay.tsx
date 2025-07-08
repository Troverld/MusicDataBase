import React from 'react';
import StarRating from './StarRating';

interface RatingDisplayProps {
  averageRating: number;
  ratingCount: number;
  popularity?: number;
  compact?: boolean;
}

const RatingDisplay: React.FC<RatingDisplayProps> = ({
  averageRating,
  ratingCount,
  popularity,
  compact = false
}) => {
  const formatPopularity = (pop: number): string => {
    if (pop >= 1000000) {
      return `${(pop / 1000000).toFixed(1)}M`;
    } else if (pop >= 1000) {
      return `${(pop / 1000).toFixed(1)}K`;
    } else {
      return pop.toFixed(0);
    }
  };

  return (
    <div className={`rating-display ${compact ? 'compact' : ''}`}>
      <div className="average-rating">
        {!compact && <label>平均评分：</label>}
        <StarRating
          rating={averageRating}
          interactive={false}
          size={compact ? 'small' : 'medium'}
          showNumber={true}
        />
        <span className="rating-count">
          ({ratingCount} {compact ? '评' : '个评分'})
        </span>
      </div>
      
      {popularity !== undefined && popularity > 0 && (
        <div className="popularity">
          {!compact && <label>热度：</label>}
          <span className="popularity-value">
            🔥 {formatPopularity(popularity)}
          </span>
        </div>
      )}
    </div>
  );
};

export default RatingDisplay;