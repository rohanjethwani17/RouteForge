package com.routeforge.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standardized error response for all APIs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("status")
    private Integer status;
    
    @JsonProperty("error")
    private String error;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("path")
    private String path;
    
    @JsonProperty("errors")
    private List<String> errors;
    
    @JsonProperty("traceId")
    private String traceId;
}
