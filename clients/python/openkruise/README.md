# Agents API Python Client

Python client library for managing Sandbox and SandboxSet custom resources in OpenKruise.

## Overview

The Agents API Python Client is a comprehensive library for managing Sandbox and SandboxSet custom resources in Kubernetes. It provides a clean, object-oriented interface for creating, updating, deleting, and monitoring sandbox resources within OpenKruise, making it easy to integrate sandbox management into your Python applications.

## Installation

### Prerequisites

- Python 3.7 or higher
- Kubernetes cluster with OpenKruise installed
- Appropriate RBAC permissions for managing Sandbox and SandboxSet resources

### Install from source via git

```bash
# Replace "main" with a specific version tag (e.g., "v0.1.0") from
# https://github.com/openkruise/agents-api/releases to pin a version tag.
pip install git+https://github.com/openkruise/agents-api.git@${VERSION}#subdirectory=clients/python/openkruise
```

### Install from Source

```bash
git clone https://github.com/openkruise/agents-api.git
cd clients/python/openkruise
pip install -e .
```


## Quick Start

### Basic Usage

```python
import os

import pytest
from kubernetes.client import V1ObjectMeta, V1ContainerPort, V1Container, V1PodSpec, V1PodTemplateSpec
from agents import Sandbox, SandboxSpec
from agents import SandboxClient


class TestSandboxClient:
    def test_create_sandbox(self):
        sandbox_client = SandboxClient()
        sandbox = self.build_sandbox()
        sandbox_client.create_sandbox(sandbox)

    def build_sandbox(self) -> Sandbox:
        # 配置参数
        namespace = os.getenv("NAMESPACE", "default")
        name = "my-python-sandbox"
        image = "nginx:latest"
        paused = False

        # 端口示例
        container_ports = [
            {"name": "http", "container_port": 80},
            {"name": "metrics", "container_port": 9090}
        ]

        # 标签示例
        labels = {
            "env": "dev",
            "created-by": "python-client"
        }
        restart_policy = "Never"
        container_name = "sandbox-container"

        metadata = V1ObjectMeta(
            name=name,
            namespace=namespace,
            labels=labels,
        )

        ports = []
        if container_ports:
            for port in container_ports:
                ports.append(V1ContainerPort(**port))

        container = V1Container(
            name=container_name,
            image=image,
            ports=ports or None,
        )

        pod_spec = V1PodSpec(
            restart_policy=restart_policy,
            containers=[container],
        )

        template = V1PodTemplateSpec(
            metadata=V1ObjectMeta(labels={"app": "sandbox-container"}),
            spec=pod_spec,
        )

        spec = SandboxSpec(
            template=template,
            paused=paused,
        )

        return Sandbox(
            metadata=metadata,
            spec=spec,
        )

```