# Copyright 2025 The OpenKruise Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""E2E tests for Python SDK - Sandbox and SandboxSet CRUD operations."""

import time
import pytest
from kubernetes import client, config
from kubernetes.client.rest import ApiException

GROUP = "agents.kruise.io"
VERSION = "v1alpha1"
NAMESPACE = "default"


@pytest.fixture(autouse=True)
def k8s_api():
    try:
        config.load_incluster_config()
    except config.ConfigException:
        config.load_kube_config()
    return client.CustomObjectsApi()


@pytest.fixture
def unique_name():
    return f"e2e-py-{int(time.time()) % 100000}"


def build_sandbox(name):
    """Build a Sandbox CR dict."""
    return {
        "apiVersion": f"{GROUP}/{VERSION}",
        "kind": "Sandbox",
        "metadata": {
            "name": name,
            "namespace": NAMESPACE,
            "labels": {"app": "e2e-test-python", "managed-by": "python-sdk"},
        },
        "spec": {
            "template": {
                "metadata": {"labels": {"app": "sandbox-test"}},
                "spec": {
                    "restartPolicy": "Never",
                    "containers": [
                        {
                            "name": "main",
                            "image": "busybox:latest",
                            "command": ["sh", "-c", "sleep 3600"],
                        }
                    ],
                },
            },
        },
    }


def build_sandboxset(name, replicas=1):
    """Build a SandboxSet CR dict."""
    return {
        "apiVersion": f"{GROUP}/{VERSION}",
        "kind": "SandboxSet",
        "metadata": {
            "name": name,
            "namespace": NAMESPACE,
            "labels": {"app": "e2e-test-python", "managed-by": "python-sdk"},
        },
        "spec": {
            "replicas": replicas,
            "template": {
                "spec": {
                    "template": {
                        "metadata": {"labels": {"app": "sandboxset-test"}},
                        "spec": {
                            "restartPolicy": "Never",
                            "containers": [
                                {
                                    "name": "main",
                                    "image": "busybox:latest",
                                    "command": ["sh", "-c", "sleep 3600"],
                                }
                            ],
                        },
                    },
                },
            },
        },
    }


class TestSandboxCRUD:
    """Test Sandbox CRUD operations via Python SDK."""

    def test_create_and_get(self, k8s_api, unique_name):
        name = f"{unique_name}-sandbox"
        sandbox = build_sandbox(name)
        try:
            result = k8s_api.create_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxes", sandbox
            )
            assert result["metadata"]["name"] == name

            got = k8s_api.get_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxes", name
            )
            assert got["metadata"]["name"] == name
            assert got["metadata"]["labels"]["app"] == "e2e-test-python"
        finally:
            try:
                k8s_api.delete_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "sandboxes", name
                )
            except ApiException:
                pass

    def test_list(self, k8s_api, unique_name):
        name = f"{unique_name}-sandbox-list"
        sandbox = build_sandbox(name)
        try:
            k8s_api.create_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxes", sandbox
            )
            listing = k8s_api.list_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxes",
                label_selector="app=e2e-test-python"
            )
            assert len(listing["items"]) >= 1
        finally:
            try:
                k8s_api.delete_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "sandboxes", name
                )
            except ApiException:
                pass

    def test_patch(self, k8s_api, unique_name):
        name = f"{unique_name}-sandbox-patch"
        sandbox = build_sandbox(name)
        try:
            k8s_api.create_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxes", sandbox
            )
            patch_body = {"metadata": {"labels": {"updated": "true"}}}
            patched = k8s_api.patch_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxes", name, patch_body
            )
            assert patched["metadata"]["labels"]["updated"] == "true"
        finally:
            try:
                k8s_api.delete_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "sandboxes", name
                )
            except ApiException:
                pass

    def test_delete(self, k8s_api, unique_name):
        name = f"{unique_name}-sandbox-del"
        sandbox = build_sandbox(name)
        k8s_api.create_namespaced_custom_object(
            GROUP, VERSION, NAMESPACE, "sandboxes", sandbox
        )
        k8s_api.delete_namespaced_custom_object(
            GROUP, VERSION, NAMESPACE, "sandboxes", name
        )
        with pytest.raises(ApiException) as exc_info:
            k8s_api.get_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxes", name
            )
        assert exc_info.value.status == 404


class TestSandboxSetCRUD:
    """Test SandboxSet CRUD operations via Python SDK."""

    def test_create_and_get(self, k8s_api, unique_name):
        name = f"{unique_name}-sandboxset"
        sandboxset = build_sandboxset(name, replicas=1)
        try:
            result = k8s_api.create_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxsets", sandboxset
            )
            assert result["metadata"]["name"] == name
            assert result["spec"]["replicas"] == 1

            got = k8s_api.get_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxsets", name
            )
            assert got["spec"]["replicas"] == 1
        finally:
            try:
                k8s_api.delete_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "sandboxsets", name
                )
            except ApiException:
                pass

    def test_delete(self, k8s_api, unique_name):
        name = f"{unique_name}-sandboxset-del"
        sandboxset = build_sandboxset(name)
        k8s_api.create_namespaced_custom_object(
            GROUP, VERSION, NAMESPACE, "sandboxsets", sandboxset
        )
        k8s_api.delete_namespaced_custom_object(
            GROUP, VERSION, NAMESPACE, "sandboxsets", name
        )
        with pytest.raises(ApiException) as exc_info:
            k8s_api.get_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxsets", name
            )
        assert exc_info.value.status == 404


