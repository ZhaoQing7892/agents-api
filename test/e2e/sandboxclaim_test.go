//go:build e2e

package e2e

import (
	"context"
	"testing"

	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// TestSandboxClaimCRUD tests Create, Get, List, Update, Delete for SandboxClaim
func TestSandboxClaimCRUD(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	clientset := getClientset(t)
	sandboxClaimClient := clientset.AgentsV1alpha1().SandboxClaims(testNamespace)

	name := generateTestName("test-sandboxclaim")

	// Create
	t.Logf("Creating SandboxClaim: %s", name)
	replicas := int32(1)
	sandboxClaim := &agentsv1alpha1.SandboxClaim{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: testNamespace,
			Labels: map[string]string{
				"app":        "e2e-test",
				"managed-by": "go-sdk",
			},
		},
		Spec: agentsv1alpha1.SandboxClaimSpec{
			TemplateName: "test-template",
			Replicas:     &replicas,
		},
	}

	created, err := sandboxClaimClient.Create(ctx, sandboxClaim, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("Failed to create SandboxClaim: %v", err)
	}
	t.Logf("SandboxClaim created: %s", created.Name)

	defer func() {
		t.Logf("Cleaning up SandboxClaim: %s", name)
		_ = sandboxClaimClient.Delete(ctx, name, metav1.DeleteOptions{})
	}()

	// Get
	got, err := sandboxClaimClient.Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		t.Fatalf("Failed to get SandboxClaim: %v", err)
	}
	if got.Spec.TemplateName != "test-template" {
		t.Fatalf("Expected templateName=test-template, got %s", got.Spec.TemplateName)
	}
	t.Logf("Get SandboxClaim OK: %s, templateName=%s", got.Name, got.Spec.TemplateName)

	// List
	list, err := sandboxClaimClient.List(ctx, metav1.ListOptions{
		LabelSelector: "app=e2e-test",
	})
	if err != nil {
		t.Fatalf("Failed to list SandboxClaims: %v", err)
	}
	if len(list.Items) < 1 {
		t.Fatalf("Expected at least 1 SandboxClaim, got %d", len(list.Items))
	}
	t.Logf("List SandboxClaims OK: found %d items", len(list.Items))

	// Update
	t.Log("Updating SandboxClaim...")
	got.Labels["updated"] = "true"
	updated, err := sandboxClaimClient.Update(ctx, got, metav1.UpdateOptions{})
	if err != nil {
		t.Fatalf("Failed to update SandboxClaim: %v", err)
	}
	if updated.Labels["updated"] != "true" {
		t.Fatalf("Expected label updated=true after update")
	}
	t.Logf("Update SandboxClaim OK: %s", updated.Name)

	// Delete
	t.Log("Deleting SandboxClaim...")
	err = sandboxClaimClient.Delete(ctx, name, metav1.DeleteOptions{})
	if err != nil {
		t.Fatalf("Failed to delete SandboxClaim: %v", err)
	}
	t.Logf("Delete SandboxClaim OK: %s", name)

	// Verify deletion
	_, err = sandboxClaimClient.Get(ctx, name, metav1.GetOptions{})
	if err == nil {
		t.Fatalf("SandboxClaim should be deleted but still exists")
	}
	t.Log("Verified SandboxClaim deleted successfully")
}
