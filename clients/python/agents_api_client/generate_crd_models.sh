#!/bin/bash

set -e  # 遇错退出

# 定义输入目录和输出目录
CRDS_DIR="crds"
SCHEMAS_DIR="schemas"
MODELS_DIR="models"

# 创建输出目录（如果不存在）
mkdir -p "$CRDS_DIR" "$SCHEMAS_DIR" "$MODELS_DIR"

# 遍历 bases 目录中的所有 YAML 文件
for file in "$CRDS_DIR"/*.yaml; do
  kind=$(yq '.spec.names.kind' < "$file")

  kind_lower=$(echo "$kind" | tr '[:upper:]' '[:lower:]')

  INPUT_SCHEMA="$SCHEMAS_DIR/${kind_lower}_schema.json"
  OUTPUT_MODEL="$MODELS_DIR/${kind_lower}.py"

  echo "开始处理schema文件 $INPUT_SCHEMA"
  # 使用 yq 提取 schema 并输出为 JSON
  yq '.spec.versions[0].schema.openAPIV3Schema' -o=json < "$file" > "$INPUT_SCHEMA"

  echo "开始生成初始模型 $OUTPUT_MODEL"

  datamodel-codegen \
    --input "$INPUT_SCHEMA" \
    --output "$OUTPUT_MODEL" \
    --input-file-type jsonschema \
    --target-python-version 3.11 \
    --use-schema-description \
    --use-field-description \
    --field-constraints \
    --formatters black isort \
    --keep-model-order \
    --class-name "$kind"

  echo "$OUTPUT_MODEL 生成完成，正在替换类型并注入"


  # Step 2: 删除 from typing import Any
  sed -i.bak '/from typing import Any/d' "$OUTPUT_MODEL"
  rm -f "$OUTPUT_MODEL.bak"

  # Step 3: 添加 kubernetes imports（如果不存在）
  if ! grep -q "from kubernetes.client.models import" "$OUTPUT_MODEL"; then
    sed -i.bak '/from pydantic import BaseModel/i\
from kubernetes.client.models import V1ObjectMeta, V1PodTemplateSpec, V1PersistentVolumeClaim
  ' "$OUTPUT_MODEL"
    rm -f "$OUTPUT_MODEL.bak"
  fi

  # Step 4: 替换字段类型
  sed -i.bak 's/metadata: dict\[str, Any\] | None = None/metadata: V1ObjectMeta | None = None/g' "$OUTPUT_MODEL"
  sed -i.bak 's/template: Any | None = None/template: V1PodTemplateSpec | None = None/g' "$OUTPUT_MODEL"
  sed -i.bak 's/volumeClaimTemplates: Any | None = None/volumeClaimTemplates: list\[V1PersistentVolumeClaim\] | None = None/g' "$OUTPUT_MODEL"
  rm -f "$OUTPUT_MODEL".bak

  CLASSES=()
  while IFS= read -r class_name; do
    [[ -n "$class_name" ]] && CLASSES+=("$class_name")
  done < <(
    awk '
      /^class [A-Za-z_][A-Za-z0-9_]*/ {
        gsub(/\(.*/, "", $2)
        cls = $2
        next
      }
      cls && /V1ObjectMeta|V1PodTemplateSpec|V1PersistentVolumeClaim/ {
        print cls
      }
    ' "$OUTPUT_MODEL" | sort -u
  )

  # Step 5: 为包含 Kubernetes 类型的类添加 model_config
  for CLASS_NAME in "${CLASSES[@]}"; do
    if grep -q "class $CLASS_NAME(" "$OUTPUT_MODEL"; then
      sed -i.bak "/class $CLASS_NAME(/a\\
    model_config = ConfigDict(arbitrary_types_allowed=True)
    " "$OUTPUT_MODEL"
      rm -f "$OUTPUT_MODEL.bak"
    fi
  done

  # Step 6: 确保导入了 ConfigDict
  if ! grep -q "from pydantic import.*ConfigDict" "$OUTPUT_MODEL"; then
    # 在 from pydantic import BaseModel 行中添加 ConfigDict
    sed -i.bak 's/from pydantic import BaseModel/from pydantic import BaseModel, ConfigDict/' "$OUTPUT_MODEL"
    rm -f "$OUTPUT_MODEL.bak"
  fi

  # Step 6: 保证全局regex替换为pattern
  sed -i '' 's/regex=/pattern=/g' "$OUTPUT_MODEL"

  echo "重新格式化代码..."
  black "$OUTPUT_MODEL"
  isort "$OUTPUT_MODEL"

  echo "$OUTPUT_MODEL 处理完成！"
done

echo "all 完成！"