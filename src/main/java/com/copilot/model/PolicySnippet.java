package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicySnippet {

    private String documentId;
    private String title;
    private String content;
    private String section;
    private String version;
    private String effectiveDate;
    private String category;
    private double score;
}
