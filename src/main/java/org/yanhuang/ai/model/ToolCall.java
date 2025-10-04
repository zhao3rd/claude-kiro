package org.yanhuang.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCall {

    private String id;

    private String type;

    private ToolFunction function;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ToolFunction getFunction() {
        return function;
    }

    public void setFunction(ToolFunction function) {
        this.function = function;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolFunction {
        private String name;
        private String arguments;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }
}

