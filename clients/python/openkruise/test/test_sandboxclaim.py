import pytest
from kubernetes import client, config
from kubernetes.client import V1ObjectMeta, V1PodTemplateSpec, V1PodSpec, V1Container

from agents import SandboxSetClient, SandboxClaimClient
from agents.models.sandboxset import SandboxSet, Spec as SandboxSetSpec
from agents.models.sandboxclaim import SandboxClaim, Spec as SandboxClaimSpec
from helpers import wait_for_condition, wait_for_deletion


def test_claim_flow(sandboxset_client, sandboxclaim_client, unique_name):
    """Test SandboxClaim flow: create pool -> wait ready -> create claim -> wait Completed -> verify claimedReplicas -> delete"""
    pool_name = f"{unique_name}-pool"
    claim_name = f"{unique_name}-claim"
    
    # Step 1: Create a SandboxSet pool with replicas=2
    sandboxset = SandboxSet(
        metadata=V1ObjectMeta(name=pool_name, namespace="default", labels={"app": "pool"}),
        spec=SandboxSetSpec(
            replicas=2,
            template=V1PodTemplateSpec(
                metadata=V1ObjectMeta(labels={"app": "sandbox-pool"}),
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
    
    created_pool = sandboxset_client.create_sandboxset(sandboxset)
    assert created_pool["metadata"]["name"] == pool_name
    
    # Wait for pool to be ready (availableReplicas >= 2)
    def get_pool():
        return sandboxset_client.get_sandboxset(pool_name)
    
    def pool_ready(resource):
        return resource.get("status", {}).get("availableReplicas", 0) >= 2
    
    wait_for_condition(get_pool, pool_ready, timeout=120)
    
    # Step 2: Create SandboxClaim
    claim = SandboxClaim(
        metadata=V1ObjectMeta(name=claim_name, namespace="default"),
        spec=SandboxClaimSpec(
            templateName=pool_name,
            replicas=1
        )
    )
    
    created_claim = sandboxclaim_client.create_sandboxclaim(claim)
    assert created_claim["metadata"]["name"] == claim_name
    
    # Wait for claim to reach Completed phase
    def get_claim():
        return sandboxclaim_client.get_sandboxclaim(claim_name)
    
    def claim_completed(resource):
        return resource.get("status", {}).get("phase") == "Completed"
    
    completed_claim = wait_for_condition(get_claim, claim_completed, timeout=120)
    assert completed_claim["status"]["phase"] == "Completed"
    
    # Verify claimedReplicas
    assert completed_claim["status"].get("claimedReplicas", 0) >= 1
    
    # Step 3: Delete claim
    sandboxclaim_client.delete_sandboxclaim(claim_name)
    wait_for_deletion(get_claim)
    
    # Step 4: Delete pool
    sandboxset_client.delete_sandboxset(pool_name)
    wait_for_deletion(get_pool)
