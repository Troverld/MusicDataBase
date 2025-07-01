import os
import re
from typing import List, Tuple
from datetime import datetime

def extract_case_classes_with_docs(file_path: str) -> List[Tuple[str, str]]:
    """从单个Scala文件中提取带文档注释的case class定义"""
    with open(file_path, 'r', encoding='utf-8') as file:
        content = file.read()
    
    # 使用正则表达式匹配文档注释和case class定义
    pattern = re.compile(
        r'/\*\*(.*?)\*/\s*case class (\w+)\((.*?)\) extends API\[(.*?)\]\((.*?)\)',
        re.DOTALL
    )
    
    results = []
    for match in pattern.finditer(content):
        doc_comment = match.group(1).strip()
        class_name = match.group(2).strip()
        params = match.group(3).strip()
        return_type = match.group(4).strip()
        service_code = match.group(5).strip()
        
        # 清理文档注释中的*和多余空格
        cleaned_doc = '\n'.join(
            line.strip().removeprefix('*').strip()
            for line in doc_comment.split('\n')
            if line.strip()
        )
        
        # 重新构建提取的内容
        extracted = f"""/**
 * {cleaned_doc}
 */

case class {class_name}(
  {params}
) extends API[{return_type}]({service_code})"""
        
        results.append((class_name, extracted))
    
    return results

def write_extracted_content(output_file: str, content: str):
    """将提取的内容写入输出文件"""
    with open(output_file, 'a', encoding='utf-8') as f:
        f.write(content + '\n\n')

def find_and_extract_scala_files(root_dir: str, output_file: str):
    """递归查找Scala文件并提取case class定义"""
    # 清空或创建输出文件
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(f"// 提取时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"// 源目录: {os.path.abspath(root_dir)}\n\n")
    
    total_count = 0
    processed_files = 0
    
    for root, _, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.scala'):
                full_path = os.path.join(root, file)
                try:
                    extracted = extract_case_classes_with_docs(full_path)
                    if extracted:
                        processed_files += 1
                        total_count += len(extracted)
                        
                        # 写入文件头
                        header = f"\n// ============================================\n" \
                                f"// 文件: {full_path}\n" \
                                f"// ============================================\n\n"
                        write_extracted_content(output_file, header)
                        
                        # 写入每个提取的类
                        for class_name, content in extracted:
                            write_extracted_content(output_file, f"// 类名: {class_name}\n")
                            write_extracted_content(output_file, content)
                        
                        # 控制台输出进度
                        print(f"处理完成: {full_path} (找到 {len(extracted)} 个匹配项)")
                except Exception as e:
                    print(f"处理文件 {full_path} 时出错: {e}")
    
    # 写入统计信息
    summary = f"\n// ============================================\n" \
              f"// 提取完成!\n" \
              f"// 共扫描 {processed_files} 个Scala文件\n" \
              f"// 找到 {total_count} 个匹配的case class定义\n" \
              f"// ============================================\n"
    write_extracted_content(output_file, summary)
    
    print(f"\n提取完成! 结果已保存到 {output_file}")
    print(f"共处理 {processed_files} 个文件，找到 {total_count} 个匹配的case class定义")

if __name__ == "__main__":
    current_dir = os.getcwd()
    output_file = "extracted_case_classes.scala"
    
    print(f"开始从 {current_dir} 递归提取case class定义...")
    print(f"结果将保存到: {output_file}")
    
    find_and_extract_scala_files(current_dir, output_file)