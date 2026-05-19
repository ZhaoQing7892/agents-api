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

package io.openkruise.agents.client.e2e.v2;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

import io.openkruise.agents.client.v2.models.*;
import io.openkruise.agents.client.v2.models.sandboxupdateopsspec.Selector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * E2E tests for Java v2 SDK using fabric8 typed client.
 * Covers all 6 CRDs: Sandbox, SandboxSet, SandboxClaim, Checkpoint, SandboxTemplate, SandboxUpdateOps.
 */
public class SdkTest {

    private static final String NAMESPACE = "default";

    private KubernetesClient client;
    private final List<Runnable> cleanupActions = new ArrayList<>();

    @Before
    public void setUp() {
        client = new KubernetesClientBuilder().build();
    }

    @After
    public void tearDown() {
        // Execute cleanup in reverse order
        for (int i = cleanupActions.size() - 1; i >= 0; i--) {
            try {
                cleanupActions.get(i).run();
            } catch (Exception ignored) {}
        }
        cleanupActions.clear();
        if (client != null) {
            client.close();
        }
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + System.currentTimeMillis() % 10000;
    }

    private PodTemplateSpec basePodTemplate(String appLabel) {
        PodTemplateSpec template = new PodTemplateSpec();
        ObjectMeta meta = new ObjectMeta();
        Map<String, String> labels = new HashMap<>();
        labels.put("app", appLabel);
        meta.setLabels(labels);
        template.setMetadata(meta);

        PodSpec podSpec = new PodSpec();
        podSpec.setRestartPolicy("Never");
        Container container = new Container();
        container.setName("main");
        container.setImage("busybox:latest");
        container.setCommand(Arrays.asList("sh", "-c", "sleep 3600"));
        podSpec.setContainers(Collections.singletonList(container));
        template.setSpec(podSpec);

        return template;
    }

    // ======================== Sandbox CRUD ========================

    @Test
    public void testSandboxCRUD() {
        String name = uniqueName("e2e-sandbox");
        MixedOperation<Sandbox, KubernetesResourceList<Sandbox>, Resource<Sandbox>> sandboxes =
                client.resources(Sandbox.class);

        // Build
        Sandbox sandbox = new Sandbox();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(NAMESPACE);
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "e2e-test-java");
        labels.put("managed-by", "java-v2-sdk");
        meta.setLabels(labels);
        sandbox.setMetadata(meta);

        SandboxSpec spec = new SandboxSpec();
        spec.setTemplate(basePodTemplate("sandbox-test"));
        sandbox.setSpec(spec);

        // Create
        Sandbox created = sandboxes.inNamespace(NAMESPACE).resource(sandbox).create();
        assertNotNull(created);
        assertEquals(name, created.getMetadata().getName());
        System.out.println("Created Sandbox: " + name);
        cleanupActions.add(() -> sandboxes.inNamespace(NAMESPACE).withName(name).delete());

        // Get
        Sandbox got = sandboxes.inNamespace(NAMESPACE).withName(name).get();
        assertNotNull(got);
        assertEquals(name, got.getMetadata().getName());
        assertEquals("e2e-test-java", got.getMetadata().getLabels().get("app"));
        System.out.println("Get Sandbox OK: " + name);

        // List
        List<Sandbox> list = sandboxes.inNamespace(NAMESPACE).withLabel("app", "e2e-test-java").list().getItems();
        assertTrue("Should have at least 1 Sandbox", list.size() >= 1);
        System.out.println("List Sandbox OK: " + list.size() + " items");

        // Update (add label)
        got.getMetadata().getLabels().put("updated", "true");
        Sandbox updated = sandboxes.inNamespace(NAMESPACE).resource(got).update();
        assertEquals("true", updated.getMetadata().getLabels().get("updated"));
        System.out.println("Update Sandbox OK: " + name);

