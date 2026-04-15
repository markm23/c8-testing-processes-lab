package com.camunda.academy.testCases;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.camunda.academy.CamundaTestUtils;
import com.camunda.academy.handlers.CreditCardChargingHandler;
import com.camunda.academy.handlers.CreditDeductionHandler;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.process.test.api.CamundaProcessTestContext;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

public abstract class PaymentProcessTest {
        protected CamundaClient client;
        protected CamundaProcessTestContext processTestContext;

        // A setter or a constructor to receive the context from the singleton
        public void setContext(CamundaClient client, CamundaProcessTestContext context) {
                this.client = client;
                this.processTestContext = context;
        }

        @BeforeEach
        public void setup() {
                client.newDeployResourceCommand()

                                .addResourceFromClasspath("payment.bpmn")
                                .send()
                                .join();
        }

        @DisplayName("Test deployment of the process")
        @Test
        public void testDeployment() {
                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstance("PaymentProcess", Map.of(),
                                client);
                // then
                assertThat(processInstance).isActive();
        }

        @DisplayName("Path when the customer has sufficient credit")
        @Test
        public void testHappyPath() throws Exception {
                // given
                double ORDER_TOTAL = 42.0;
                double CUSTOMER_CREDIT = 50.0;
                Map<String, Object> startVars = Map.of(
                                "orderTotal", ORDER_TOTAL,
                                "customerCredit", CUSTOMER_CREDIT);
                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstance("PaymentProcess", startVars,
                                client);
                // then
                assertThat(processInstance)
                                .hasActiveElements("Task_DeductCredit");
                JobHandler creditDeductionHandler = new CreditDeductionHandler();
                CamundaTestUtils.completeJob("credit-deduction", 1, creditDeductionHandler, client);
                // then
                assertThat(processInstance)
                                .hasVariable("openAmount", 0.0)
                                .hasNotActivatedElements("Task_ChargeCreditCard")
                                .hasCompletedElements("EndEvent_PaymentCompleted")
                                .isCompleted();

        }

        @DisplayName("Path when credit card charging is needed")
        @Test
        public void testCreditCardPath() throws Exception {
                // given
                double OPEN_AMOUNT = 50.0;
                String CARD_NR = "TEST_NR";
                String CVC = "ABC";
                String EXPIRY_DATE = "01/99";
                Map<String, Object> startVars = Map.of(
                                "openAmount", OPEN_AMOUNT,
                                "expiryDate", EXPIRY_DATE,
                                "cardNumber", CARD_NR,
                                "cvc", CVC);
                JobHandler creditCardHandler = new CreditCardChargingHandler();

                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstanceBefore(
                                "PaymentProcess",
                                startVars,
                                "Gateway_CreditSufficient", client);

                processTestContext.completeUserTask("Task_VerifyCreditCardData");
                CamundaTestUtils.completeJob("credit-card-charging", 1, creditCardHandler, client);

                // then
                assertThat(processInstance)
                                .hasCompletedElements("Task_ChargeCreditCard")
                                .hasCompletedElements("EndEvent_PaymentCompleted")
                                .isCompleted();
        }
}
