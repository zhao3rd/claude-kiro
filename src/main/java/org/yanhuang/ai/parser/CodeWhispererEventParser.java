package org.yanhuang.ai.parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CodeWhispererEventParser {

    private static final Logger log = LoggerFactory.getLogger(CodeWhispererEventParser.class);

    private final ObjectMapper mapper;

    public CodeWhispererEventParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<JsonNode> parse(byte[] data) {
        List<JsonNode> events = new ArrayList<>();
        if (data == null || data.length == 0) {
            return events;
        }

        int offset = 0;
        while (offset + 12 <= data.length) {
            int totalLen = readInt(data, offset);
            int headerLen = readInt(data, offset + 4);

            if (totalLen <= 0 || headerLen < 0) {
                break;
            }

            if (offset + totalLen > data.length) {
                break;
            }

            int payloadStart = offset + 8 + headerLen;
            int payloadEnd = offset + totalLen - 4;

            if (payloadStart >= payloadEnd || payloadEnd > data.length) {
                offset += totalLen;
                continue;
            }

            byte[] payload = Arrays.copyOfRange(data, payloadStart, payloadEnd);
            String payloadText = new String(payload, StandardCharsets.UTF_8).trim();
            int jsonIndex = payloadText.indexOf('{');
            if (jsonIndex >= 0) {
                String jsonSlice = payloadText.substring(jsonIndex);
                try {
                    JsonNode node = mapper.readTree(jsonSlice);
                    events.add(node);
                } catch (IOException ex) {
                    log.debug("Failed to parse event payload as JSON", ex);
                }
            }

            offset += totalLen;
        }

        return events;
    }

    private int readInt(byte[] data, int offset) {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, 4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getInt();
    }
}

