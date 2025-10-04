package org.yanhuang.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicStreamChunk {

    private String type;

    private String index;

    private AnthropicMessage delta;

    private AnthropicChatResponse finalResponse;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public AnthropicMessage getDelta() {
        return delta;
    }

    public void setDelta(AnthropicMessage delta) {
        this.delta = delta;
    }

    public AnthropicChatResponse getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(AnthropicChatResponse finalResponse) {
        this.finalResponse = finalResponse;
    }
}

