import pytest
from kubernetes import client, config
from kubernetes.client import V1ObjectMeta, V1PodTemplateSpec, V1PodSpec, V1Container

from agents.models.sandboxtemplate import SandboxTemplate, Spec as SandboxTemplateSpec
from helpers import GROUP, VERSION, NAMESPACE, wait_for_deletion


def test_crud(k8s_api, unique_name):
    """Test SandboxTemplate CRUD operations using CustomObjectsApi: Create -> Get -> List -> Delete -> wait deletion"""
    name = f"{unique_name}-template"
    
    # Create SandboxTemplate
    template = SandboxTemplate(
        apiVersion=f"{GROUP}/{VERSION}",
        kind="SandboxTemplate",
        metadata=V1ObjectMeta(name=name, namespace=NAMESPACE),
        spec=SandboxTemplateSpec(
            template=V1PodTemplateSpec(
                metadata=V1ObjectMeta(labels={"app": "template-test"}),
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
    
    body = template.model_dump(exclude_unset=True, by_alias=True)
    created = k8s_api.create_namespaced_custom_object(
        group=GROUP,
        version=VERSION,
        namespace=NAMESPACE,
        plural="sandboxtemplates",
        body=body
    )
    assert created["metadata"]["name"] == name
    
    # Get SandboxTemplate
    def get_template():
        return k8s_api.get_namespaced_custom_object(
            group=GROUP,
            version=VERSION,
            namespace=NAMESPACE,
            plural="sandboxtemplates",
            name=name
        )
    
    fetched = get_template()
    assert fetched["metadata"]["name"] == name
    
    # List SandboxTemplates
    listed = k8s_api.list_namespaced_custom_object(
        group=GROUP,
        version=VERSION,
        namespace=NAMESPACE,
        plural="sandboxtemplates"
    )
    assert any(item["metadata"]["name"] == name for item in listed["items"])
    
    # Delete SandboxTemplate
    k8s_api.delete_namespaced_custom_object(
        group=GROUP,
        version=VERSION,
        namespace=NAMESPACE,
        plural="sandboxtemplates",
        name=name
    )
    
    # Wait for deletion
    wait_for_deletion(get_template)
    
    # Verify deletion
    with pytest.raises(client.exceptions.ApiException) as exc_info:
        get_template()
    assert exc_info.value.status == 404
