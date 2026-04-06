package com.copilot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotRequest {

    @NotBlank(message = "User query must not be blank")
    @Size(max = 50000, message = "Input exceeds maximum allowed length")
    private String query;

    private String sessionId;

    private String userId;

    /** Optional: raw logs pasted by the user for incident triage */
    private String rawLogs;

    /** Optional: force a specific mode (SINGLE, MULTI) — otherwise auto-routed */
    private AgentMode forceMode;

    public enum AgentMode {
        SINGLE, MULTI
    }
}
