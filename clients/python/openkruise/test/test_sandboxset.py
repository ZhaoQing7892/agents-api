import pytest
from kubernetes import client, config
from kubernetes.client import V1ObjectMeta, V1PodTemplateSpec, V1PodSpec, V1Container

from agents import SandboxSetClient
from agents.models.sandboxset import SandboxSet, Spec as SandboxSetSpec
from helpers import wait_for_condition, wait_for_deletion


def test_create_wait_scale_delete(sandboxset_client, unique_name):
    """Test SandboxSet CRUD operations: create(replicas=2) -> wait available=2 -> patch scale to 3 -> wait available=3 -> delete"""
    name = f"{unique_name}-sandboxset"
    
    # Create SandboxSet with replicas=2
    sandboxset = SandboxSet(
        metadata=V1ObjectMeta(name=name, namespace="default", labels={"app": "test"}),
        spec=SandboxSetSpec(
            replicas=2,
            template=V1PodTemplateSpec(
                metadata=V1ObjectMeta(labels={"app": "sandboxset-test"}),
                spec=V1PodSpec(
                    restart_policy="Never",
                    containers=[V1Container(
                        name="main",
                        image="busybox:latest",
                        command=["sh", "-c", "sleep 3600"]
                    )]
                )
            )
        )
    )
    
    created = sandboxset_client.create_sandboxset(sandboxset)
    assert created["metadata"]["name"] == name
    
    # Wait for availableReplicas=2
    def get_sandboxset():
        return sandboxset_client.get_sandboxset(name)
    
    def has_available_replicas(resource):
        return resource.get("status", {}).get("availableReplicas", 0) >= 2
    
    running_sandboxset = wait_for_condition(get_sandboxset, has_available_replicas, timeout=120)
    assert running_sandboxset["status"]["availableReplicas"] >= 2
    
    # Patch to scale to 3
    patch_body = {
        "spec": {
            "replicas": 3
        }
    }
    patched = sandboxset_client.update_sandboxset(name, patch_body)
    assert patched["spec"]["replicas"] == 3
    
    # Wait for availableReplicas=3
    def has_three_available(resource):
        return resource.get("status", {}).get("availableReplicas", 0) >= 3
    
    scaled_sandboxset = wait_for_condition(get_sandboxset, has_three_available, timeout=120)
    assert scaled_sandboxset["status"]["availableReplicas"] >= 3
    
    # Delete sandboxset
    sandboxset_client.delete_sandboxset(name)
    
    # Wait for deletion
    wait_for_deletion(get_sandboxset)
    
    # Verify deletion
    with pytest.raises(Exception):
        sandboxset_client.get_sandboxset(name)
