# Agents API Python Client

Python client library for managing Sandbox and SandboxSet custom resources in OpenKruise.

## Overview

The Agents API Python Client is a comprehensive library for managing Sandbox and SandboxSet custom resources in Kubernetes. It provides a clean, object-oriented interface for creating, updating, deleting, and monitoring sandbox resources within OpenKruise, making it easy to integrate sandbox management into your Python applications.

## Features

- **Complete CRUD Operations**: Full support for creating, reading, updating, and deleting Sandbox and SandboxSet resources
- **Resource Management**: Comprehensive tools for managing sandbox lifecycles
- **Status Monitoring**: Built-in functions to monitor resource conditions and status
- **Event Watching**: Real-time monitoring of resource changes
- **Exception Handling**: Custom exception classes for better error management
- **Configuration Management**: Flexible configuration options for different environments
- **Extensible Design**: Easy to extend for additional custom resources

## Installation

### Prerequisites

- Python 3.7 or higher
- Kubernetes cluster with OpenKruise installed
- Appropriate RBAC permissions for managing Sandbox and SandboxSet resources

### Install from source via git

```bash
# Replace "main" with a specific version tag (e.g., "v0.1.0") from
# https://github.com/openkruise/agents-api/releases to pin a version tag.
pip install git+https://github.com/openkruise/agents-api.git@${VERSION}#subdirectory=clients/python/agents_api
```

### Install from Source

```bash
git clone https://github.com/openkruise/agents-api.git
cd agents-api/clients/python/agents_api
pip install -e .
```


## Quick Start

### Basic Usage

```python
from agents_api_client import SandboxClient, SandboxSetClient

# Initialize clients
sandbox_client = SandboxClient(namespace="default")
sandboxset_client = SandboxSetClient(namespace="default")

# Create a sandbox
sandbox_manifest = {
    "apiVersion": "agents.kruise.io/v1alpha1",
    "kind": "Sandbox",
    "metadata": {
        "name": "example-sandbox",
        "namespace": "default",
        "labels": {
            "app": "example"
        }
    },
    "spec": {
        "template": {
            "spec": {
                "containers": [
                    {
                        "name": "app",
                        "image": "nginx:latest",
                        "ports": [
                            {
                                "containerPort": 80
                            }
                        ]
                    }
                ]
            }
        }
    }
}

# Create the sandbox
created_sandbox = sandbox_client.create_sandbox(sandbox_manifest)
print(f"Created sandbox: {created_sandbox['metadata']['name']}")
```


### Working with SandboxSets

```python
# Create a sandboxset with multiple replicas
sandboxset_manifest = {
    "apiVersion": "agents.kruise.io/v1alpha1",
    "kind": "SandboxSet",
    "metadata": {
        "name": "example-sandboxset",
        "namespace": "default",
        "labels": {
            "app": "example"
        }
    },
    "spec": {
        "replicas": 3,
        "template": {
            "spec": {
                "containers": [
                    {
                        "name": "app",
                        "image": "nginx:latest",
                        "ports": [
                            {
                                "containerPort": 80
                            }
                        ]
                    }
                ]
            }
        }
    }
}

# Create the sandboxset
created_sandboxset = sandboxset_client.create_sandboxset(sandboxset_manifest)
print(f"Created sandboxset: {created_sandboxset['metadata']['name']} with {created_sandboxset['spec']['replicas']} replicas")

# Scale the sandboxset
scaled_sandboxset = sandboxset_client.scale_sandboxset("example-sandboxset", 5)
print(f"Scaled to {scaled_sandboxset['spec']['replicas']} replicas")
```


## API Reference

### SandboxClient

#### Creating Resources

- **`create_sandbox(body, namespace=None)`**: Create a new Sandbox resource
- **`create_sandboxset(body, namespace=None)`**: Create a new SandboxSet resource

#### Retrieving Resources

- **`get_sandbox(name, namespace=None)`**: Get a specific Sandbox by name
- **`get_sandboxset(name, namespace=None)`**: Get a specific SandboxSet by name
- **`list_sandboxes(namespace=None, label_selector=None, field_selector=None)`**: List all sandboxes in a namespace
- **`list_sandboxsets(namespace=None, label_selector=None, field_selector=None)`**: List all sandboxsets in a namespace

#### Updating Resources

- **`update_sandbox(name, body, namespace=None)`**: Update an existing Sandbox
- **`update_sandboxset(name, body, namespace=None)`**: Update an existing SandboxSet
- **`patch_sandbox(name, body, namespace=None, content_type='application/merge-patch+json')`**: Partially update a Sandbox
- **`patch_sandboxset(name, body, namespace=None, content_type='application/merge-patch+json')`**: Partially update a SandboxSet

#### Status Management

- **`update_sandbox_status(name, body, namespace=None)`**: Update Sandbox status
- **`update_sandboxset_status(name, body, namespace=None)`**: Update SandboxSet status
- **`get_sandboxset_replicas(name, namespace=None)`**: Get current replica count for a SandboxSet

#### Resource Scaling

- **`scale_sandboxset(name, replicas, namespace=None)`**: Scale a SandboxSet to specified replicas

#### Deleting Resources

- **`delete_sandbox(name, namespace=None, grace_period_seconds=None)`**: Delete a Sandbox
- **`delete_sandboxset(name, namespace=None, grace_period_seconds=None)`**: Delete a SandboxSet
- **`delete_collection_sandboxes(namespace=None, label_selector=None, field_selector=None)`**: Delete multiple sandboxes
- **`delete_collection_sandboxsets(namespace=None, label_selector=None, field_selector=None)`**: Delete multiple sandboxsets

#### Monitoring

- **`watch_sandboxes(namespace=None, label_selector=None, field_selector=None, timeout_seconds=None)`**: Watch for sandbox changes
- **`watch_sandboxsets(namespace=None, label_selector=None, field_selector=None, timeout_seconds=None)`**: Watch for sandboxset changes
- **`wait_for_sandbox_condition(name, condition_type, condition_status="True", timeout=300, namespace=None)`**: Wait for sandbox condition
- **`wait_for_sandboxset_condition(name, condition_type, condition_status="True", timeout=300, namespace=None)`**: Wait for sandboxset condition


## Configuration

### Kubernetes Configuration

The client automatically loads Kubernetes configuration:

1. **In-cluster**: When running inside a Kubernetes pod
2. **Local**: When running outside the cluster (uses `~/.kube/config`)

### Custom Configuration

```python
from agents_api_client.configuration import Configuration

config = Configuration()
config.default_timeout = 60  # seconds
config.request_timeout = 120  # seconds
```

## Advanced Usage

### Filtering and Label Selection

```python
# List sandboxes with specific labels
sandboxes = sandbox_client.list_sandboxes(
    label_selector="app=example,version=v1"
)

# List sandboxsets with field selectors
sandboxsets = sandboxset_client.list_sandboxsets(
    field_selector="metadata.namespace=default"
)
```


### Watching Resources

```python
# Watch for sandbox changes
for event in sandbox_client.watch_sandboxes():
    print(f"Event: {event['type']} - {event['object']['metadata']['name']}")
    if event['type'] == 'ERROR':
        break
```


### Waiting for Conditions

```python
# Wait for sandbox to be ready
try:
    ready_sandbox = sandbox_client.wait_for_sandbox_condition(
        name="my-sandbox",
        condition_type="Ready",
        condition_status="True",
        timeout=600
    )
    print("Sandbox is ready!")
except TimeoutException:
    print("Sandbox did not become ready within timeout")
```