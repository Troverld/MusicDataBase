import React, { useState, useEffect } from 'react';
import { statisticsService } from '../../services/statistics.service';
import { SongRating } from '../../types';
import StarRating from './StarRating';
import RatingDisplay from './RatingDisplay';

interface SongRatingProps {
  songID: string;
  showUserRating?: boolean;
  showAverageRating?: boolean;
  showPopularity?: boolean;
  compact?: boolean;
  onRatingChange?: (rating: number) => void;
}

const SongRatingComponent: React.FC<SongRatingProps> = ({
  songID,
  showUserRating = true,
  showAverageRating = true,
  showPopularity = false,
  compact = false,
  onRatingChange
}) => {
  const [ratingInfo, setRatingInfo] = useState<SongRating>({
    userRating: 0,
    averageRating: 0,
    ratingCount: 0,
    popularity: 0
  });
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // 加载评分信息
  const loadRatingInfo = async () => {
    setLoading(true);
    setError('');
    
    try {
      const rating = await statisticsService.getSongRatingInfo(songID);
      setRatingInfo(rating);
    } catch (err: any) {
      setError(err.message || '获取评分信息失败');
    } finally {
      setLoading(false);
    }
  };

  // 提交用户评分
  const handleRatingSubmit = async (rating: number) => {
    setSubmitting(true);
    setError('');
    setSuccess('');

    try {
      const [success, message] = await statisticsService.rateSong(songID, rating);
      
      if (success) {
        setSuccess('评分成功！');
        setRatingInfo(prev => ({ ...prev, userRating: rating }));
        
        // 重新加载平均评分
        setTimeout(() => {
          loadRatingInfo();
        }, 500);
        
        // 调用回调函数
        if (onRatingChange) {
          onRatingChange(rating);
        }
      } else {
        setError(message || '评分失败');
      }
    } catch (err: any) {
      setError(err.message || '评分失败');
    } finally {
      setSubmitting(false);
    }
  };

  useEffect(() => {
    if (songID) {
      loadRatingInfo();
    }
  }, [songID]);

  // 清除成功消息
  useEffect(() => {
    if (success) {
      const timer = setTimeout(() => setSuccess(''), 3000);
      return () => clearTimeout(timer);
    }
  }, [success]);

  if (loading) {
    return (
      <div className={`song-rating ${compact ? 'compact' : ''}`}>
        <div className="loading-spinner"></div>
        {!compact && <span>加载评分信息...</span>}
      </div>
    );
  }

  return (
    <div className={`song-rating ${compact ? 'compact' : ''}`}>
      {error && (
        <div className="rating-error">
          {error}
        </div>
      )}
      
      {success && (
        <div className="rating-success">
          {success}
        </div>
      )}

      {showUserRating && (
        <div className="user-rating-section">
          {!compact && <label>您的评分：</label>}
          <StarRating
            rating={ratingInfo.userRating}
            onRatingChange={handleRatingSubmit}
            disabled={submitting}
            interactive={true}
            size={compact ? 'small' : 'medium'}
          />
          {submitting && <span className="submitting-text">提交中...</span>}
        </div>
      )}

      {showAverageRating && (
        <RatingDisplay
          averageRating={ratingInfo.averageRating}
          ratingCount={ratingInfo.ratingCount}
          popularity={showPopularity ? ratingInfo.popularity : undefined}
          compact={compact}
        />
      )}
    </div>
  );
};

export default SongRatingComponent;