
package queueforge.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

    @GetMapping("/api/v1/system/status")
    public Map<String, String> getStatus() {

        return Map.of(
            "application", "QueueForge",
            "status", "UP",
            "version", "1.0.0",
            "message", "Job Queue Service is operational"
        );
    }
}
