import React from 'react';
import { UserCharacteristics } from './types';
import './CharacteristicsCards.css';

interface CharacteristicsCardsProps {
  characteristics: UserCharacteristics;
}

const CharacteristicsCards: React.FC<CharacteristicsCardsProps> = ({ characteristics }) => {
  return (
    <div className="characteristics-section">
      <div className="characteristic-cards">
        <div className="char-card">
          <div className="char-icon">🎯</div>
          <div className="char-info">
            <h3>主要风格</h3>
            <p>{characteristics.mainStyle}</p>
          </div>
        </div>
        
        <div className="char-card">
          <div className="char-icon">📊</div>
          <div className="char-info">
            <h3>偏好集中度</h3>
            <p>{characteristics.concentration}</p>
          </div>
        </div>
        
        <div className="char-card">
          <div className="char-icon">🌈</div>
          <div className="char-info">
            <h3>音乐多样性</h3>
            <p>{characteristics.diversity}</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CharacteristicsCards;
