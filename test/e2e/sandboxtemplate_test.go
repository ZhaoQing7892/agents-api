//go:build e2e
// +build e2e

package e2e

import (
	"context"
	"testing"

	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// TestSandboxTemplateCRUD tests Create, Get, List, Update, Delete for SandboxTemplate
func TestSandboxTemplateCRUD(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	clientset := getClientset(t)
	sandboxTemplateClient := clientset.AgentsV1alpha1().SandboxTemplates(testNamespace)

	name := generateTestName("test-sandboxtemplate")

	// Create
	t.Logf("Creating SandboxTemplate: %s", name)
	sandboxTemplate := &agentsv1alpha1.SandboxTemplate{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: testNamespace,
			Labels: map[string]string{
				"app":        "e2e-test",
				"managed-by": "go-sdk",
			},
		},
		Spec: agentsv1alpha1.SandboxTemplateSpec{
			Template: basePodTemplateSpec(map[string]string{"app": "sandboxtemplate-test"}),
		},
	}

	created, err := sandboxTemplateClient.Create(ctx, sandboxTemplate, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("Failed to create SandboxTemplate: %v", err)
	}
	t.Logf("SandboxTemplate created: %s", created.Name)

	defer func() {
		t.Logf("Cleaning up SandboxTemplate: %s", name)
		_ = sandboxTemplateClient.Delete(ctx, name, metav1.DeleteOptions{})
	}()

	// Get
	got, err := sandboxTemplateClient.Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		t.Fatalf("Failed to get SandboxTemplate: %v", err)
	}
	if got.Spec.Template == nil {
		t.Fatalf("Expected template to be set")
	}
	t.Logf("Get SandboxTemplate OK: %s", got.Name)

	// List
	list, err := sandboxTemplateClient.List(ctx, metav1.ListOptions{
		LabelSelector: "app=e2e-test",
	})
	if err != nil {
		t.Fatalf("Failed to list SandboxTemplates: %v", err)
	}
	if len(list.Items) < 1 {
		t.Fatalf("Expected at least 1 SandboxTemplate, got %d", len(list.Items))
	}
	t.Logf("List SandboxTemplates OK: found %d items", len(list.Items))

	// Update
	t.Log("Updating SandboxTemplate...")
	got.Labels["updated"] = "true"
	updated, err := sandboxTemplateClient.Update(ctx, got, metav1.UpdateOptions{})
	if err != nil {
		t.Fatalf("Failed to update SandboxTemplate: %v", err)
	}
	if updated.Labels["updated"] != "true" {
		t.Fatalf("Expected label updated=true after update")
	}
	t.Logf("Update SandboxTemplate OK: %s", updated.Name)

	// Delete
	t.Log("Deleting SandboxTemplate...")
	err = sandboxTemplateClient.Delete(ctx, name, metav1.DeleteOptions{})
	if err != nil {
		t.Fatalf("Failed to delete SandboxTemplate: %v", err)
	}
	t.Logf("Delete SandboxTemplate OK: %s", name)

	// Verify deletion
	_, err = sandboxTemplateClient.Get(ctx, name, metav1.GetOptions{})
	if err == nil {
		t.Fatalf("SandboxTemplate should be deleted but still exists")
	}
	t.Log("Verified SandboxTemplate deleted successfully")
}
