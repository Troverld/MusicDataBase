import { useState, useEffect, useCallback } from 'react';
import { useLocation } from 'react-router-dom';
import { bandService } from '../../services/band.service';
import { Band } from '../../types';
import { useArtistBand } from '../../hooks/useArtistBand';
import { BandMemberDetails } from './types';

export const useBandManagement = () => {
  const location = useLocation();
  const [bands, setBands] = useState<Band[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingBand, setEditingBand] = useState<Band | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [memberNamesDisplay, setMemberNamesDisplay] = useState<{ [bandID: string]: string[] }>({});
  const [memberDetailsDisplay, setMemberDetailsDisplay] = useState<{ [bandID: string]: BandMemberDetails[] }>({});
  
  const { convertIdsToNames, convertIdsToArtistBandItems } = useArtistBand();

  // 加载乐队成员名称和详情
  const loadMemberNames = async (bandList: Band[]) => {
    const memberNamesMap: { [bandID: string]: string[] } = {};
    const memberDetailsMap: { [bandID: string]: BandMemberDetails[] } = {};
    
    for (const band of bandList) {
      try {
        // 保持原有逻辑：获取成员名称
        const memberNames = await convertIdsToNames(band.members || []);
        memberNamesMap[band.bandID] = memberNames;
        
        // 新增：获取成员详情
        const memberItems = await convertIdsToArtistBandItems(band.members || []);
        memberDetailsMap[band.bandID] = memberItems.map(item => ({
          id: item.id,
          name: item.name
        }));
      } catch (error) {
        console.error(`Failed to load member names for band ${band.bandID}:`, error);
        memberNamesMap[band.bandID] = band.members || [];
        memberDetailsMap[band.bandID] = [];
      }
    }
    
    setMemberNamesDisplay(memberNamesMap);
    setMemberDetailsDisplay(memberDetailsMap);
  };

  // 获取所有乐队
  const fetchAllBands = async () => {
    setLoading(true);
    setError('');
    
    try {
      // 使用 `%` 字符串搜索来获取所有乐队
      const [bandIDs, message] = await bandService.searchBandByName('%');
      if (bandIDs && bandIDs.length > 0) {
        const bandDetails = await bandService.getBandsByIds(bandIDs);
        setBands(bandDetails);
        await loadMemberNames(bandDetails);
      } else {
        setBands([]);
        setMemberNamesDisplay({});
        setMemberDetailsDisplay({});
      }
    } catch (err: any) {
      setError(err.message || '获取乐队列表失败');
      setBands([]);
      setMemberNamesDisplay({});
      setMemberDetailsDisplay({});
    } finally {
      setLoading(false);
    }
  };

  // 搜索处理
  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      // 如果搜索框为空，显示所有乐队
      await fetchAllBands();
      return;
    }
    
    setLoading(true);
    setError('');
    
    try {
      const [bandIDs, message] = await bandService.searchBandByName(searchKeyword);
      if (bandIDs && bandIDs.length > 0) {
        const bandDetails = await bandService.getBandsByIds(bandIDs);
        setBands(bandDetails);
        await loadMemberNames(bandDetails);
        
        if (bandDetails.length === 0) {
          setError('未找到匹配的乐队详情');
        }
      } else {
        setBands([]);
        setMemberNamesDisplay({});
        setMemberDetailsDisplay({});
        setError(message || '未找到匹配的乐队');
      }
    } catch (err: any) {
      setError(err.message || '搜索失败');
      setBands([]);
      setMemberNamesDisplay({});
      setMemberDetailsDisplay({});
    } finally {
      setLoading(false);
    }
  };

  // 删除处理
  const handleDelete = async (bandID: string) => {
    if (!window.confirm('确定要删除这个乐队吗？此操作不可撤销，可能会影响相关的歌曲和专辑。')) return;
    
    try {
      const [success, message] = await bandService.deleteBand(bandID);
      if (success) {
        // 如果当前有搜索关键词，重新搜索；否则重新获取所有乐队
        if (searchKeyword.trim()) {
          await handleSearch();
        } else {
          await fetchAllBands();
        }
        setSuccess('乐队删除成功');
      } else {
        setError(message);
      }
    } catch (err: any) {
      setError(err.message);
    }
  };

  // 编辑处理
  const handleEdit = (band: Band) => {
    setEditingBand(band);
    setShowModal(true);
  };

  // 表单成功处理
  const handleFormSuccess = async (message: string) => {
    setSuccess(message);
    setShowModal(false);
    setEditingBand(null);
    
    // 刷新乐队列表
    if (searchKeyword.trim()) {
      await handleSearch();
    } else {
      await fetchAllBands();
    }
  };

  // 表单错误处理
  const handleFormError = (message: string) => {
    setError(message);
  };

  // 清除消息
  const clearMessages = useCallback(() => {
    setError('');
    setSuccess('');
  }, []);

  // 重置表单
  const resetForm = useCallback(() => {
    setEditingBand(null);
  }, []);

  // 页面加载时获取所有乐队
  useEffect(() => {
    fetchAllBands();
  }, []);

  // 检查是否从其他页面传递了编辑乐队的数据
  useEffect(() => {
    if (location.state?.editBand) {
      const bandToEdit = location.state.editBand as Band;
      setEditingBand(bandToEdit);
      setShowModal(true);
      // 清除 location state 以避免重复处理
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  // 搜索关键词变化时清除消息
  useEffect(() => {
    clearMessages();
  }, [searchKeyword, clearMessages]);

  return {
    // 状态
    bands,
    searchKeyword,
    showModal,
    editingBand,
    error,
    success,
    loading,
    memberNamesDisplay,
    memberDetailsDisplay,
    
    // 方法
    setSearchKeyword,
    setShowModal,
    handleSearch,
    handleDelete,
    handleEdit,
    handleFormSuccess,
    handleFormError,
    clearMessages,
    resetForm
  };
};