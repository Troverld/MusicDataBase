import React, { useState, useEffect } from 'react';
import { bandService } from '../../services/band.service';
import { Band } from '../../types';
import { ArtistBandItem, useArtistBand } from '../../hooks/useArtistBand';
import ArtistBandSelector from '../../components/ArtistBandSelector';

interface BandFormProps {
  editingBand: Band | null;
  memberNamesDisplay: { [bandID: string]: string[] };
  onSuccess: (message: string) => void;
  onError: (message: string) => void;
  onClose: () => void;
}

const BandForm: React.FC<BandFormProps> = ({
  editingBand,
  memberNamesDisplay,
  onSuccess,
  onError,
  onClose
}) => {
  const [formData, setFormData] = useState({
    name: '',
    bio: ''
  });
  const [selectedMembers, setSelectedMembers] = useState<ArtistBandItem[]>([]);
  
  const { convertIdsToArtistBandItems } = useArtistBand();

  // 初始化编辑数据
  useEffect(() => {
    if (editingBand) {
      setFormData({
        name: editingBand.name,
        bio: editingBand.bio
      });
      
      // 将现有成员ID转换为选中项目
      const loadEditingData = async () => {
        try {
          const memberItems = await convertIdsToArtistBandItems(editingBand.members || []);
          setSelectedMembers(memberItems);
          
          // 检查是否有无法找到的项目
          const unresolvedMembers = memberItems.filter(item => 
            item.id.startsWith('unresolved-')
          );
          
          if (unresolvedMembers.length > 0) {
            onError(`警告：有 ${unresolvedMembers.length} 个乐队成员无法准确匹配，可能是旧数据或已删除的成员。建议重新搜索选择所有成员。`);
          }
        } catch (error) {
          console.error('Failed to load member details for editing:', error);
          // 如果转换失败，创建占位符项目
          const placeholderMembers: ArtistBandItem[] = (editingBand.members || []).map((memberId, index) => ({
            id: `placeholder-${memberId}`,
            name: memberNamesDisplay[editingBand.bandID]?.[index] || memberId,
            bio: '编辑模式：请重新搜索选择此成员以确保数据准确性。',
            type: 'artist'
          }));
          setSelectedMembers(placeholderMembers);
          onError('编辑模式：无法获取成员详情，请重新搜索选择所有乐队成员。');
        }
      };

      loadEditingData();
    }
  }, [editingBand, convertIdsToArtistBandItems, memberNamesDisplay, onError]);

  // 验证选中的成员是否有问题
  const validateSelectedMembers = () => {
    const problemMembers = selectedMembers.filter(member => 
      member.id.startsWith('placeholder-') || 
      member.id.startsWith('unresolved-') || 
      member.id.startsWith('error-') ||
      member.id.startsWith('virtual-')
    );
    
    return problemMembers;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.name.trim() || !formData.bio.trim()) {
      onError('乐队名称和简介都不能为空');
      return;
    }

    // 验证是否有问题的选中成员
    const problemMembers = validateSelectedMembers();
    if (problemMembers.length > 0) {
      onError(`请重新选择以下有问题的乐队成员：${problemMembers.map(member => member.name).join(', ')}`);
      return;
    }

    // 获取成员 ID 列表
    const memberIDs = selectedMembers.map(member => member.id);

    try {
      if (editingBand) {
        // 直接调用后端 API，传递 ID 列表
        const updateData = {
          name: formData.name,
          bio: formData.bio,
          members: memberIDs
        };
        
        const [success, message] = await bandService.updateBandWithIds(editingBand.bandID, updateData);
        if (success) {
          onSuccess('乐队信息更新成功');
        } else {
          onError(message);
        }
      } else {
        // 创建新乐队，直接传递 ID 列表
        const createData = {
          name: formData.name,
          bio: formData.bio,
          memberIDs: memberIDs
        };
        
        const [bandID, message] = await bandService.createBandWithIds(createData);
        if (bandID) {
          onSuccess(`乐队创建成功！乐队ID: ${bandID}`);
        } else {
          onError(message);
        }
      }
    } catch (err: any) {
      onError(err.message);
    }
  };

  return (
    <div className="modal" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>{editingBand ? '编辑乐队' : '创建新乐队'}</h2>
          <button onClick={onClose}>×</button>
        </div>
        
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>乐队名称*</label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => setFormData({...formData, name: e.target.value})}
              placeholder="请输入乐队名称"
              required
            />
          </div>
          
          {/* 使用艺术家选择器 */}
          <ArtistBandSelector
            selectedItems={selectedMembers}
            onSelectionChange={setSelectedMembers}
            searchType="artist"
            label="乐队成员"
            placeholder="搜索艺术家作为乐队成员..."
          />
          
          <div className="form-group">
            <label>乐队简介*</label>
            <textarea
              value={formData.bio}
              onChange={(e) => setFormData({...formData, bio: e.target.value})}
              placeholder="请输入乐队简介、成立背景、风格特色等..."
              required
              rows={6}
              style={{ resize: 'vertical', minHeight: '120px' }}
            />
          </div>
          
          <div style={{ 
            display: 'flex', 
            gap: '10px', 
            justifyContent: 'flex-end',
            marginTop: '20px'
          }}>
            <button 
              type="button" 
              className="btn btn-secondary"
              onClick={onClose}
            >
              取消
            </button>
            <button 
              type="submit" 
              className="btn btn-primary"
              disabled={!formData.name.trim() || !formData.bio.trim()}
            >
              {editingBand ? '更新乐队' : '创建乐队'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default BandForm;