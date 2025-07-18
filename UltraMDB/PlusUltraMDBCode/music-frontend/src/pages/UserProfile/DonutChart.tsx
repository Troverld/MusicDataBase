import React from 'react';
import { SortedGenreData } from './types';
import './DonutChart.css';

interface DonutChartProps {
  data: SortedGenreData[];
  total: number;
  getColor: (index: number) => string;
}

const DonutChart: React.FC<DonutChartProps> = ({ data, total, getColor }) => {
  return (
    <div className="visualization-section">
      <h2>偏好分布</h2>
      
      {/* 环形图 */}
      <div className="donut-chart-container">
        <svg viewBox="0 0 240 240" className="donut-chart">
          {/* 背景圆环 */}
          <circle 
            cx="120" 
            cy="120" 
            r="90" 
            fill="none" 
            stroke="#f3f4f6" 
            strokeWidth="40"
          />
          
          {/* 数据环形 */}
          {data.map((item, index) => {
            const percentage = (item.value / total) * 100;
            const startAngle = data
              .slice(0, index)
              .reduce((sum, d) => sum + (d.value / total) * 360, -90);
            const dashArray = `${(percentage / 100) * 565.5} 565.5`;
            
            return (
              <circle
                key={item.genreID}
                cx="120"
                cy="120"
                r="90"
                fill="none"
                stroke={getColor(index)}
                strokeWidth="40"
                strokeDasharray={dashArray}
                strokeDashoffset={0}
                transform={`rotate(${startAngle} 120 120)`}
                className="donut-segment"
              />
            );
          })}
          
          {/* 中心文字 */}
          <text x="120" y="110" textAnchor="middle" className="center-text">
            <tspan x="120" dy="0" className="center-number">
              {data.length}
            </tspan>
            <tspan x="120" dy="25" className="center-label">
              种曲风
            </tspan>
          </text>
        </svg>
      </div>

      {/* 图例 */}
      <div className="legend-new">
        {data.map((item, index) => (
          <div key={item.genreID} className="legend-item-new">
            <span 
              className="legend-dot" 
              style={{ backgroundColor: getColor(index) }}
            />
            <span className="legend-name">{item.name}</span>
            <span className="legend-percent">
              {((item.value / total) * 100).toFixed(1)}%
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

export default DonutChart;
