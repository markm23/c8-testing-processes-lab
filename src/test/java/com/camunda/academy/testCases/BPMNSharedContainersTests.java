package com.camunda.academy.testCases;

import org.junit.jupiter.api.Nested;

// @CamundaProcessTest
public abstract class BPMNSharedContainersTests {

    @Nested
    class PaymentProcessRealNested extends PaymentProcessRealTest {
    }

    // @Nested
    // class PaymentProcessNested extends PaymentProcessTest {
    // }

    // @Nested
    // class HardwareRequestNested extends HardwareRequestTest {
    // }

        // @BeforeAll
        // void inject() {
        //     setContext(client, processTestContext);
        // }
}
