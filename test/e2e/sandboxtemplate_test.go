package e2e

import (
	"time"

	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	"k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"sigs.k8s.io/controller-runtime/pkg/client"
)

var _ = Describe("SandboxTemplate", func() {
	Context("create and verify", func() {
		var templateName string

		BeforeEach(func() {
			templateName = generateTestName("test-sandboxtemplate")
		})

		AfterEach(func() {
			// Cleanup: delete template if it exists
			st := &agentsv1alpha1.SandboxTemplate{
				ObjectMeta: metav1.ObjectMeta{
					Name:      templateName,
					Namespace: Namespace,
				},
			}
			_ = k8sClient.Delete(ctx, st)
		})

		It("should create, get, list, and delete a SandboxTemplate", func() {
			labels := map[string]string{"app": "sandboxtemplate-test"}

			// Create template
			sandboxTemplate := &agentsv1alpha1.SandboxTemplate{
				ObjectMeta: metav1.ObjectMeta{
					Name:      templateName,
					Namespace: Namespace,
					Labels: map[string]string{
						"app":        "e2e-test",
						"managed-by": "ginkgo",
					},
				},
				Spec: agentsv1alpha1.SandboxTemplateSpec{
					Template: basePodTemplateSpec(labels),
				},
			}

			Expect(k8sClient.Create(ctx, sandboxTemplate)).To(Succeed(), "Failed to create SandboxTemplate")

			// Get and verify spec has template
			got := &agentsv1alpha1.SandboxTemplate{}
			Expect(k8sClient.Get(ctx, client.ObjectKey{Name: templateName, Namespace: Namespace}, got)).To(Succeed(), "Failed to get SandboxTemplate")
			Expect(got.Spec.Template).ToNot(BeNil(), "Expected template to be set")
			Expect(got.Spec.Template.Labels).To(HaveKeyWithValue("app", "sandboxtemplate-test"))

			// List by label
			list := &agentsv1alpha1.SandboxTemplateList{}
			Expect(k8sClient.List(ctx, list, client.InNamespace(Namespace), client.MatchingLabels{"app": "e2e-test"})).To(Succeed(), "Failed to list SandboxTemplates")
			Expect(list.Items).ToNot(BeEmpty(), "Expected at least 1 SandboxTemplate in list")

			// Delete
			Expect(k8sClient.Delete(ctx, sandboxTemplate)).To(Succeed(), "Failed to delete SandboxTemplate")

			By("Verifying deletion")
			Eventually(func() bool {
				err := k8sClient.Get(ctx, client.ObjectKey{Name: templateName, Namespace: Namespace}, &agentsv1alpha1.SandboxTemplate{})
				return errors.IsNotFound(err)
			}, time.Second*30, time.Second).Should(BeTrue())
		})
	})
})
