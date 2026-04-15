package com.camunda.academy.testCases;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.camunda.academy.CamundaTestUtils;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

public abstract class HardwareRequestTest {
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

                                .addResourceFromClasspath("hardwarerequest.bpmn")
                                .send()
                                .join();
        }

        @DisplayName("Test deployment of the process")
        @Test
        public void testDeployment() {
                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstance("HardwareRequestProcess",
                                Map.of(),
                                client);
                // then
                assertThat(processInstance).isActive();
        }

        @DisplayName("Path when price is below threshold and hardware is available")
        @Test
        public void testHappyPath() throws Exception {
                // given
                double PRICE = 100.0;
                Map<String, Object> startVars = Map.of(
                                "price", PRICE);
                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstance("HardwareRequestProcess",
                                startVars,
                                client);
                // then
                assertThat(processInstance)
                                .hasActiveElements("ServiceTask_CheckAvailability");
                CamundaTestUtils.completeJobMock("check-availability", 1, Map.of("available", true), client);
                assertThat(processInstance).hasVariable("available", true);
                CamundaTestUtils.completeJobMock("send-hardware", 1, null, client);
                // then
                assertThat(processInstance)
                                .hasNotActivatedElements("ServiceTask_OrderHardware")
                                .hasCompletedElements("EndEvent_HardwareSent")
                                .isCompleted();

        }

        @DisplayName("Path when hardware is not available and is received within the expected time")
        @Test
        public void testReceivedRequestedHardware() throws Exception {
                // given
                boolean AVAILABLE = false;
                String ORDER_ID = "abc-123y";
                Map<String, Object> startVars = Map.of("available", AVAILABLE, "orderId", ORDER_ID);

                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstanceBefore(
                                "HardwareRequestProcess",
                                startVars,
                                "Gateway_HardwareAvailable", client);

                CamundaTestUtils.completeJobMock("order-hardware", 1, null,
                                client);

                client.newPublishMessageCommand()
                                .messageName("hardwareReceived")
                                .correlationKey(ORDER_ID)
                                .send()
                                .join();
                // then
                assertThat(processInstance)
                                .hasCompletedElements("Gateway_0xj6kw4")
                                .hasNotActivatedElements("Event_1Week");
        }

        @DisplayName("Path when hardware request is not fulfilled within the expected time")
        @Test
        public void testNotReceivedRequestedHardware() throws Exception {
                // given
                String ORDER_ID = "abc-123y";
                Map<String, Object> startVars = Map.of("orderId",
                                ORDER_ID);

                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstanceBefore(
                                "HardwareRequestProcess",
                                startVars,
                                "Gateway_WaitForHardware", client);

                processTestContext.increaseTime(Duration.ofMinutes(1));
                processTestContext.completeUserTask("UserTask_CallWithSupplier");
                // then
                assertThat(processInstance)
                                .hasActiveElement("Gateway_WaitForHardware", 1)
                                .hasNotActivatedElements("Event_HardwareReceived");
        }

        @DisplayName("Path when cost greater than threshold and is approved")
        @Test
        public void testHighCostHardwareApproved() throws Exception {
                // given
                double PRICE = 2000;
                List<String> APPROVERS = List.of("mark", "arash", "igor");
                Map<String, Object> startVars = Map.of("approvers", APPROVERS,
                                "price", PRICE);

                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstance(
                                "HardwareRequestProcess",
                                startVars, client);

                // The UserTaskSelector is a Functional Interface. When we pass a lambda or a
                // combined selector (using .and()), we are providing the implementation for the
                // abstract test(UserTask task) method. The testing library iterates through all
                // active tasks, passing each one into our test() method as an
                // io.camunda.client.api.search.response.UserTask object. If test() returns
                // true, that task is selected for completion.
                for (String approver : APPROVERS) {
                        processTestContext
                                        .completeUserTask(
                                                        CamundaTestUtils.byElement("UserTask_ApproveOrder")
                                                                        .and(CamundaTestUtils.byAssignee(approver)),
                                                        Map.of("approved", true));

                }
                // then
                assertThat(processInstance)
                                .hasActiveElement("ServiceTask_CheckAvailability", 1);
        }

        @DisplayName("Path when cost greater than threshold and one approve rejects")
        @Test
        public void testHighCostHardwareRejected() throws Exception {
                // given
                List<String> APPROVERS = List.of("mark", "arash", "igor");
                Map<String, Object> startVars = Map.of("approvers", APPROVERS);

                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstanceBefore(
                                "HardwareRequestProcess",
                                startVars, "UserTask_ApproveOrder", client);

                // The UserTaskSelector is a Functional Interface. When we pass a lambda or a
                // combined selector (using .and()), we are providing the implementation for the
                // abstract test(UserTask task) method. The testing library iterates through all
                // active tasks, passing each one into our test() method as an
                // io.camunda.client.api.search.response.UserTask object. If test() returns
                // true, that task is selected for completion.
                for (int i = 0; i < APPROVERS.size() - 1; i++) {
                        String approver = APPROVERS.get(i);
                        processTestContext
                                        .completeUserTask(
                                                        CamundaTestUtils.byElement("UserTask_ApproveOrder")
                                                                        .and(CamundaTestUtils.byAssignee(approver)),
                                                        Map.of("approved", true));

                }
                System.out.println("Rejecting with " + APPROVERS.get(APPROVERS.size() - 1));

                processTestContext
                                .completeUserTask(
                                                CamundaTestUtils.byElement("UserTask_ApproveOrder")
                                                                .and(CamundaTestUtils.byAssignee(
                                                                                APPROVERS.get(APPROVERS.size() - 1))),
                                                Map.of("approved", false));
                // then
                assertThat(processInstance)
                                .hasNotActivatedElements("ServiceTask_CheckAvailability")
                                .hasCompletedElement("EndEvent_OrderRejected", 1)
                                .isCompleted();
        }

        @DisplayName("Path when hardware stolen")
        @Test
        public void testHardwareStolen() throws Exception {
                // when
                ProcessInstanceEvent processInstance = CamundaTestUtils.startInstanceBefore(
                                "HardwareRequestProcess",
                                null, "ServiceTask_SendHardware", client);

                CamundaTestUtils.failJobMockWithError("send-hardware", 1, "stolen", client);

                assertThat(processInstance)
                                .hasCompletedElement("Event_HardwareStolen", 1);

                CamundaTestUtils.completeJobMock("inform-requester", 1, null, client);
                // then
                assertThat(processInstance)
                                .hasCompletedElement("EndEvent_HardwareStolen", 1)
                                .isCompleted();
        }

}
