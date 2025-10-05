package org.yanhuang.ai.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolDefinition {

    private String name;

    private String description;

    @JsonProperty("input_schema")
    private Map<String, Object> inputSchema;

    // Support Anthropic's tool format
    private String type;

    @JsonProperty("function")
    private Map<String, Object> function;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getFunction() {
        return function;
    }

    public void setFunction(Map<String, Object> function) {
        this.function = function;
    }

    /**
     * Extract name from function if name is null (for Anthropic format support)
     */
    public String getEffectiveName() {
        if (name != null) {
            return name;
        }
        if (function != null && function.containsKey("name")) {
            return (String) function.get("name");
        }
        return null;
    }

    /**
     * Extract description from function if description is null (for Anthropic format support)
     */
    public String getEffectiveDescription() {
        if (description != null) {
            return description;
        }
        if (function != null && function.containsKey("description")) {
            return (String) function.get("description");
        }
        return null;
    }

    /**
     * Extract input schema from function if inputSchema is null (for Anthropic format support)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getEffectiveInputSchema() {
        if (inputSchema != null) {
            return inputSchema;
        }
        if (function != null && function.containsKey("parameters")) {
            return (Map<String, Object>) function.get("parameters");
        }
        return null;
    }
}

