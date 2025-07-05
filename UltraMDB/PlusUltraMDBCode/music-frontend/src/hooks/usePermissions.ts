import { useState, useEffect, useCallback } from 'react';
import { permissionService } from '../services/permission.service';

export interface PermissionState {
  isUser: boolean;
  isAdmin: boolean;
  loading: boolean;
  error: string;
}

export const usePermissions = () => {
  const [permissions, setPermissions] = useState<PermissionState>({
    isUser: false,
    isAdmin: false,
    loading: true,
    error: ''
  });

  // 检查基础权限
  const checkPermissions = useCallback(async () => {
    setPermissions(prev => ({ ...prev, loading: true, error: '' }));

    try {
      // 并行检查用户和管理员权限
      const [userResult, adminResult] = await Promise.allSettled([
        permissionService.validateUser(),
        permissionService.validateAdmin()
      ]);

      const isUser = userResult.status === 'fulfilled' && userResult.value[0];
      const isAdmin = adminResult.status === 'fulfilled' && adminResult.value[0];

      setPermissions({
        isUser,
        isAdmin,
        loading: false,
        error: ''
      });
    } catch (error: any) {
      setPermissions({
        isUser: false,
        isAdmin: false,
        loading: false,
        error: error.message || '权限验证失败'
      });
    }
  }, []);

  // 初始化时检查权限
  useEffect(() => {
    checkPermissions();
  }, [checkPermissions]);

  return {
    ...permissions,
    refreshPermissions: checkPermissions
  };
};

export const useSongPermission = (songID: string) => {
  const [canEdit, setCanEdit] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const checkSongPermission = useCallback(async () => {
    if (!songID) {
      setCanEdit(false);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');

    try {
      const [hasPermission, message] = await permissionService.validateSongOwnership(songID);
      setCanEdit(hasPermission);
      if (!hasPermission && message) {
        setError(message);
      }
    } catch (err: any) {
      setCanEdit(false);
      setError(err.message || '权限验证失败');
    } finally {
      setLoading(false);
    }
  }, [songID]);

  useEffect(() => {
    checkSongPermission();
  }, [checkSongPermission]);

  return { canEdit, loading, error, refreshPermission: checkSongPermission };
};

export const useArtistPermission = (artistID: string) => {
  const [canEdit, setCanEdit] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const checkArtistPermission = useCallback(async () => {
    if (!artistID) {
      setCanEdit(false);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');

    try {
      const [hasPermission, message] = await permissionService.validateArtistOwnership(artistID);
      setCanEdit(hasPermission);
      if (!hasPermission && message) {
        setError(message);
      }
    } catch (err: any) {
      setCanEdit(false);
      setError(err.message || '权限验证失败');
    } finally {
      setLoading(false);
    }
  }, [artistID]);

  useEffect(() => {
    checkArtistPermission();
  }, [checkArtistPermission]);

  return { canEdit, loading, error, refreshPermission: checkArtistPermission };
};

export const useBandPermission = (bandID: string) => {
  const [canEdit, setCanEdit] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const checkBandPermission = useCallback(async () => {
    if (!bandID) {
      setCanEdit(false);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');

    try {
      const [hasPermission, message] = await permissionService.validateBandOwnership(bandID);
      setCanEdit(hasPermission);
      if (!hasPermission && message) {
        setError(message);
      }
    } catch (err: any) {
      setCanEdit(false);
      setError(err.message || '权限验证失败');
    } finally {
      setLoading(false);
    }
  }, [bandID]);

  useEffect(() => {
    checkBandPermission();
  }, [checkBandPermission]);

  return { canEdit, loading, error, refreshPermission: checkBandPermission };
};