"""
Basic usage example for Agents API Python Client
"""
from agents_api_client import SandboxClient
from agents_api_client import SandboxSetClient


def main():
    """Main function demonstrating basic usage of the clients."""

    # Initialize clients
    sandbox_client = SandboxClient(namespace="default")
    sandboxset_client = SandboxSetClient(namespace="default")

    # Example: Create a simple sandbox
    print("Creating a sandbox...")
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

    try:
        created_sandbox = sandbox_client.create_sandbox(sandbox_manifest)
        print(f"Successfully created sandbox: {created_sandbox['metadata']['name']}")
    except Exception as e:
        print(f"Failed to create sandbox: {e}")

    # Example: Create a sandboxset
    print("\nCreating a sandboxset...")
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
            "replicas": 2,
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

    try:
        created_sandboxset = sandboxset_client.create_sandboxset(sandboxset_manifest)
        print(f"Successfully created sandboxset: {created_sandboxset['metadata']['name']}")
        print(f"Replicas: {created_sandboxset['spec']['replicas']}")
    except Exception as e:
        print(f"Failed to create sandboxset: {e}")

    # Example: List sandboxes
    print("\nListing sandboxes...")
    try:
        sandboxes = sandbox_client.list_sandboxes()
        for item in sandboxes.get('items', []):
            print(f"- {item['metadata']['name']}")
    except Exception as e:
        print(f"Failed to list sandboxes: {e}")


if __name__ == "__main__":
    main()
