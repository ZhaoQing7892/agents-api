# Generate Kubernetes CRD Models

This script automates the process of generating Python models from Kubernetes Custom Resource Definitions (CRDs). It downloads the latest CRD YAML files, extracts their schemas, and generates corresponding Pydantic models.

## Prerequisites

Before running the script, ensure you have the following tools installed:

- `curl`: To download CRD files.
- `yq`: To parse and extract schema information from YAML files.
- `datamodel-codegen`: To generate Pydantic models from JSON schemas.
- `black` and `isort`: To format the generated Python code.

Install the required dependencies using the commands below:

```bash
pip install datamodel-codegen black isort
brew install yq curl  # On macOS
```

## How to Run

1. Make the script executable:
```bash
chmod +x generate_crd_models.sh
```
2. Execute the script:
```bash
./generate_crd_models.sh
```
## The script will perform the following steps:
1. Download the latest CRD YAML files from the specified URLs.
2. Extract the OpenAPI v3 schema from each CRD.
3. Generate Pydantic models using `datamodel-codegen`.
4. Post-process the generated models to:
    - Replace generic types with Kubernetes-specific types (`V1ObjectMeta`, `V1PodTemplateSpec`, etc.).
    - Add necessary imports and configurations.
    - Format the code using `black` and `isort`.

## Output

The script generates the following files:
- **CRD Files**: Stored in the `crds/` directory.
- **JSON Schemas**: Stored in the `schemas/` directory.
- **Python Models**: Stored in the `models/` directory.

Each generated model file corresponds to a specific Kubernetes CRD (e.g., [sandbox.py](file:///Users/issuser/codes/ZhaoQing7892/agents-api/clients/python/openkruise/agents/models/sandbox.py), [sandboxset.py](file:///Users/issuser/codes/ZhaoQing7892/agents-api/clients/python/openkruise/agents/models/sandboxset.py)).

## Customization

To customize the script:
1. Modify the `crd_urls` array to include additional CRD sources.
2. Adjust the `datamodel-codegen` options in the script to suit your needs.
3. Update the post-processing logic to handle new field types or imports.

## Troubleshooting

If the script fails:
1. Check that all prerequisites are installed.
2. Verify that the CRD URLs are accessible.
3. Inspect the logs for detailed error messages.

For further assistance, refer to the documentation of the individual tools used in this script.

## Deployment and Verification

After the models are generated, follow these steps to ensure everything works as expected:

1. **Replace Files**  
   Copy the generated model files from the `models/` directory to the corresponding location in your project workspace:
   ```bash
   cp models/*.py /path/to/your/project/models/
   ```


2. **Verify Expected Results**
   - Confirm that all model files have been successfully copied.
   - Validate that the field types and structures in the models meet expectations.
   - Run unit tests or simple scripts in your project to ensure the newly generated models function correctly.

3. **Clean Up Temporary Files** *(Optional)*  
   If the temporary files are no longer needed, remove the `crds/` and `schemas/` directories to keep your workspace tidy:
   ```bash
   rm -rf crds/ schemas/
   ```

If any issues arise, inspect the logs or error messages for troubleshooting, and rerun the script if necessary.