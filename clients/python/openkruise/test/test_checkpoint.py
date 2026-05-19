import pytest
from kubernetes import client, config
from kubernetes.client import V1ObjectMeta

from agents.models.checkpoint import Checkpoint, Spec as CheckpointSpec
from helpers import GROUP, VERSION, NAMESPACE, wait_for_deletion


def test_crud(k8s_api, unique_name):
    """Test Checkpoint CRUD operations using CustomObjectsApi: Create -> Get -> List -> Delete -> wait deletion"""
    name = f"{unique_name}-checkpoint"
    
    # Create Checkpoint
    checkpoint = Checkpoint(
        apiVersion=f"{GROUP}/{VERSION}",
        kind="Checkpoint",
        metadata=V1ObjectMeta(name=name, namespace=NAMESPACE),
        spec=CheckpointSpec(
            sandboxName="test-sandbox",
            podName="test-pod"
        )
    )
    
    body = checkpoint.model_dump(exclude_unset=True, by_alias=True)
    created = k8s_api.create_namespaced_custom_object(
        group=GROUP,
        version=VERSION,
        namespace=NAMESPACE,
        plural="checkpoints",
        body=body
    )
    assert created["metadata"]["name"] == name
    
    # Get Checkpoint
    def get_checkpoint():
        return k8s_api.get_namespaced_custom_object(
            group=GROUP,
            version=VERSION,
            namespace=NAMESPACE,
            plural="checkpoints",
            name=name
        )
    
    fetched = get_checkpoint()
    assert fetched["metadata"]["name"] == name
    
    # List Checkpoints
    listed = k8s_api.list_namespaced_custom_object(
        group=GROUP,
        version=VERSION,
        namespace=NAMESPACE,
        plural="checkpoints"
    )
    assert any(item["metadata"]["name"] == name for item in listed["items"])
    
    # Delete Checkpoint
    k8s_api.delete_namespaced_custom_object(
        group=GROUP,
        version=VERSION,
        namespace=NAMESPACE,
        plural="checkpoints",
        name=name
    )
    
    # Wait for deletion
    wait_for_deletion(get_checkpoint)
    
    # Verify deletion
    with pytest.raises(client.exceptions.ApiException) as exc_info:
        get_checkpoint()
    assert exc_info.value.status == 404
