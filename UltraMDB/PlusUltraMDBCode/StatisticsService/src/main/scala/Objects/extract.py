import os
import re
from typing import List

def extract_case_classes(file_path: str) -> List[str]:
    """提取带文档注释的case class定义"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 匹配模式：文档注释 + case class定义（不包含后续的伴生对象或类体内容）
    pattern = re.compile(
        r'/\*\*(.*?)\*/\s*case class (\w+)\((.*?)\)(?:[\s\{]|$)',
        re.DOTALL
    )
    
    results = []
    for match in pattern.finditer(content):
        doc_comment = match.group(1).strip()
        class_name = match.group(2).strip()
        params = match.group(3).strip()
        
        # 清理文档注释
        cleaned_doc = '\n'.join(
            line.strip().lstrip('*').strip() 
            for line in doc_comment.split('\n')
        ).strip()
        
        # 重构提取内容
        extracted = f"""/**
 * {cleaned_doc}
 */
case class {class_name}(
  {params}
)"""
        results.append(extracted)
    
    return results

def process_directory(root_dir: str, output_file: str):
    """递归处理目录中的Scala文件"""
    with open(output_file, 'w', encoding='utf-8') as out_f:
        for root, _, files in os.walk(root_dir):
            for file in files:
                if file.endswith('.scala'):
                    file_path = os.path.join(root, file)
                    try:
                        classes = extract_case_classes(file_path)
                        if classes:
                            out_f.write(f"// 来源文件: {file_path}\n\n")
                            out_f.write("\n\n".join(classes))
                            out_f.write("\n\n" + "="*80 + "\n\n")
                            print(f"已处理: {file_path} (找到 {len(classes)} 个case class)")
                    except Exception as e:
                        print(f"处理 {file_path} 出错: {e}")

if __name__ == "__main__":
    target_dir = input("请输入要扫描的目录路径: ").strip()
    output_path = "extracted_case_classes.txt"
    
    if os.path.isdir(target_dir):
        print(f"开始扫描目录: {target_dir}")
        process_directory(target_dir, output_path)
        print(f"\n提取完成！结果已保存到: {output_path}")
    else:
        print("错误: 指定的路径不是有效目录")