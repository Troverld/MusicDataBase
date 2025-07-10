import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authService } from '../services/auth.service';
import './Auth.css';

const Login: React.FC = () => {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    userName: '',
    password: ''
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const result = await authService.login(formData);
      
      if (result[0]) {
        // 登录成功，result[0] 包含 [userID, userToken]
        navigate('/');
      } else {
        // 登录失败，result[1] 包含错误信息
        setError(result[1] || '登录失败，请检查您的用户名和密码');
      }
    } catch (err: any) {
      setError(err.message || '登录失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>Music System</h1>
            <p>探索音乐的无限可能</p>
          </div>
          
          <h2 className="auth-title">欢迎回来</h2>
          
          {error && <div className="auth-message auth-error">{error}</div>}
          
          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="auth-form-group">
              <label htmlFor="userName">用户名</label>
              <input
                type="text"
                id="userName"
                name="userName"
                value={formData.userName}
                onChange={handleChange}
                placeholder="请输入用户名"
                required
                autoComplete="username"
              />
            </div>
            
            <div className="auth-form-group">
              <label htmlFor="password">密码</label>
              <input
                type="password"
                id="password"
                name="password"
                value={formData.password}
                onChange={handleChange}
                placeholder="请输入密码"
                required
                autoComplete="current-password"
              />
            </div>
            
            <button 
              type="submit" 
              className="auth-submit-btn"
              disabled={loading}
            >
              {loading ? (
                <span className="auth-loading">
                  <span className="auth-loading-spinner"></span>
                  登录中...
                </span>
              ) : (
                '登录'
              )}
            </button>
          </form>
          
          <div className="auth-footer">
            还没有账号？
            <Link to="/register">立即注册</Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;