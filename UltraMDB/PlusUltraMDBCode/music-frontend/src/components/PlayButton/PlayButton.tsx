import React, { useState } from 'react';
import { statisticsService } from '../../services/statistics.service';
import './PlayButton.css';

interface PlayButtonProps {
  songID: string;
  songName: string;
  disabled?: boolean;
  size?: 'small' | 'medium' | 'large';
  onPlayStart?: () => void;
  onPlayError?: (error: string) => void;
}

const PlayButton: React.FC<PlayButtonProps> = ({
  songID,
  songName,
  disabled = false,
  size = 'medium',
  onPlayStart,
  onPlayError
}) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [loading, setLoading] = useState(false);

  const handlePlay = async () => {
    if (disabled || loading || isPlaying) {
      return;
    }

    setLoading(true);
    
    try {
      // 调用 LogPlayback API
      const [success, message] = await statisticsService.logPlayback(songID);
      
      if (success) {
        setIsPlaying(true);
        console.log(`播放记录成功: ${songName} (ID: ${songID})`);
        
        // 显示播放动画2秒
        setTimeout(() => {
          setIsPlaying(false);
        }, 2000);
        
        // 调用成功回调
        if (onPlayStart) {
          onPlayStart();
        }
      } else {
        console.error('Failed to log playback:', message);
        if (onPlayError) {
          onPlayError(message || '播放记录失败');
        }
      }
    } catch (error: any) {
      console.error('Error during playback:', error);
      if (onPlayError) {
        onPlayError(error.message || '播放出错');
      }
    } finally {
      setLoading(false);
    }
  };

  const getButtonIcon = () => {
    if (loading) {
      return '⏳';
    }
    if (isPlaying) {
      return '🎵';
    }
    return '▶️';
  };

  const getButtonText = () => {
    if (loading) {
      return '记录中...';
    }
    if (isPlaying) {
      return '播放中';
    }
    return '播放';
  };

  return (
    <button
      className={`play-button ${size} ${isPlaying ? 'playing' : ''} ${disabled ? 'disabled' : ''}`}
      onClick={handlePlay}
      disabled={disabled || loading || isPlaying}
      title={`播放 ${songName}`}
    >
      <span className="play-icon">{getButtonIcon()}</span>
      <span className="play-text">{getButtonText()}</span>
    </button>
  );
};

export default PlayButton;