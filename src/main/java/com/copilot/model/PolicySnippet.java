package com.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicySnippet implements Serializable {

    private static final long serialVersionUID = 1L;

    private String documentId;
    private String title;
    private String content;
    private String section;
    private String version;
    private String effectiveDate;
    private String category;
    private double score;
}
