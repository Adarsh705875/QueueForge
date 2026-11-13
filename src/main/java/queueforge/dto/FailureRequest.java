
package queueforge.dto;

import jakarta.validation.constraints.NotBlank;

public class FailureRequest {

    @NotBlank(message = "Error message is required")
    private String error;

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
