package com.camunda.academy;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobHandler;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import io.camunda.process.test.api.assertions.UserTaskSelector;

public class CamundaTestUtils {

    static public ProcessInstanceEvent startInstance(String id, Map<String, Object> variables, CamundaClient client) {
        ProcessInstanceEvent processInstance = client
                .newCreateInstanceCommand()
                .bpmnProcessId(id)
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        assertThat(processInstance).isCreated();

        return processInstance;
    }

    static public ProcessInstanceEvent startInstanceBefore(
            String id,
            Map<String, Object> variables,
            String startingPoint, CamundaClient client) {

        CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 commandStep3 = client
                .newCreateInstanceCommand()
                .bpmnProcessId(id)
                .latestVersion()
                .startBeforeElement(startingPoint);

        if (variables != null) {
            commandStep3.variables(variables);
        }
        ProcessInstanceEvent processInstance = commandStep3.send().join();

        assertThat(processInstance).isCreated();

        return processInstance;
    }

    static public void completeJob(String type, int count, JobHandler handler, CamundaClient client) throws Exception {
        ActivateJobsResponse activateJobsResponse = client
                .newActivateJobsCommand()
                .jobType(type)
                .maxJobsToActivate(count)
                .send().join();

        List<ActivatedJob> activatedJobs = activateJobsResponse.getJobs();

        if (activatedJobs.size() != count) {
            fail("No job activated for type " + type);
        }

        for (ActivatedJob job : activatedJobs) {
            handler.handle(client, job);
        }

    }

    static public void completeJobMock(String type, int count, Map<String, Object> variables, CamundaClient client)
            throws Exception {
        ActivateJobsResponse activateJobsResponse = client
                .newActivateJobsCommand()
                .jobType(type)
                .maxJobsToActivate(count)
                .send().join();

        List<ActivatedJob> activatedJobs = activateJobsResponse.getJobs();

        if (activatedJobs.size() != count) {
            fail("No job activated for type " + type);
        }

        for (ActivatedJob job : activatedJobs) {
            if (variables != null) {
                client.newCompleteCommand(job).variables(variables).send().join();
            } else {
                client.newCompleteCommand(job).send().join();
            }
        }

    }

    static public void failJobMockWithError(String type, int count, String errorCode, CamundaClient client)
            throws Exception {
        ActivateJobsResponse activateJobsResponse = client
                .newActivateJobsCommand()
                .jobType(type)
                .maxJobsToActivate(count)
                .send().join();

        List<ActivatedJob> activatedJobs = activateJobsResponse.getJobs();

        if (activatedJobs.size() != count) {
            fail("No job activated for type " + type);
        }

        for (ActivatedJob job : activatedJobs) {
            client.newThrowErrorCommand(job).errorCode(errorCode).send().join();
        }

    }

    // Helper method used to create UserTaskSelectors for filtering user tasks by
    // assignee or elementId. These can be combined using .and() to create
    // more complex filters.
    static public UserTaskSelector byAssignee(String assignee) {
        return userTask -> userTask.getAssignee().equals(assignee);
    }

    static public UserTaskSelector byElement(String elementId) {
        return userTask -> userTask.getElementId().equals(elementId);
    }

    public static UserTaskSelector or(UserTaskSelector s1, UserTaskSelector s2) {
        return task -> s1.test(task) || s2.test(task);
    }

}
