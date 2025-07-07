import React, { useState, useEffect } from 'react';
import { musicService } from '../../services/music.service';
import { Song } from '../../types';
import { useArtistBand, ArtistBandItem } from '../../hooks/useArtistBand';
import ArtistBandSelector from '../../components/ArtistBandSelector';
import GenreSelector from './GenreSelector';

interface SongFormProps {
  editingSong: Song | null;
  onSuccess: (message: string) => void;
  onError: (message: string) => void;
  onClose: () => void;
}

const SongForm: React.FC<SongFormProps> = ({
  editingSong,
  onSuccess,
  onError,
  onClose
}) => {
  // 表单基础数据
  const [formData, setFormData] = useState({
    name: '',
    releaseTime: new Date().toISOString().split('T')[0]
  });

  // 艺术家/乐队选择状态
  const [selectedCreators, setSelectedCreators] = useState<ArtistBandItem[]>([]);
  const [selectedPerformers, setSelectedPerformers] = useState<ArtistBandItem[]>([]);
  const [selectedLyricists, setSelectedLyricists] = useState<ArtistBandItem[]>([]);
  const [selectedComposers, setSelectedComposers] = useState<ArtistBandItem[]>([]);
  const [selectedArrangers, setSelectedArrangers] = useState<ArtistBandItem[]>([]);
  const [selectedInstrumentalists, setSelectedInstrumentalists] = useState<ArtistBandItem[]>([]);

  // 曲风选择状态
  const [selectedGenresSet, setSelectedGenresSet] = useState<Set<string>>(new Set());

  const { 
    convertCreatorsToSelectedItems,
    convertIdsToSelectedItems
  } = useArtistBand();

  // 初始化编辑数据
  useEffect(() => {
    if (editingSong) {
      setFormData({
        name: editingSong.name,
        releaseTime: new Date(editingSong.releaseTime).toISOString().split('T')[0]
      });
      
      // 使用 Set 来管理选中的曲风
      setSelectedGenresSet(new Set(editingSong.genres));
      
      // 转换现有的ID列表为选中项目
      const loadEditingData = async () => {
        try {
          const [creators, performers, lyricists, composers, arrangers, instrumentalists] = await Promise.all([
            convertCreatorsToSelectedItems(editingSong.creators || []),
            convertIdsToSelectedItems(editingSong.performers || []),
            convertIdsToSelectedItems(editingSong.lyricists || []),
            convertIdsToSelectedItems(editingSong.composers || []),
            convertIdsToSelectedItems(editingSong.arrangers || []),
            convertIdsToSelectedItems(editingSong.instrumentalists || [])
          ]);
          
          setSelectedCreators(creators);
          setSelectedPerformers(performers);
          setSelectedLyricists(lyricists);
          setSelectedComposers(composers);
          setSelectedArrangers(arrangers);
          setSelectedInstrumentalists(instrumentalists);
          
          // 检查是否有无法找到的项目
          const allItems = [...creators, ...performers, ...lyricists, ...composers, ...arrangers, ...instrumentalists];
          const notFoundItems = allItems.filter(item => 
            item.id.startsWith('not-found-') || item.id.startsWith('error-')
          );
          
          if (notFoundItems.length > 0) {
            onError(`警告：有 ${notFoundItems.length} 个创作者信息无法准确匹配，可能是已删除的项目。请检查并重新选择相关项目。`);
          }
          
        } catch (error) {
          console.error('Failed to convert song data for editing:', error);
          onError('加载歌曲编辑数据时发生错误，请重新选择所有创作者信息。');
          
          // 如果转换失败，清空所有选择
          setSelectedCreators([]);
          setSelectedPerformers([]);
          setSelectedLyricists([]);
          setSelectedComposers([]);
          setSelectedArrangers([]);
          setSelectedInstrumentalists([]);
        }
      };

      loadEditingData();
    }
  }, [editingSong, convertCreatorsToSelectedItems, convertIdsToSelectedItems, onError]);

  // 验证选中的项目是否有问题
  const validateSelectedItems = () => {
    const allItems = [
      ...selectedCreators,
      ...selectedPerformers, 
      ...selectedLyricists,
      ...selectedComposers,
      ...selectedArrangers,
      ...selectedInstrumentalists
    ];
    
    const problemItems = allItems.filter(item => 
      item.id.startsWith('not-found-') || 
      item.id.startsWith('error-') ||
      item.id.startsWith('virtual-')
    );
    
    return problemItems;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // 验证是否有问题的选中项目
    const problemItems = validateSelectedItems();
    if (problemItems.length > 0) {
      onError(`请重新选择以下有问题的创作者信息：${problemItems.map(item => item.name).join(', ')}`);
      return;
    }

    const songData = {
      name: formData.name,
      releaseTime: new Date(formData.releaseTime).getTime(),
      creators: selectedCreators,
      performers: selectedPerformers,
      lyricists: selectedLyricists,
      composers: selectedComposers,
      arrangers: selectedArrangers,
      instrumentalists: selectedInstrumentalists,
      genres: Array.from(selectedGenresSet)
    };

    try {
      if (editingSong) {
        const [success, message] = await musicService.updateSong(editingSong.songID, songData);
        if (success) {
          onSuccess('歌曲更新成功');
        } else {
          onError(message);
        }
      } else {
        const [songID, message] = await musicService.uploadSong(songData);
        if (songID) {
          onSuccess('歌曲上传成功');
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
          <h2>{editingSong ? '编辑歌曲' : '上传新歌曲'}</h2>
          <button onClick={onClose}>×</button>
        </div>
        
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>歌曲名称*</label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => setFormData({...formData, name: e.target.value})}
              required
            />
          </div>
          
          <div className="form-group">
            <label>发布日期*</label>
            <input
              type="date"
              value={formData.releaseTime}
              onChange={(e) => setFormData({...formData, releaseTime: e.target.value})}
              required
            />
          </div>
          
          {/* 使用新的艺术家/乐队选择器 */}
          <ArtistBandSelector
            selectedItems={selectedCreators}
            onSelectionChange={setSelectedCreators}
            searchType="both"
            label="创作者"
            placeholder="搜索创作者（艺术家或乐队）..."
          />
          
          <ArtistBandSelector
            selectedItems={selectedPerformers}
            onSelectionChange={setSelectedPerformers}
            searchType="artist"
            label="演唱者"
            placeholder="搜索演唱者（艺术家）..."
          />
          
          <div className="form-row">
            <div style={{ flex: 1, marginRight: '10px' }}>
              <ArtistBandSelector
                selectedItems={selectedLyricists}
                onSelectionChange={setSelectedLyricists}
                searchType="artist"
                label="作词者"
                placeholder="搜索作词者..."
              />
            </div>
            
            <div style={{ flex: 1, marginLeft: '10px' }}>
              <ArtistBandSelector
                selectedItems={selectedComposers}
                onSelectionChange={setSelectedComposers}
                searchType="artist"
                label="作曲者"
                placeholder="搜索作曲者..."
              />
            </div>
          </div>
          
          <div className="form-row">
            <div style={{ flex: 1, marginRight: '10px' }}>
              <ArtistBandSelector
                selectedItems={selectedArrangers}
                onSelectionChange={setSelectedArrangers}
                searchType="artist"
                label="编曲者"
                placeholder="搜索编曲者..."
              />
            </div>
            
            <div style={{ flex: 1, marginLeft: '10px' }}>
              <ArtistBandSelector
                selectedItems={selectedInstrumentalists}
                onSelectionChange={setSelectedInstrumentalists}
                searchType="artist"
                label="演奏者"
                placeholder="搜索演奏者..."
              />
            </div>
          </div>
          
          <GenreSelector
            selectedGenresSet={selectedGenresSet}
            onGenresChange={setSelectedGenresSet}
          />
          
          <button type="submit" className="btn btn-primary">
            {editingSong ? '更新歌曲' : '上传歌曲'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default SongForm;