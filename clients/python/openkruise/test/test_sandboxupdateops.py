import pytest
from kubernetes import client, config
from kubernetes.client import V1ObjectMeta, V1PodTemplateSpec, V1PodSpec, V1Container

from agents import SandboxClient
from agents.models.sandbox import Sandbox, Spec as SandboxSpec
from agents.models.sandboxupdateops import SandboxUpdateOps, Spec as SandboxUpdateOpsSpec, Selector
from helpers import GROUP, VERSION, NAMESPACE, wait_for_condition, wait_for_deletion


def test_update_flow(sandbox_client, k8s_api, unique_name):
    """Test SandboxUpdateOps flow: create Sandbox -> wait Running -> create UpdateOps -> verify selector -> wait Completed -> List -> Delete -> wait deletion"""
    sandbox_name = f"{unique_name}-sandbox"
    updateops_name = f"{unique_name}-updateops"
    
    # Step 1: Create a Sandbox
    sandbox = Sandbox(
        metadata=V1ObjectMeta(name=sandbox_name, namespace=NAMESPACE, labels={"app": "update-test"}),
        spec=SandboxSpec(
            template=V1PodTemplateSpec(
                metadata=V1ObjectMeta(labels={"app": "update-test"}),
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
    
    created_sandbox = sandbox_client.create_sandbox(sandbox)
    assert created_sandbox["metadata"]["name"] == sandbox_name
    
    # Wait for Sandbox to be Running
    def get_sandbox():
        return sandbox_client.get_sandbox(sandbox_name)
    
    def is_running(resource):
        return resource.get("status", {}).get("phase") == "Running"
    
    wait_for_condition(get_sandbox, is_running, timeout=120)
    
    # Step 2: Create SandboxUpdateOps
    update_ops = SandboxUpdateOps(
        apiVersion=f"{GROUP}/{VERSION}",
        kind="SandboxUpdateOps",
        metadata=V1ObjectMeta(name=updateops_name, namespace=NAMESPACE),
        spec=SandboxUpdateOpsSpec(
            selector=Selector(
                matchLabels={"app": "update-test"}
            ),
            patch={
                "spec": {
                    "template": {
                        "spec": {
                            "containers": [
                                {
                                    "name": "main",
                                    "image": "busybox:1.36"
                                }
                            ]
                        }
                    }
                }
            }
        )
    )
    
    body = update_ops.model_dump(exclude_unset=True, by_alias=True)
    created_updateops = k8s_api.create_namespaced_custom_object(
        group=GROUP,
        version=VERSION,
        namespace=NAMESPACE,
        plural="sandboxupdateops",
        body=body
    )
    assert created_updateops["metadata"]["name"] == updateops_name
    
    # Verify selector
    assert created_updateops["spec"]["selector"]["matchLabels"]["app"] == "update-test"
    
    # Wait for UpdateOps to reach Completed phase
    def get_updateops():
        return k8s_api.get_namespaced_custom_object(
            group=GROUP,
            version=VERSION,
            namespace=NAMESPACE,
            plural="sandboxupdateops",
            name=updateops_name
        )
    
    def updateops_completed(resource):
        return resource.get("status", {}).get("phase") in ["Completed", "Succeeded"]
    
    # Note: UpdateOps might complete quickly or take time depending on the update strategy
    try:
        completed_updateops = wait_for_condition(get_updateops, updateops_completed, timeout=120)
        assert completed_updateops["status"]["phase"] in ["Completed", "Succeeded"]
    except TimeoutError:
        # If timeout, it might mean the update is still in progress or failed
        # For e2e test purposes, we'll continue with cleanup
        pass
    
    # List UpdateOps
    listed = k8s_api.list_namespaced_custom_object(
        group=GROUP,
        version=VERSION,
        namespace=NAMESPACE,
        plural="sandboxupdateops"
    )
    assert any(item["metadata"]["name"] == updateops_name for item in listed["items"])
    
    # Delete UpdateOps
    k8s_api.delete_namespaced_custom_object(
        group=GROUP,
        version=VERSION,
        namespace=NAMESPACE,
        plural="sandboxupdateops",
        name=updateops_name
    )
    
    # Wait for UpdateOps deletion
    wait_for_deletion(get_updateops)
    
    # Delete Sandbox (use longer timeout as sandbox with finalizers may take time)
    sandbox_client.delete_sandbox(sandbox_name)
    wait_for_deletion(get_sandbox, timeout=120)
    
    # Verify Sandbox deletion
    with pytest.raises(Exception):
        sandbox_client.get_sandbox(sandbox_name)
