#!/bin/bash

# Test tool call functionality
curl -X POST http://localhost:7860/v1/messages \
  -H "Content-Type: application/json" \
  -H "x-api-key: sk-testing" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "claude-sonnet-4-5-20250929",
    "max_tokens": 1000,
    "tools": [
      {
        "name": "get_weather",
        "description": "Get weather information for a location",
        "input_schema": {
          "type": "object",
          "properties": {
            "location": {
              "type": "string",
              "description": "City name"
            }
          },
          "required": ["location"]
        }
      }
    ],
    "messages": [
      {
        "role": "user",
        "content": "What is the weather in Beijing?"
      }
    ]
  }'
