#!/bin/bash

# 自动化脚本：将models2文件夹中的Java文件转换为models文件夹中的格式
# 处理类型映射、导入语句和方法签名的变更

SOURCE_DIR="./models2"
TARGET_DIR="./models_new"

# 确保源目录存在
if [ ! -d "$SOURCE_DIR" ]; then
    echo "Source directory does not exist: $SOURCE_DIR"
    exit 1
fi

# 创建目标目录
mkdir -p "$TARGET_DIR"

# 清空目标目录
rm -f "$TARGET_DIR"/*.java

# 复制所有源文件到目标目录
cp "$SOURCE_DIR"/*.java "$TARGET_DIR/"

echo "Starting conversion from $SOURCE_DIR to $TARGET_DIR..."

# 遍历目标目录中的所有Java文件
for file in "$TARGET_DIR"/*.java; do
    if [ -f "$file" ]; then
        echo "Processing $(basename "$file")..."

        # 创建临时文件
        temp_file=$(mktemp)

        # 第一步：提取包声明
        package_line=$(grep -m 1 "^package " "$file")

        # 第二步：获取旧的import语句
        old_imports=$(grep "^[[:space:]]*import[[:space:]]" "$file" || true)

        # 第三步：提取类定义开始之后的内容（跳过import部分）
        class_start_line=$(awk '/^public (class|interface) / { print NR; exit }' "$file")
        if [ -n "$class_start_line" ]; then
            class_content=$(tail -n +$((class_start_line)) "$file")
        else
            class_content=$(cat "$file" | sed '1,/^[[:space:]]*import[[:space:]]/d')
        fi

        # 第四步：处理类内容中的类型替换
        echo "$class_content" > "$temp_file"

        # 应用类型映射
        sed -i.bak -e 's/IoK8sApimachineryPkgApisMetaV1ObjectMetaV2/V1ObjectMeta/g' "$temp_file"
        sed -i.bak -e 's/Object template/V1PodTemplateSpec template/g' "$temp_file"
        sed -i.bak -e 's/Object metadata/V1ObjectMeta metadata/g' "$temp_file"
        sed -i.bak -e 's/Map<String, Object> limits/Map<String, Quantity> limits/g' "$temp_file"
        sed -i.bak -e 's/Map<String, Object> requests/Map<String, Quantity> requests/g' "$temp_file"

        # 第五步：构建新的import列表
        # 移除旧的import并添加新的import
        new_imports=""
        if [ -n "$old_imports" ]; then
            # 过滤掉需要被替换的类型相关的import
            filtered_imports=$(echo "$old_imports" | grep -v -E "(IoK8sApimachineryPkgApisMetaV1ObjectMetaV2|Quantity|V1ObjectMeta|V1PodTemplateSpec)")

            # 构建完整的import列表
            all_imports=(
                "import io.kubernetes.client.custom.Quantity;"
                "import io.kubernetes.client.openapi.models.V1ObjectMeta;"
                "import io.kubernetes.client.openapi.models.V1PodTemplateSpec;"
            )

            # 添加过滤后保留的原始import
            while IFS= read -r imp; do
                if [ -n "$imp" ]; then
                    all_imports+=("$imp")
                fi
            done <<< "$(echo "$filtered_imports" | grep -v '^$')"

            # 排序并去重
            printf '%s\n' "${all_imports[@]}" | sort -u > "${temp_file}.imports"
        fi

        # 第六步：重建文件
        {
            # 包声明
            if [ -n "$package_line" ]; then
                echo "$package_line"
                echo ""
            fi

            # 新的import语句
            if [ -f "${temp_file}.imports" ]; then
                cat "${temp_file}.imports"
                echo ""
            elif [ -n "$old_imports" ]; then
                # 如果没有创建import文件，至少添加必要的import
                echo "import io.kubernetes.client.custom.Quantity;"
                echo "import io.kubernetes.client.openapi.models.V1ObjectMeta;"
                echo "import io.kubernetes.client.openapi.models.V1PodTemplateSpec;"
                echo ""
            fi

            # 类定义及以后的内容
            cat "$temp_file"

        } > "${file}.new" && mv "${file}.new" "$file"

        # 清理临时文件
        rm -f "$temp_file" "$temp_file.bak" "${temp_file}.imports"
    fi
done

echo "Conversion completed successfully!"
echo "Converted files are in: $TARGET_DIR"
