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
    if (disabled || loading) {
      return;
    }

    setLoading(true);
    
    try {
      // 调用 LogPlayback API
      const [success, message] = await statisticsService.logPlayback(songID);
      
      if (success) {
        setIsPlaying(true);
        console.log(`Playing: ${songName} (ID: ${songID})`);
        
        // 模拟播放3秒后停止（实际项目中这里会是真实的播放逻辑）
        setTimeout(() => {
          setIsPlaying(false);
        }, 3000);
        
        // 调用成功回调
        if (onPlayStart) {
          onPlayStart();
        }
      } else {
        console.error('Failed to log playback:', message);
        if (onPlayError) {
          onPlayError(message || '播放失败');
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

  const handleStop = () => {
    setIsPlaying(false);
    console.log(`Stopped: ${songName}`);
  };

  const getButtonIcon = () => {
    if (loading) {
      return '⏳';
    }
    return isPlaying ? '⏸️' : '▶️';
  };

  const getButtonText = () => {
    if (loading) {
      return '载入中...';
    }
    return isPlaying ? '暂停' : '播放';
  };

  return (
    <button
      className={`play-button ${size} ${isPlaying ? 'playing' : ''} ${disabled ? 'disabled' : ''}`}
      onClick={isPlaying ? handleStop : handlePlay}
      disabled={disabled || loading}
      title={`${getButtonText()} ${songName}`}
    >
      <span className="play-icon">{getButtonIcon()}</span>
      <span className="play-text">{getButtonText()}</span>
    </button>
  );
};

export default PlayButton;