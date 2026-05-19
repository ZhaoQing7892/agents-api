//go:build e2e
// +build e2e

/*
Copyright 2025 The OpenKruise Authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package e2e

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"testing"
	"time"

	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	kruiseclient "github.com/openkruise/agents-api/client/clientset/versioned"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/tools/clientcmd"
)

const (
	testNamespace = "default"
	testTimeout   = 60 * time.Second
)

func getClientset(t *testing.T) *kruiseclient.Clientset {
	t.Helper()
	kubeconfig := os.Getenv("KUBECONFIG")
	if kubeconfig == "" {
		kubeconfig = os.Getenv("HOME") + "/.kube/config"
	}
	config, err := clientcmd.BuildConfigFromFlags("", kubeconfig)
	if err != nil {
		t.Fatalf("Failed to build config: %v", err)
	}
	clientset, err := kruiseclient.NewForConfig(config)
	if err != nil {
		t.Fatalf("Failed to create clientset: %v", err)
	}
	return clientset
}

func generateTestName(prefix string) string {
	return fmt.Sprintf("%s-%d", prefix, time.Now().UnixNano()%10000)
}

func basePodTemplateSpec(labels map[string]string) *corev1.PodTemplateSpec {
	return &corev1.PodTemplateSpec{
		ObjectMeta: metav1.ObjectMeta{
			Labels: labels,
		},
		Spec: corev1.PodSpec{
			RestartPolicy: corev1.RestartPolicyNever,
			Containers: []corev1.Container{
				{
					Name:  "main",
					Image: "busybox:latest",
					Command: []string{
						"sh", "-c", "sleep 3600",
					},
				},
			},
		},
	}
}

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

	// Cleanup
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

// TestSandboxSetCRUD tests Create, Get, List, Update, Delete for SandboxSet
func TestSandboxSetCRUD(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	clientset := getClientset(t)
	sandboxSetClient := clientset.AgentsV1alpha1().SandboxSets(testNamespace)

	name := generateTestName("test-sandboxset")

	// Create
	t.Logf("Creating SandboxSet: %s", name)
	replicas := int32(1)
	sandboxSet := &agentsv1alpha1.SandboxSet{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: testNamespace,
			Labels: map[string]string{
				"app":        "e2e-test",
				"managed-by": "go-sdk",
			},
		},
		Spec: agentsv1alpha1.SandboxSetSpec{
			Replicas: replicas,
			EmbeddedSandboxTemplate: agentsv1alpha1.EmbeddedSandboxTemplate{
				Template: basePodTemplateSpec(map[string]string{"app": "sandboxset-test"}),
			},
		},
	}

	created, err := sandboxSetClient.Create(ctx, sandboxSet, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("Failed to create SandboxSet: %v", err)
	}
	t.Logf("SandboxSet created: %s", created.Name)

	defer func() {
		t.Logf("Cleaning up SandboxSet: %s", name)
		_ = sandboxSetClient.Delete(ctx, name, metav1.DeleteOptions{})
	}()

	// Get
	got, err := sandboxSetClient.Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		t.Fatalf("Failed to get SandboxSet: %v", err)
	}
	if got.Spec.Replicas != 1 {
		t.Fatalf("Expected replicas=1, got %d", got.Spec.Replicas)
	}
	t.Logf("Get SandboxSet OK: %s, replicas=%d", got.Name, got.Spec.Replicas)

	// List
	list, err := sandboxSetClient.List(ctx, metav1.ListOptions{
		LabelSelector: "app=e2e-test",
	})
	if err != nil {
		t.Fatalf("Failed to list SandboxSets: %v", err)
	}
	if len(list.Items) < 1 {
		t.Fatalf("Expected at least 1 SandboxSet, got %d", len(list.Items))
	}
	t.Logf("List SandboxSets OK: found %d items", len(list.Items))

	// Update
	t.Log("Updating SandboxSet...")
	got.Labels["updated"] = "true"
	updated, err := sandboxSetClient.Update(ctx, got, metav1.UpdateOptions{})
	if err != nil {
		t.Fatalf("Failed to update SandboxSet: %v", err)
	}
	if updated.Labels["updated"] != "true" {
		t.Fatalf("Expected label updated=true after update")
	}
	t.Logf("Update SandboxSet OK: %s", updated.Name)

	// Delete
	t.Log("Deleting SandboxSet...")
	err = sandboxSetClient.Delete(ctx, name, metav1.DeleteOptions{})
	if err != nil {
		t.Fatalf("Failed to delete SandboxSet: %v", err)
	}
	t.Logf("Delete SandboxSet OK: %s", name)

	// Verify deletion
	_, err = sandboxSetClient.Get(ctx, name, metav1.GetOptions{})
	if err == nil {
		t.Fatalf("SandboxSet should be deleted but still exists")
	}
	t.Log("Verified SandboxSet deleted successfully")
}

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

// TestCheckpointCRUD tests Create, Get, List, Update, Delete for Checkpoint
func TestCheckpointCRUD(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	clientset := getClientset(t)
	checkpointClient := clientset.AgentsV1alpha1().Checkpoints(testNamespace)

	name := generateTestName("test-checkpoint")

	// Create
	t.Logf("Creating Checkpoint: %s", name)
	sandboxName := "test-sandbox"
	podName := "test-pod"
	checkpoint := &agentsv1alpha1.Checkpoint{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: testNamespace,
			Labels: map[string]string{
				"app":        "e2e-test",
				"managed-by": "go-sdk",
			},
		},
		Spec: agentsv1alpha1.CheckpointSpec{
			SandboxName: &sandboxName,
			PodName:     &podName,
		},
	}

	created, err := checkpointClient.Create(ctx, checkpoint, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("Failed to create Checkpoint: %v", err)
	}
	t.Logf("Checkpoint created: %s", created.Name)

	defer func() {
		t.Logf("Cleaning up Checkpoint: %s", name)
		_ = checkpointClient.Delete(ctx, name, metav1.DeleteOptions{})
	}()

	// Get
	got, err := checkpointClient.Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		t.Fatalf("Failed to get Checkpoint: %v", err)
	}
	if got.Spec.SandboxName == nil || *got.Spec.SandboxName != sandboxName {
		t.Fatalf("Expected sandboxName=%s, got %v", sandboxName, got.Spec.SandboxName)
	}
	t.Logf("Get Checkpoint OK: %s, sandboxName=%s", got.Name, *got.Spec.SandboxName)

	// List
	list, err := checkpointClient.List(ctx, metav1.ListOptions{
		LabelSelector: "app=e2e-test",
	})
	if err != nil {
		t.Fatalf("Failed to list Checkpoints: %v", err)
	}
	if len(list.Items) < 1 {
		t.Fatalf("Expected at least 1 Checkpoint, got %d", len(list.Items))
	}
	t.Logf("List Checkpoints OK: found %d items", len(list.Items))

	// Update
	t.Log("Updating Checkpoint...")
	got.Labels["updated"] = "true"
	updated, err := checkpointClient.Update(ctx, got, metav1.UpdateOptions{})
	if err != nil {
		t.Fatalf("Failed to update Checkpoint: %v", err)
	}
	if updated.Labels["updated"] != "true" {
		t.Fatalf("Expected label updated=true after update")
	}
	t.Logf("Update Checkpoint OK: %s", updated.Name)

	// Delete
	t.Log("Deleting Checkpoint...")
	err = checkpointClient.Delete(ctx, name, metav1.DeleteOptions{})
	if err != nil {
		t.Fatalf("Failed to delete Checkpoint: %v", err)
	}
	t.Logf("Delete Checkpoint OK: %s", name)

	// Verify deletion
	_, err = checkpointClient.Get(ctx, name, metav1.GetOptions{})
	if err == nil {
		t.Fatalf("Checkpoint should be deleted but still exists")
	}
	t.Log("Verified Checkpoint deleted successfully")
}

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

// TestSandboxUpdateOpsCRUD tests Create, Get, List, Update/Patch, Delete for SandboxUpdateOps
func TestSandboxUpdateOpsCRUD(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	clientset := getClientset(t)
	sandboxUpdateOpsClient := clientset.AgentsV1alpha1().SandboxUpdateOps(testNamespace)

	name := generateTestName("test-sandboxupdateops")

	// Create patch data
	patchData := map[string]interface{}{
		"spec": map[string]interface{}{
			"template": map[string]interface{}{
				"spec": map[string]interface{}{
					"containers": []map[string]interface{}{
						{
							"name":  "main",
							"image": "busybox:1.36",
						},
					},
				},
			},
		},
	}
	patchJSON, err := json.Marshal(patchData)
	if err != nil {
		t.Fatalf("Failed to marshal patch data: %v", err)
	}

	// Create
	t.Logf("Creating SandboxUpdateOps: %s", name)
	sandboxUpdateOps := &agentsv1alpha1.SandboxUpdateOps{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: testNamespace,
			Labels: map[string]string{
				"app":        "e2e-test",
				"managed-by": "go-sdk",
			},
		},
		Spec: agentsv1alpha1.SandboxUpdateOpsSpec{
			Selector: &metav1.LabelSelector{
				MatchLabels: map[string]string{
					"app": "sandbox-test",
				},
			},
			Patch: runtime.RawExtension{
				Raw: patchJSON,
			},
		},
	}

	created, err := sandboxUpdateOpsClient.Create(ctx, sandboxUpdateOps, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("Failed to create SandboxUpdateOps: %v", err)
	}
	t.Logf("SandboxUpdateOps created: %s", created.Name)

	defer func() {
		t.Logf("Cleaning up SandboxUpdateOps: %s", name)
		_ = sandboxUpdateOpsClient.Delete(ctx, name, metav1.DeleteOptions{})
	}()

	// Get
	got, err := sandboxUpdateOpsClient.Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		t.Fatalf("Failed to get SandboxUpdateOps: %v", err)
	}
	if got.Spec.Selector == nil {
		t.Fatalf("Expected selector to be set")
	}
	t.Logf("Get SandboxUpdateOps OK: %s", got.Name)

	// List
	list, err := sandboxUpdateOpsClient.List(ctx, metav1.ListOptions{
		LabelSelector: "app=e2e-test",
	})
	if err != nil {
		t.Fatalf("Failed to list SandboxUpdateOps: %v", err)
	}
	if len(list.Items) < 1 {
		t.Fatalf("Expected at least 1 SandboxUpdateOps, got %d", len(list.Items))
	}
	t.Logf("List SandboxUpdateOps OK: found %d items", len(list.Items))

	// Update
	t.Log("Updating SandboxUpdateOps...")
	got.Labels["updated"] = "true"
	updated, err := sandboxUpdateOpsClient.Update(ctx, got, metav1.UpdateOptions{})
	if err != nil {
		t.Fatalf("Failed to update SandboxUpdateOps: %v", err)
	}
	if updated.Labels["updated"] != "true" {
		t.Fatalf("Expected label updated=true after update")
	}
	t.Logf("Update SandboxUpdateOps OK: %s", updated.Name)

	// Delete
	t.Log("Deleting SandboxUpdateOps...")
	err = sandboxUpdateOpsClient.Delete(ctx, name, metav1.DeleteOptions{})
	if err != nil {
		t.Fatalf("Failed to delete SandboxUpdateOps: %v", err)
	}
	t.Logf("Delete SandboxUpdateOps OK: %s", name)

	// Verify deletion
	_, err = sandboxUpdateOpsClient.Get(ctx, name, metav1.GetOptions{})
	if err == nil {
		t.Fatalf("SandboxUpdateOps should be deleted but still exists")
	}
	t.Log("Verified SandboxUpdateOps deleted successfully")
}
