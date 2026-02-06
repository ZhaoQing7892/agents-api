#!/bin/bash

set -e   # Exit on error

# # Define input and output directories
CRDS_DIR="crds"
SCHEMAS_DIR="schemas"
MODELS_DIR="models"

# Create output directories if they don't exist
mkdir -p "$CRDS_DIR" "$SCHEMAS_DIR" "$MODELS_DIR"

# Download the latest CRD files from master
crd_urls=(
  "https://raw.githubusercontent.com/openkruise/agents/master/config/crd/bases/agents.kruise.io_sandboxes.yaml"
  "https://raw.githubusercontent.com/openkruise/agents/master/config/crd/bases/agents.kruise.io_sandboxsets.yaml"
  "https://raw.githubusercontent.com/openkruise/agents/master/config/crd/bases/agents.kruise.io_sandboxclaims.yaml"
)

for url in "${crd_urls[@]}"; do
  filename=$(basename "$url")
  curl -fsSL "$url" -o "$CRDS_DIR/$filename"
done

# Iterate through all YAML files in the bases directory
for file in "$CRDS_DIR"/*.yaml; do
  kind=$(yq '.spec.names.kind' < "$file")

  kind_lower=$(echo "$kind" | tr '[:upper:]' '[:lower:]')

  INPUT_SCHEMA="$SCHEMAS_DIR/${kind_lower}_schema.json"
  OUTPUT_MODEL="$MODELS_DIR/${kind_lower}.py"

  echo "Processing schema file $INPUT_SCHEMA"
  # Use yq to extract schema and output as JSON
  yq '.spec.versions[0].schema.openAPIV3Schema' -o=json < "$file" > "$INPUT_SCHEMA"

  echo "Generating initial model $OUTPUT_MODEL"

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

  echo "$OUTPUT_MODEL generation completed, replacing types and injecting"


  # Step 2: Remove from typing import Any
  sed -i.bak '/from typing import Any/d' "$OUTPUT_MODEL"
  rm -f "$OUTPUT_MODEL.bak"

   # Step 3: Add kubernetes imports (if not present)
  if ! grep -q "from kubernetes.client.models import" "$OUTPUT_MODEL"; then
    sed -i.bak '/from pydantic import BaseModel/i\
from kubernetes.client.models import V1ObjectMeta, V1PodTemplateSpec, V1PersistentVolumeClaim
  ' "$OUTPUT_MODEL"
    rm -f "$OUTPUT_MODEL.bak"
  fi

  # Step 4: Replace field types
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

   # Step 5: Add model_config for classes containing Kubernetes types
  for CLASS_NAME in "${CLASSES[@]}"; do
    if grep -q "class $CLASS_NAME(" "$OUTPUT_MODEL"; then
      sed -i.bak "/class $CLASS_NAME(/a\\
    model_config = ConfigDict(arbitrary_types_allowed=True)
    " "$OUTPUT_MODEL"
      rm -f "$OUTPUT_MODEL.bak"
    fi
  done

  # Step 6: Ensure ConfigDict is imported
  if ! grep -q "from pydantic import.*ConfigDict" "$OUTPUT_MODEL"; then
    # Add ConfigDict to the from pydantic import BaseModel line
    sed -i.bak 's/from pydantic import BaseModel/from pydantic import BaseModel, ConfigDict/' "$OUTPUT_MODEL"
    rm -f "$OUTPUT_MODEL.bak"
  fi

  # Step 6: Replace global regex with pattern
  sed -i '' 's/regex=/pattern=/g' "$OUTPUT_MODEL"

  echo "Reformatting code..."
  black "$OUTPUT_MODEL"
  isort "$OUTPUT_MODEL"

  echo "$OUTPUT_MODEL processing completed!"
done

echo "All done!"