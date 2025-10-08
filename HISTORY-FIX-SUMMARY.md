# History Structure Fix Summary

## Problem

Kiro API was returning `400 BAD_REQUEST: "Improperly formed request"` when history was included in the request.

## Root Cause

The `buildHistory()` method in `KiroService.java` was directly converting each Anthropic message to a Kiro history entry without ensuring the required **alternating pattern**:

```
userInputMessage → assistantResponseMessage → userInputMessage → assistantResponseMessage
```

### Example of Incorrect Structure (Before Fix)

Given Anthropic messages:
```
Message 0: user
Message 1: user
Message 2: assistant
Message 3: user
```

Old code generated:
```json
[
  { "userInputMessage": {...} },           // Index 0 ✓
  { "userInputMessage": {...} },           // Index 1 ✗ Should be assistantResponseMessage
  { "assistantResponseMessage": {...} },   // Index 2 ✗ Should be userInputMessage
  { "userInputMessage": {...} }            // Index 3 ✗ Should be assistantResponseMessage
]
```

Result: **400 BAD_REQUEST**

## Solution

Implemented a **two-step pairing algorithm** based on `ki2api/app.py` (lines 642-743):

### Step 1: Process Messages
Convert Anthropic messages to (role, content) pairs while respecting size limits.

### Step 2: Build Paired History
Ensure strict alternating pattern:
- Each `userInputMessage` is followed by `assistantResponseMessage`
- Add placeholder `"I understand."` if no assistant response exists
- Add placeholder `"Continue"` for orphaned assistant messages

### Example of Correct Structure (After Fix)

Same Anthropic messages generate:
```json
[
  { "userInputMessage": {...} },                    // Index 0 ✓
  { "assistantResponseMessage": {"content": "I understand."} },  // Index 1 ✓ Placeholder
  { "userInputMessage": {...} },                    // Index 2 ✓
  { "assistantResponseMessage": {...} },            // Index 3 ✓
  { "userInputMessage": {...} },                    // Index 4 ✓
  { "assistantResponseMessage": {"content": "I understand."} }   // Index 5 ✓ Placeholder
]
```

Result: **SUCCESS**

## Code Changes

### File: `src/main/java/org/yanhuang/ai/service/KiroService.java`

**Lines 463-593**: Complete rewrite of `buildHistory()` method

**Key Implementation**:

```java
// Step 1: Process messages into (role, content) pairs
List<MessagePair> processedMessages = new ArrayList<>();
for (AnthropicMessage message : historicalMessages) {
    String content = buildMessageContent(message);
    if ("user".equalsIgnoreCase(message.getRole())) {
        processedMessages.add(new MessagePair("user", content));
    } else if ("assistant".equalsIgnoreCase(message.getRole())) {
        processedMessages.add(new MessagePair("assistant", content));
    }
}

// Step 2: Build history pairs ensuring alternating pattern
int i = 0;
while (i < processedMessages.size()) {
    MessagePair current = processedMessages.get(i);
    
    if ("user".equals(current.role)) {
        // Add userInputMessage
        history.add(createUserInputMessage(current.content, historyModelId));
        
        // Look for assistant response
        if (i + 1 < processedMessages.size() && "assistant".equals(processedMessages.get(i + 1).role)) {
            // Found paired assistant response
            history.add(createAssistantResponseMessage(processedMessages.get(i + 1).content));
            i += 2;
        } else {
            // No assistant response, add placeholder
            history.add(createAssistantResponseMessage("I understand."));
            i += 1;
        }
    } else if ("assistant".equals(current.role)) {
        // Orphaned assistant message - add placeholder user message first
        history.add(createUserInputMessage("Continue", historyModelId));
        history.add(createAssistantResponseMessage(current.content));
        i += 1;
    }
}
```

**Helper Class** (lines 579-593):
```java
private static class MessagePair {
    final String role;
    final String content;
    
    MessagePair(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
```

## Testing

### Test File: `src/test/java/org/yanhuang/ai/service/KiroHistoryPairingTest.java`

**Test 1**: `testClaudeCodeRequestStructure()`
- Validates history structure follows alternating pattern
- Result: ✅ **PASSED**

**Test 2**: `testClaudeCodeRequestToKiro()`
- Real Kiro API call with Claude Code request structure
- Result: ✅ **SUCCESS**

### Test Results

```
=== Testing Claude Code Request Structure ===
Total messages: 5
Message 0: role=user
Message 1: role=user
Message 2: role=assistant
Message 3: role=user
Message 4: role=user

=== Generated History Structure ===
Total history entries: 6

History Entry 0: userInputMessage ✓
History Entry 1: assistantResponseMessage ✓ (placeholder)
History Entry 2: userInputMessage ✓
History Entry 3: assistantResponseMessage ✓
History Entry 4: userInputMessage ✓
History Entry 5: assistantResponseMessage ✓ (placeholder)

=== Checking History Pattern ===
History follows expected pattern: true

=== Testing Claude Code Request to Kiro ===
Payload size: 31399 bytes (30 KB)
History entries: 6

=== SUCCESS ===
Request succeeded with Claude Code structure
```

## Reference Implementation

The fix is based on the Python implementation in `/data/code-src/tools/ki2api/huggingface/ki2api/app.py` (lines 642-743), which correctly handles history pairing for Kiro API.

## Impact

- ✅ Fixes 400 BAD_REQUEST errors when history is included
- ✅ Maintains compatibility with Kiro API requirements
- ✅ Handles edge cases (orphaned messages, missing pairs)
- ✅ Respects size limits and message count limits
- ✅ Works with real Claude Code request structures

## Files Modified

1. `src/main/java/org/yanhuang/ai/service/KiroService.java` - Fixed `buildHistory()` method
2. `src/test/java/org/yanhuang/ai/service/KiroHistoryPairingTest.java` - New test file
3. `TEST-RESULTS-HISTORY-ISSUE.md` - Detailed test results
4. `HISTORY-FIX-SUMMARY.md` - This summary

## Verification Commands

```bash
# Test history structure validation
export JAVA_HOME=$JAVA21_HOME && mvn test -Dtest=KiroHistoryPairingTest#testClaudeCodeRequestStructure

# Test real Kiro API call
export JAVA_HOME=$JAVA21_HOME && mvn test -Dtest=KiroHistoryPairingTest#testClaudeCodeRequestToKiro

# Run all history tests
export JAVA_HOME=$JAVA21_HOME && mvn test -Dtest=KiroHistoryPairingTest
```

## Conclusion

The history structure issue has been **completely resolved**. The new implementation ensures that all history entries follow the strict alternating pattern required by Kiro API, preventing 400 BAD_REQUEST errors and enabling successful API calls with conversation history.

