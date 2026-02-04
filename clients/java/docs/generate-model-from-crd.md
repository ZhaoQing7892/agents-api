# Guide to generate Java codes from CustomResourceDefinition

This document is adapted from the official Kubernetes Java client documentation, with the original reference source being:[Kubernetes-Client](https://github.com/kubernetes-client/java/blob/master/docs/generate-model-from-third-party-resources.md)

__TL;DR__: This document shows you how to generate java class models from your CRD YAML manifests.
Alternatively, without this automatic code-generation process, you can always manually write your
models for CRDs by implementing [KubernetesObject](https://github.com/kubernetes-client/java/blob/master/kubernetes/src/main/java/io/kubernetes/client/common/KubernetesObject.java)
and [KubernetesListObject](https://github.com/kubernetes-client/java/blob/master/kubernetes/src/main/java/io/kubernetes/client/common/KubernetesListObject.java) interfaces.

## Remote Generate via Github Action

### 1. Fork Upstream Repo

Fork the repository [agents-api](https://github.com/openkruise/agents-api)
so that you can run the github action ["CRD Java Model Generate"](/.github/workflows/generate-crd.yml)
in your forked repo. (Running github action job manually requires "collaborator" access to the repo).

Alternatively, you can also copy-paste the above github action definition to your repo to run it.

### 2. Execute Github Action

Go to the repo home page, then click __"Actions"__. Find the job "CRD Java Model Generate"
under __"Workflows"__ and then run it. The workflow will help convert your CRD yaml manifests to
zip-archived java sources which is downloadable after it finishes.

### 3. Download the Generated Sources

Go to the __"Summary"__ page of the workflow execution, you can find the archived java sources at
the bottom of the page. Just click it to download.

## Generate in Local Environment
# Java Model Generation Script Usage Guide

## Overview

[generate-java-model.sh](/clients/java/generate-java-model.sh) is a script used to generate Java model classes from OpenKruise Agents CRD (Custom Resource Definitions).

## Prerequisites

1. **Docker Environment** - The script relies on Docker to run the model generation container
2. **Ability to connect to Kind Cluster** - The script needs to communicate with the KinD cluster running on the host during execution

## Parameter Description

This script supports the following environment variable configurations:

- `IMAGE_NAME` - Specifies the Docker image name, default is `ghcr.io/kubernetes-client/java/crd-model-gen`
- `IMAGE_TAG` - Specifies the Docker image tag, default is `v1.0.6`

## Usage

### Running the Script

```
cd clients/java
bash generate-java-model.sh
```

## Workflow

1. The script first checks if the specified Docker image exists locally
2. If the image doesn't exist, it automatically pulls the image
3. Runs the container and mounts necessary volumes (Docker socket and current directory)
4. Retrieves CRD definition files from the OpenKruise repository
5. Generates Java model classes to the specified output directory

## Target Resources Generated

The script will generate Java models from the following CRD files:

- `agents.kruise.io_sandboxes.yaml`
- `agents.kruise.io_sandboxsets.yaml`

## Output Location

The generated Java model classes will be placed in the current working directory with package name `io.openkruise.agents.client`.

## Customizing Output Directory Configuration

If you need to customize the output directory, you should modify [generate-java-model.sh](/clients/java/generate-java-model.sh) -u, -n, and -p parameters in the file:

### Parameters Explanation
- **`-u`**: Specifies the URL addresses of CRD YAML files
    - Current URLs used:
        - `https://raw.githubusercontent.com/openkruise/agents/master/config/crd/bases/agents.kruise.io_sandboxes.yaml`
        - `https://raw.githubusercontent.com/openkruise/agents/master/config/crd/bases/agents.kruise.io_sandboxsets.yaml`
- **`-n`**: Specifies the namespace name for generated models, default is `io.openkruise.agents.client`
- **`-p`**: Specifies the Java package name, default is `io.openkruise.agents.client`
- **`-o`**: Specifies the output directory, default is `"$(pwd)"` (current working directory)

### Default Output Path
By default, the code will be output to:
```
src/main/java/${package}/models
```

Where `${package}` is the package name passed via the `-p` parameter.

### Customization Example
To change the output settings, you can modify these parameters before running the script:
```bash
# Modify the -p parameter to change package name and output path
docker run \
  --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$(pwd)":"$(pwd)" \
  --network host \
  ${IMAGE_NAME}:${IMAGE_TAG} \
  /generate.sh \
  -u [YOUR_CRD_URL] \
  -n [YOUR_NAMESPACE] \
  -p [YOUR_PACKAGE_NAME] \
  -o [YOUR_OUTPUT_DIR]
```


## Classes and Specific Type Changes
```
V1alpha1Sandbox	IoK8sApimachineryPkgApisMetaV1ObjectMetaV2 -> V1ObjectMeta
V1alpha1SandboxSpec	Object template -> V1PodTemplateSpec template
V1alpha1SandboxSet	IoK8sApimachineryPkgApisMetaV1ObjectMetaV2 -> V1ObjectMeta
V1alpha1SandboxSetSpec	Object template -> V1PodTemplateSpec template
V1alpha1SandboxSetSpecVolumeClaimTemplates	Object metadata -> V1ObjectMeta metadata
V1alpha1SandboxSetSpecSpecResources	Map<String, Object> -> Map<String, Quantity>
V1alpha1SandboxSetSpecStatus	Map<String, Object> -> Map<String, Quantity>
```