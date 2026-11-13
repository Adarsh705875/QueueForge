
package queueforge.worker;

import queueforge.entity.Job;

public interface JobHandler {

    boolean supports(String jobType);

    void execute(Job job) throws Exception;
}