class TestSandboxClaimCRUD:
    """Test SandboxClaim CRUD operations via Python SDK."""

    def test_create_and_get(self, k8s_api, unique_name):
        name = f"{unique_name}-claim"
        claim = {
            "apiVersion": f"{GROUP}/{VERSION}",
            "kind": "SandboxClaim",
            "metadata": {
                "name": name,
                "namespace": NAMESPACE,
                "labels": {"app": "e2e-test-python"},
            },
            "spec": {
                "sandboxSetRef": {"name": "nonexistent-set"},
            },
        }
        try:
            result = k8s_api.create_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxclaims", claim
            )
            assert result["metadata"]["name"] == name

            got = k8s_api.get_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxclaims", name
            )
            assert got["metadata"]["name"] == name
        finally:
            try:
                k8s_api.delete_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "sandboxclaims", name
                )
            except ApiException:
                pass


class TestCheckpointCRUD:
    """Test Checkpoint CRUD operations via Python SDK."""

    def test_crud(self, k8s_api, unique_name):
        name = f"{unique_name}-checkpoint"
        checkpoint = {
            "apiVersion": f"{GROUP}/{VERSION}",
            "kind": "Checkpoint",
            "metadata": {
                "name": name,
                "namespace": NAMESPACE,
                "labels": {"app": "e2e-test-python"},
            },
            "spec": {
                "sandboxName": "test-sandbox",
                "podName": "test-pod",
            },
        }
        try:
            # Create
            result = k8s_api.create_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "checkpoints", checkpoint
            )
            assert result["metadata"]["name"] == name

            # Get
            got = k8s_api.get_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "checkpoints", name
            )
            assert got["metadata"]["name"] == name
            assert got["spec"]["sandboxName"] == "test-sandbox"

            # List
            listing = k8s_api.list_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "checkpoints",
                label_selector="app=e2e-test-python"
            )
            assert len(listing["items"]) >= 1

            # Delete
            k8s_api.delete_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "checkpoints", name
            )
            with pytest.raises(ApiException) as exc_info:
                k8s_api.get_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "checkpoints", name
                )
            assert exc_info.value.status == 404
        finally:
            try:
                k8s_api.delete_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "checkpoints", name
                )
            except ApiException:
                pass


class TestSandboxTemplateCRUD:
    """Test SandboxTemplate CRUD operations via Python SDK."""

    def test_crud(self, k8s_api, unique_name):
        name = f"{unique_name}-template"
        template = {
            "apiVersion": f"{GROUP}/{VERSION}",
            "kind": "SandboxTemplate",
            "metadata": {
                "name": name,
                "namespace": NAMESPACE,
                "labels": {"app": "e2e-test-python"},
            },
            "spec": {
                "template": {
                    "metadata": {"labels": {"app": "template-test"}},
                    "spec": {
                        "restartPolicy": "Never",
                        "containers": [
                            {
                                "name": "main",
                                "image": "busybox:latest",
                                "command": ["sh", "-c", "sleep 3600"],
                            }
                        ],
                    },
                },
            },
        }
        try:
            # Create
            result = k8s_api.create_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxtemplates", template
            )
            assert result["metadata"]["name"] == name

            # Get
            got = k8s_api.get_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxtemplates", name
            )
            assert got["metadata"]["name"] == name

            # List
            listing = k8s_api.list_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxtemplates",
                label_selector="app=e2e-test-python"
            )
            assert len(listing["items"]) >= 1

            # Delete
            k8s_api.delete_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxtemplates", name
            )
            with pytest.raises(ApiException) as exc_info:
                k8s_api.get_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "sandboxtemplates", name
                )
            assert exc_info.value.status == 404
        finally:
            try:
                k8s_api.delete_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "sandboxtemplates", name
                )
            except ApiException:
                pass


class TestSandboxUpdateOpsCRUD:
    """Test SandboxUpdateOps CRUD operations via Python SDK."""

    def test_crud(self, k8s_api, unique_name):
        name = f"{unique_name}-updateops"
        updateops = {
            "apiVersion": f"{GROUP}/{VERSION}",
            "kind": "SandboxUpdateOps",
            "metadata": {
                "name": name,
                "namespace": NAMESPACE,
                "labels": {"app": "e2e-test-python"},
            },
            "spec": {
                "selector": {
                    "matchLabels": {"app": "target-sandbox"},
                },
                "patch": {
                    "spec": {
                        "template": {
                            "spec": {
                                "containers": [
                                    {"name": "main", "image": "busybox:1.36"}
                                ]
                            }
                        }
                    }
                },
            },
        }
        try:
            # Create
            result = k8s_api.create_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxupdateops", updateops
            )
            assert result["metadata"]["name"] == name

            # Get
            got = k8s_api.get_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxupdateops", name
            )
            assert got["metadata"]["name"] == name
            assert got["spec"]["selector"]["matchLabels"]["app"] == "target-sandbox"

            # List
            listing = k8s_api.list_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxupdateops",
                label_selector="app=e2e-test-python"
            )
            assert len(listing["items"]) >= 1

            # Delete
            k8s_api.delete_namespaced_custom_object(
                GROUP, VERSION, NAMESPACE, "sandboxupdateops", name
            )
            with pytest.raises(ApiException) as exc_info:
                k8s_api.get_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "sandboxupdateops", name
                )
            assert exc_info.value.status == 404
        finally:
            try:
                k8s_api.delete_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, "sandboxupdateops", name
                )
            except ApiException:
                pass
