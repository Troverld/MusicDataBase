import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authService } from '../services/auth.service';
import './Auth.css';

const Register: React.FC = () => {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    userName: '',
    password: ''
  });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
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
    setSuccess('');
    setLoading(true);

    try {
      const [userID, message] = await authService.register(formData);
      
      if (userID) {
        setSuccess('注册成功！正在跳转到登录页面...');
        setTimeout(() => navigate('/login'), 2000);
      } else {
        setError(message || '注册失败，请稍后重试');
      }
    } catch (err: any) {
      setError(err.message || '注册失败，请稍后重试');
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
            <p>开启您的音乐之旅</p>
          </div>
          
          <h2 className="auth-title">创建账号</h2>
          
          {error && <div className="auth-message auth-error">{error}</div>}
          {success && <div className="auth-message auth-success">{success}</div>}
          
          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="auth-form-group">
              <label htmlFor="userName">用户名</label>
              <input
                type="text"
                id="userName"
                name="userName"
                value={formData.userName}
                onChange={handleChange}
                placeholder="设置您的用户名"
                required
                autoComplete="username"
                minLength={3}
                maxLength={20}
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
                placeholder="设置您的密码"
                required
                autoComplete="new-password"
                minLength={6}
              />
            </div>
            
            <button 
              type="submit" 
              className="auth-submit-btn"
              disabled={loading || !!success}
            >
              {loading ? (
                <span className="auth-loading">
                  <span className="auth-loading-spinner"></span>
                  注册中...
                </span>
              ) : success ? (
                '注册成功！'
              ) : (
                '注册'
              )}
            </button>
          </form>
          
          <div className="auth-footer">
            已有账号？
            <Link to="/login">立即登录</Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Register;