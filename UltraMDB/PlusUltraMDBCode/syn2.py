import os
import shutil
from pathlib import Path

# 基础路径配置
BASE_DIR = Path(r"C:\lec\tmsp\mdb\MusicDataBase\UltraMDB\PlusUltraMDBCode")
SERVICES = {
    "1": "CreatorService",
    "2": "MusicService",
    "3": "OrganizeService",
    "4": "TrackService"
}

def get_service_path(service_name):
    """获取服务的完整路径"""
    return BASE_DIR / service_name / "src" / "main" / "scala" / "Objects"

def display_service_options():
    """显示服务选择菜单"""
    print("\n请选择源服务（其API将被复制到其他服务）:")
    for num, name in SERVICES.items():
        print(f"{num}. {name}")
    print("0. 退出")

def validate_service_choice(choice):
    """验证用户输入的服务选择"""
    return choice in SERVICES or choice == "0"

def sync_apis(source_service, target_services):
    """同步API文件夹"""
    source_path = get_service_path(source_service)
    if not source_path.exists():
        print(f"错误：源路径不存在 - {source_path}")
        return False
    
    for service in target_services:
        target_path = get_service_path(service)
        print(f"\n正在同步到 {service}...")
        
        # 删除目标文件夹（如果存在）
        if target_path.exists():
            try:
                shutil.rmtree(target_path)
                print(f"已清除旧API文件夹: {target_path}")
            except Exception as e:
                print(f"删除旧文件夹失败: {e}")
                continue
        
        # 复制源文件夹到目标位置
        try:
            shutil.copytree(source_path, target_path)
            print(f"成功同步到: {target_path}")
        except Exception as e:
            print(f"同步失败: {e}")
    
    return True

def main():
    print("=== API同步工具 ===")
    print(f"工作目录: {BASE_DIR}")
    
    while True:
        display_service_options()
        choice = input("请输入选择 (0-4): ").strip()
        
        if choice == "0":
            print("退出程序。")
            break
        
        if not validate_service_choice(choice):
            print("无效选择，请重新输入。")
            continue
        
        source_service = SERVICES[choice]
        target_services = [s for n, s in SERVICES.items() if n != choice]
        
        print(f"\n即将执行同步操作:")
        print(f"源服务: {source_service}")
        print(f"目标服务: {', '.join(target_services)}")
        
        confirm = input("确认执行同步？(y/n): ").lower()
        if confirm != 'y':
            print("取消同步操作。")
            continue
        
        if sync_apis(source_service, target_services):
            print("\n同步操作完成！")
        else:
            print("\n同步过程中出现错误。")
        
        input("\n按Enter键继续...")

if __name__ == "__main__":
    main()
