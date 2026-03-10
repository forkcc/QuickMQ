#!/bin/bash

# 生成 QuickMQ 架构与流程图片
# 需要安装 Node.js 和 @mermaid-js/mermaid-cli
# 使用: ./generate-diagrams.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCS_DIR="$SCRIPT_DIR/docs"
IMAGES_DIR="$DOCS_DIR/images"

# 创建图片目录
mkdir -p "$IMAGES_DIR"

# 检查是否安装了 mermaid-cli
if ! command -v mmdc &> /dev/null; then
    echo "安装 @mermaid-js/mermaid-cli..."
    npm install --no-save @mermaid-js/mermaid-cli
    export PATH="$SCRIPT_DIR/node_modules/.bin:$PATH"
fi

# 定义图表文件
declare -A diagrams=(
    ["architecture"]="整体架构"
    ["connect_auth"]="连接与认证流程"
    ["pub_sub"]="发布与订阅流程"
    ["hook_system"]="Hook系统"
)

# 生成图片
export PUPPETEER_SANDBOX=0
for key in "${!diagrams[@]}"; do
    input="$IMAGES_DIR/$key.mmd"
    output_svg="$IMAGES_DIR/$key.svg"
    output_png="$IMAGES_DIR/$key.png"
    
    if [[ -f "$input" ]]; then
        echo "生成 ${diagrams[$key]}..."
        
        # 生成 SVG
        mmdc -i "$input" -o "$output_svg" -t forest
        
        # 生成 PNG
        mmdc -i "$input" -o "$output_png" -t forest -b white
        
        echo "  -> $output_svg"
        echo "  -> $output_png"
    else
        echo "警告: 输入文件不存在: $input"
    fi
done

echo "完成！图片已保存到 $IMAGES_DIR/"