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

import org.junit.After;
import org.junit.Before;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.Assert.fail;

/**
 * Base class for E2E tests providing common utilities.
 * Follows the Ginkgo BDD pattern used in openkruise/agents controller e2e tests.
 */
public abstract class BaseE2eTest {

    protected static final String NAMESPACE = "default";
    protected static final String INITIAL_IMAGE = "nginx:stable-alpine3.20";
    protected static final long TIMEOUT_SECONDS = 90;
    protected static final long POLL_INTERVAL_MS = 500;

    protected KubernetesClient client;
    protected final List<Runnable> cleanupActions = new ArrayList<>();

    @Before
    public void setUp() {
        client = new KubernetesClientBuilder().build();
    }

    @After
    public void tearDown() {
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

    protected String uniqueName(String prefix) {
        return prefix + "-" + System.nanoTime() % 100000;
    }

    protected PodTemplateSpec basePodTemplate(String appLabel) {
        PodTemplateSpec template = new PodTemplateSpec();
        ObjectMeta meta = new ObjectMeta();
        Map<String, String> labels = new HashMap<>();
        labels.put("app", appLabel);
        meta.setLabels(labels);
        template.setMetadata(meta);

        PodSpec podSpec = new PodSpec();
        podSpec.setRestartPolicy("Never");
        Container container = new Container();
        container.setName("test-container");
        container.setImage(INITIAL_IMAGE);
        ContainerPort port = new ContainerPort();
        port.setName("http");
        port.setContainerPort(80);
        container.setPorts(Collections.singletonList(port));
        podSpec.setContainers(Collections.singletonList(container));
        template.setSpec(podSpec);

        return template;
    }

    /**
     * Poll until a condition is met or timeout is reached (similar to Gomega's Eventually).
     */
    protected <T> T eventually(String description, long timeoutSec, Supplier<T> supplier,
                               Predicate<T> condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000;
        T result = null;
        while (System.currentTimeMillis() < deadline) {
            result = supplier.get();
            if (result != null && condition.test(result)) {
                return result;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        fail("Timed out waiting for: " + description + ", last value: " + result);
        return result;
    }

    protected void by(String step) {
        System.out.println("  By: " + step);
    }
}
