
package queueforge.exception;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(Long id) {
        super("Job not found with ID: " + id);
    }
}
