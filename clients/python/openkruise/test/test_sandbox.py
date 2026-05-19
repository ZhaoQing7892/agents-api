import pytest
from kubernetes import client, config
from kubernetes.client import V1ObjectMeta, V1PodTemplateSpec, V1PodSpec, V1Container

from agents import SandboxClient
from agents.models.sandbox import Sandbox, Spec as SandboxSpec
from helpers import wait_for_condition, wait_for_deletion


def test_create_wait_running_patch_list_delete(sandbox_client, unique_name):
    """Test Sandbox CRUD operations: create -> wait Running -> patch labels -> list -> delete -> wait deletion"""
    name = f"{unique_name}-sandbox"
    
    # Create Sandbox
    sandbox = Sandbox(
        metadata=V1ObjectMeta(name=name, namespace="default", labels={"app": "test"}),
        spec=SandboxSpec(
            template=V1PodTemplateSpec(
                metadata=V1ObjectMeta(labels={"app": "sandbox-test"}),
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
    created = sandbox_client.create_sandbox(sandbox)
    assert created["metadata"]["name"] == name
    
    # Wait for Running phase
    def get_sandbox():
        return sandbox_client.get_sandbox(name)
    
    def is_running(resource):
        return resource.get("status", {}).get("phase") == "Running"
    
    running_sandbox = wait_for_condition(get_sandbox, is_running, timeout=120)
    assert running_sandbox["status"]["phase"] == "Running"
    
    # Patch labels using dict (Sandbox model requires spec, so use raw dict for patch)
    patch_body = {
        "metadata": {
            "labels": {"app": "test", "patched": "true"}
        }
    }
    patched = sandbox_client.update_sandbox(name, patch_body)
    assert patched["metadata"]["labels"].get("patched") == "true"
    
    # List sandboxes
    try:
        config.load_incluster_config()
    except config.ConfigException:
        config.load_kube_config()
    api = client.CustomObjectsApi()
    listed = api.list_namespaced_custom_object(
        group="agents.kruise.io",
        version="v1alpha1",
        namespace="default",
        plural="sandboxes"
    )
    assert any(item["metadata"]["name"] == name for item in listed["items"])
    
    # Delete sandbox
    sandbox_client.delete_sandbox(name)
    
    # Wait for deletion
    wait_for_deletion(get_sandbox, timeout=120)
    
    # Verify deletion
    with pytest.raises(Exception):
        sandbox_client.get_sandbox(name)
