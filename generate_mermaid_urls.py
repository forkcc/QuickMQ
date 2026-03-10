#!/usr/bin/env python3
import base64
import urllib.parse
import sys
import os

def encode_mermaid_to_url(mermaid_code):
    # Base64 encode
    encoded = base64.b64encode(mermaid_code.encode('utf-8')).decode('utf-8')
    # URL encode
    encoded = encoded.replace('+', '%2B').replace('/', '%2F').replace('=', '%3D')
    return f"https://mermaid.ink/svg/{encoded}"

def main():
    images_dir = "docs/images"
    diagrams = {
        "architecture": "整体架构",
        "connect_auth": "连接与认证流程", 
        "pub_sub": "发布与订阅流程",
        "hook_system": "Hook系统"
    }
    
    print("生成 Mermaid.ink 图片链接:")
    print()
    
    for key, name in diagrams.items():
        mmd_file = os.path.join(images_dir, f"{key}.mmd")
        if os.path.exists(mmd_file):
            with open(mmd_file, 'r', encoding='utf-8') as f:
                mermaid_code = f.read()
            url = encode_mermaid_to_url(mermaid_code)
            print(f"### {name}")
            print(f"![{name}]({url})")
            print(f"```markdown")
            print(f"![{name}]({url})")
            print(f"```")
            print()
        else:
            print(f"文件不存在: {mmd_file}")
    
    print("将上述图片链接复制到 README.md 中即可使用在线图片。")

if __name__ == "__main__":
    main()