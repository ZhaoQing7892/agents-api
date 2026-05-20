//go:build e2e

package e2e

import (
	"context"
	"testing"

	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// TestSandboxCRUD tests Create, Get, List, Update, Delete for Sandbox
func TestSandboxCRUD(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	clientset := getClientset(t)
	sandboxClient := clientset.AgentsV1alpha1().Sandboxes(testNamespace)

	name := generateTestName("test-sandbox")

	// Create
	t.Logf("Creating Sandbox: %s", name)
	sandbox := &agentsv1alpha1.Sandbox{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: testNamespace,
			Labels: map[string]string{
				"app":        "e2e-test",
				"managed-by": "go-sdk",
			},
		},
		Spec: agentsv1alpha1.SandboxSpec{
			EmbeddedSandboxTemplate: agentsv1alpha1.EmbeddedSandboxTemplate{
				Template: basePodTemplateSpec(map[string]string{"app": "sandbox-test"}),
			},
		},
	}

	created, err := sandboxClient.Create(ctx, sandbox, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("Failed to create Sandbox: %v", err)
	}
	t.Logf("Sandbox created: %s (UID: %s)", created.Name, created.UID)

	defer func() {
		t.Logf("Cleaning up Sandbox: %s", name)
		_ = sandboxClient.Delete(ctx, name, metav1.DeleteOptions{})
	}()

	// Get
	t.Log("Getting Sandbox...")
	got, err := sandboxClient.Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		t.Fatalf("Failed to get Sandbox: %v", err)
	}
	if got.Name != name {
		t.Fatalf("Expected name %s, got %s", name, got.Name)
	}
	if got.Labels["app"] != "e2e-test" {
		t.Fatalf("Expected label app=e2e-test, got %s", got.Labels["app"])
	}
	t.Logf("Get Sandbox OK: %s", got.Name)

	// List
	t.Log("Listing Sandboxes...")
	list, err := sandboxClient.List(ctx, metav1.ListOptions{
		LabelSelector: "app=e2e-test",
	})
	if err != nil {
		t.Fatalf("Failed to list Sandboxes: %v", err)
	}
	if len(list.Items) < 1 {
		t.Fatalf("Expected at least 1 Sandbox, got %d", len(list.Items))
	}
	t.Logf("List Sandboxes OK: found %d items", len(list.Items))

	// Update
	t.Log("Updating Sandbox...")
	got.Labels["updated"] = "true"
	updated, err := sandboxClient.Update(ctx, got, metav1.UpdateOptions{})
	if err != nil {
		t.Fatalf("Failed to update Sandbox: %v", err)
	}
	if updated.Labels["updated"] != "true" {
		t.Fatalf("Expected label updated=true after update")
	}
	t.Logf("Update Sandbox OK: %s", updated.Name)

	// Delete
	t.Log("Deleting Sandbox...")
	err = sandboxClient.Delete(ctx, name, metav1.DeleteOptions{})
	if err != nil {
		t.Fatalf("Failed to delete Sandbox: %v", err)
	}
	t.Logf("Delete Sandbox OK: %s", name)

	// Verify deletion
	_, err = sandboxClient.Get(ctx, name, metav1.GetOptions{})
	if err == nil {
		t.Fatalf("Sandbox should be deleted but still exists")
	}
	t.Log("Verified Sandbox deleted successfully")
}