        // Delete
        sandboxes.inNamespace(NAMESPACE).withName(name).delete();
        Sandbox deleted = sandboxes.inNamespace(NAMESPACE).withName(name).get();
        assertNull("Sandbox should be deleted", deleted);
        System.out.println("Delete Sandbox OK: " + name);
    }

    // ======================== SandboxSet CRUD ========================

    @Test
    public void testSandboxSetCRUD() {
        String name = uniqueName("e2e-sandboxset");
        MixedOperation<SandboxSet, KubernetesResourceList<SandboxSet>, Resource<SandboxSet>> sandboxSets =
                client.resources(SandboxSet.class);

        // Build
        SandboxSet sandboxSet = new SandboxSet();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(NAMESPACE);
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "e2e-test-java");
        meta.setLabels(labels);
        sandboxSet.setMetadata(meta);

        SandboxSetSpec spec = new SandboxSetSpec();
        spec.setReplicas(1);
        spec.setTemplate(basePodTemplate("sandboxset-test"));
        sandboxSet.setSpec(spec);

        // Create
        SandboxSet created = sandboxSets.inNamespace(NAMESPACE).resource(sandboxSet).create();
        assertNotNull(created);
        assertEquals(Integer.valueOf(1), created.getSpec().getReplicas());
        System.out.println("Created SandboxSet: " + name);
        cleanupActions.add(() -> sandboxSets.inNamespace(NAMESPACE).withName(name).delete());

        // Get
        SandboxSet got = sandboxSets.inNamespace(NAMESPACE).withName(name).get();
        assertNotNull(got);
        assertEquals(Integer.valueOf(1), got.getSpec().getReplicas());
        System.out.println("Get SandboxSet OK: " + name);

        // List
        List<SandboxSet> list = sandboxSets.inNamespace(NAMESPACE).withLabel("app", "e2e-test-java").list().getItems();
        assertTrue("Should have at least 1 SandboxSet", list.size() >= 1);
        System.out.println("List SandboxSet OK: " + list.size() + " items");

        // Update
        got.getSpec().setReplicas(2);
        SandboxSet updated = sandboxSets.inNamespace(NAMESPACE).resource(got).update();
        assertEquals(Integer.valueOf(2), updated.getSpec().getReplicas());
        System.out.println("Update SandboxSet OK: " + name);

        // Delete
        sandboxSets.inNamespace(NAMESPACE).withName(name).delete();
        assertNull(sandboxSets.inNamespace(NAMESPACE).withName(name).get());
        System.out.println("Delete SandboxSet OK: " + name);
    }

    // ======================== SandboxClaim CRUD ========================

    @Test
    public void testSandboxClaimCRUD() {
        String name = uniqueName("e2e-claim");
        MixedOperation<SandboxClaim, KubernetesResourceList<SandboxClaim>, Resource<SandboxClaim>> claims =
                client.resources(SandboxClaim.class);

        // Build
        SandboxClaim claim = new SandboxClaim();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(NAMESPACE);
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "e2e-test-java");
        meta.setLabels(labels);
        claim.setMetadata(meta);

        SandboxClaimSpec spec = new SandboxClaimSpec();
        spec.setTemplateName("test-template");
        spec.setReplicas(1);
        claim.setSpec(spec);

        // Create
        SandboxClaim created = claims.inNamespace(NAMESPACE).resource(claim).create();
        assertNotNull(created);
        assertEquals("test-template", created.getSpec().getTemplateName());
        System.out.println("Created SandboxClaim: " + name);
        cleanupActions.add(() -> claims.inNamespace(NAMESPACE).withName(name).delete());

        // Get
        SandboxClaim got = claims.inNamespace(NAMESPACE).withName(name).get();
        assertNotNull(got);
        assertEquals("test-template", got.getSpec().getTemplateName());
        System.out.println("Get SandboxClaim OK: " + name);

        // List
        List<SandboxClaim> list = claims.inNamespace(NAMESPACE).withLabel("app", "e2e-test-java").list().getItems();
        assertTrue("Should have at least 1 SandboxClaim", list.size() >= 1);
        System.out.println("List SandboxClaim OK: " + list.size() + " items");

        // Delete
        claims.inNamespace(NAMESPACE).withName(name).delete();
        assertNull(claims.inNamespace(NAMESPACE).withName(name).get());
        System.out.println("Delete SandboxClaim OK: " + name);
    }

    // ======================== Checkpoint CRUD ========================

    @Test
    public void testCheckpointCRUD() {
        String name = uniqueName("e2e-checkpoint");
        MixedOperation<Checkpoint, KubernetesResourceList<Checkpoint>, Resource<Checkpoint>> checkpoints =
                client.resources(Checkpoint.class);

        // Build
        Checkpoint checkpoint = new Checkpoint();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(NAMESPACE);
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "e2e-test-java");
        meta.setLabels(labels);
        checkpoint.setMetadata(meta);

        CheckpointSpec spec = new CheckpointSpec();
        spec.setSandboxName("test-sandbox");
        spec.setPodName("test-pod");
        spec.setKeepRunning(true);
        checkpoint.setSpec(spec);

        // Create
        Checkpoint created = checkpoints.inNamespace(NAMESPACE).resource(checkpoint).create();
        assertNotNull(created);
        assertEquals("test-sandbox", created.getSpec().getSandboxName());
        System.out.println("Created Checkpoint: " + name);
        cleanupActions.add(() -> checkpoints.inNamespace(NAMESPACE).withName(name).delete());

        // Get
        Checkpoint got = checkpoints.inNamespace(NAMESPACE).withName(name).get();
        assertNotNull(got);
        assertEquals("test-sandbox", got.getSpec().getSandboxName());
        assertEquals("test-pod", got.getSpec().getPodName());
        System.out.println("Get Checkpoint OK: " + name);

        // List
        List<Checkpoint> list = checkpoints.inNamespace(NAMESPACE).withLabel("app", "e2e-test-java").list().getItems();
        assertTrue("Should have at least 1 Checkpoint", list.size() >= 1);
        System.out.println("List Checkpoint OK: " + list.size() + " items");

        // Delete
        checkpoints.inNamespace(NAMESPACE).withName(name).delete();
        assertNull(checkpoints.inNamespace(NAMESPACE).withName(name).get());
        System.out.println("Delete Checkpoint OK: " + name);
    }

    // ======================== SandboxTemplate CRUD ========================

    @Test
    public void testSandboxTemplateCRUD() {
        String name = uniqueName("e2e-template");
        MixedOperation<SandboxTemplate, KubernetesResourceList<SandboxTemplate>, Resource<SandboxTemplate>> templates =
                client.resources(SandboxTemplate.class);

        // Build
        SandboxTemplate tmpl = new SandboxTemplate();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(NAMESPACE);
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "e2e-test-java");
        meta.setLabels(labels);
        tmpl.setMetadata(meta);

        SandboxTemplateSpec spec = new SandboxTemplateSpec();
        spec.setTemplate(basePodTemplate("template-test"));
        tmpl.setSpec(spec);

        // Create
        SandboxTemplate created = templates.inNamespace(NAMESPACE).resource(tmpl).create();
        assertNotNull(created);
        assertNotNull(created.getSpec().getTemplate());
        System.out.println("Created SandboxTemplate: " + name);
        cleanupActions.add(() -> templates.inNamespace(NAMESPACE).withName(name).delete());

        // Get
        SandboxTemplate got = templates.inNamespace(NAMESPACE).withName(name).get();
        assertNotNull(got);
        assertNotNull(got.getSpec().getTemplate());
        System.out.println("Get SandboxTemplate OK: " + name);

        // List
        List<SandboxTemplate> list = templates.inNamespace(NAMESPACE).withLabel("app", "e2e-test-java").list().getItems();
        assertTrue("Should have at least 1 SandboxTemplate", list.size() >= 1);
        System.out.println("List SandboxTemplate OK: " + list.size() + " items");

        // Delete
        templates.inNamespace(NAMESPACE).withName(name).delete();
        assertNull(templates.inNamespace(NAMESPACE).withName(name).get());
        System.out.println("Delete SandboxTemplate OK: " + name);
    }

    // ======================== SandboxUpdateOps CRUD ========================

    @Test
    public void testSandboxUpdateOpsCRUD() {
        String name = uniqueName("e2e-updateops");
        MixedOperation<SandboxUpdateOps, KubernetesResourceList<SandboxUpdateOps>, Resource<SandboxUpdateOps>> updateOps =
                client.resources(SandboxUpdateOps.class);

        // Build
        SandboxUpdateOps ops = new SandboxUpdateOps();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(NAMESPACE);
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "e2e-test-java");
        meta.setLabels(labels);
        ops.setMetadata(meta);

        SandboxUpdateOpsSpec spec = new SandboxUpdateOpsSpec();
        Selector selector = new Selector();
        Map<String, String> matchLabels = new HashMap<>();
        matchLabels.put("app", "target-sandbox");
        selector.setMatchLabels(matchLabels);
        spec.setSelector(selector);
        // Patch as RawExtension
        io.fabric8.kubernetes.api.model.runtime.RawExtension patch = new io.fabric8.kubernetes.api.model.runtime.RawExtension();
        patch.setRaw("{\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"main\",\"image\":\"busybox:1.36\"}]}}}}".getBytes());
        spec.setPatch(patch);
        ops.setSpec(spec);

        // Create
        SandboxUpdateOps created = updateOps.inNamespace(NAMESPACE).resource(ops).create();
        assertNotNull(created);
        assertNotNull(created.getSpec().getSelector());
        assertEquals("target-sandbox", created.getSpec().getSelector().getMatchLabels().get("app"));
        System.out.println("Created SandboxUpdateOps: " + name);
        cleanupActions.add(() -> updateOps.inNamespace(NAMESPACE).withName(name).delete());

        // Get
        SandboxUpdateOps got = updateOps.inNamespace(NAMESPACE).withName(name).get();
        assertNotNull(got);
        assertEquals("target-sandbox", got.getSpec().getSelector().getMatchLabels().get("app"));
        System.out.println("Get SandboxUpdateOps OK: " + name);

        // List
        List<SandboxUpdateOps> list = updateOps.inNamespace(NAMESPACE).withLabel("app", "e2e-test-java").list().getItems();
        assertTrue("Should have at least 1 SandboxUpdateOps", list.size() >= 1);
        System.out.println("List SandboxUpdateOps OK: " + list.size() + " items");

        // Delete
        updateOps.inNamespace(NAMESPACE).withName(name).delete();
        assertNull(updateOps.inNamespace(NAMESPACE).withName(name).get());
        System.out.println("Delete SandboxUpdateOps OK: " + name);
    }
}
