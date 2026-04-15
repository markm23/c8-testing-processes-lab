package com.camunda.academy.testCases;

import org.junit.jupiter.api.Nested;

import io.camunda.process.test.api.CamundaProcessTest;

@CamundaProcessTest
public class BPMNSharedContainersTests {

    // private CamundaClient client;
    // private CamundaProcessTestContext processTestContext;

    @Nested
    class PaymentProcessNested extends PaymentProcessTest {
        // @BeforeAll
        // void inject() {
        //     // Pass the injected client/context from the outer class to the superclass
        //     setContext(client, processTestContext);
        // }
    }

    @Nested
    class HardwareRequestNested extends HardwareRequestTest {
        // @BeforeAll
        // void inject() {
        //     setContext(client, processTestContext);
        // }
    }
}
