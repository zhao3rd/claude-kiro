// ==UserScript==
// @name         Yupp.ai Chat Capture
// @namespace    http://tampermonkey.net/
// @version      1.1
// @description  Captures Yupp.ai chat requests and streaming responses for analysis
// @author       Lianues
// @match        https://yupp.ai/*
// @match        https://*.yupp.ai/*
// @icon         https://www.google.com/s2/favicons?sz=64&domain=yupp.ai
// @grant        GM_download
// @run-at       document-start
// ==/UserScript==

(function () {
    'use strict';

    console.log('[Yupp Chat Capture] Script initialized');

    // Store captured data
    const capturedSessions = [];

    // Save original fetch
    const originalFetch = window.fetch;

    // Override fetch to intercept requests
    window.fetch = async function (...args) {
        const [resource, config] = args;
        let url = '';

        // Parse URL from different input types
        if (typeof resource === 'string') {
            url = resource;
        } else if (resource instanceof Request) {
            url = resource.url;
        } else if (resource instanceof URL) {
            url = resource.href;
        }

        // Check if this is a chat request we want to capture
        // Pattern: /chat/{uuid} with POST method
        const chatUrlPattern = /\/chat\/[a-f0-9-]{36}/i;
        const shouldCapture = chatUrlPattern.test(url) && config?.method === 'POST';

        if (!shouldCapture) {
            // Not a chat request, pass through normally
            return originalFetch.apply(this, args);
        }

        console.log('[Yupp Chat Capture] 🎯 Capturing chat request:', url);
        console.log('[Yupp Chat Capture] 📋 Request method:', config?.method);

        // Capture request data
        const captureData = {
            timestamp: new Date().toISOString(),
            requestUrl: url,
            requestMethod: config?.method || 'GET',
            requestHeaders: {},
            requestPayload: null,
            responseStatus: null,
            responseHeaders: {},
            responseChunks: [],
            fullResponse: '',
            error: null
        };

        // Capture request headers
        if (config?.headers) {
            if (config.headers instanceof Headers) {
                config.headers.forEach((value, key) => {
                    captureData.requestHeaders[key] = value;
                });
            } else {
                captureData.requestHeaders = { ...config.headers };
            }
        }

        // Capture request body
        if (config?.body) {
            try {
                if (typeof config.body === 'string') {
                    try {
                        captureData.requestPayload = JSON.parse(config.body);
                    } catch (jsonError) {
                        // If not valid JSON, store as string
                        captureData.requestPayload = config.body;
                    }
                } else if (config.body instanceof FormData) {
                    // Convert FormData to object
                    captureData.requestPayload = {};
                    for (const [key, value] of config.body.entries()) {
                        captureData.requestPayload[key] = value;
                    }
                } else {
                    captureData.requestPayload = config.body;
                }
                console.log('[Yupp Chat Capture] 📦 Request payload captured, type:', typeof captureData.requestPayload);
            } catch (e) {
                console.warn('[Yupp Chat Capture] ⚠️ Error parsing request body:', e);
                captureData.requestPayload = String(config.body);
            }
        }

        try {
            // Execute original fetch
            const response = await originalFetch.apply(this, args);

            // Capture response metadata
            captureData.responseStatus = response.status;
            response.headers.forEach((value, key) => {
                captureData.responseHeaders[key] = value;
            });

            // Clone response to avoid consuming the original stream
            const clonedResponse = response.clone();

            // Process stream in background
            processStreamInBackground(clonedResponse, captureData);

            // Return original response to caller
            return response;

        } catch (error) {
            console.error('[Yupp Chat Capture] ❌ Error capturing request:', error);
            captureData.error = error.message;
            saveCapture(captureData);
            throw error; // Re-throw to maintain original behavior
        }
    };

    // Process streaming response in background
    async function processStreamInBackground(response, captureData) {
        try {
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let chunkCount = 0;

            console.log('[Yupp Chat Capture] 🔄 Starting to read response stream...');

            while (true) {
                const { value, done } = await reader.read();
                if (done) {
                    console.log('[Yupp Chat Capture] ✅ Stream completed, total chunks:', chunkCount);
                    break;
                }

                const chunk = decoder.decode(value, { stream: true });
                captureData.responseChunks.push(chunk);
                captureData.fullResponse += chunk;
                chunkCount++;

                // Log progress every 10 chunks
                if (chunkCount % 10 === 0) {
                    console.log(`[Yupp Chat Capture] 📊 Received ${chunkCount} chunks, ${captureData.fullResponse.length} bytes`);
                }
            }

            // Save captured data
            console.log('[Yupp Chat Capture] 💾 Saving capture data...');
            saveCapture(captureData);

        } catch (error) {
            console.error('[Yupp Chat Capture] ❌ Error processing stream:', error);
            captureData.error = error.message;
            saveCapture(captureData);
        }
    }

    // Save captured data to file
    function saveCapture(captureData) {
        try {
            // Add to session history
            capturedSessions.push(captureData);

            // Generate filename with timestamp
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            const filename = `yupp-chat-capture-${timestamp}.json`;

            // Format JSON with pretty print
            const jsonContent = JSON.stringify(captureData, null, 2);

            // Download file using GM_download
            GM_download({
                url: 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonContent),
                name: filename,
                saveAs: false // Auto-save without dialog
            });

            console.log(`[Yupp Chat Capture] 💾 Saved capture to: ${filename}`);
            console.log('[Yupp Chat Capture] 📊 Captured data:', {
                requestUrl: captureData.requestUrl,
                responseChunks: captureData.responseChunks.length,
                totalResponseLength: captureData.fullResponse.length
            });

        } catch (error) {
            console.error('[Yupp Chat Capture] ❌ Error saving capture:', error);
        }
    }

    // Add export all function (accessible via console)
    window.exportAllCaptures = function () {
        if (capturedSessions.length === 0) {
            console.log('[Yupp Chat Capture] ⚠️ No captures to export');
            return;
        }

        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const filename = `yupp-chat-captures-all-${timestamp}.json`;
        const jsonContent = JSON.stringify(capturedSessions, null, 2);

        GM_download({
            url: 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonContent),
            name: filename,
            saveAs: true // Show save dialog for bulk export
        });

        console.log(`[Yupp Chat Capture] 💾 Exported ${capturedSessions.length} captures to: ${filename}`);
    };

    // Add status check function
    window.checkCaptureStatus = function () {
        console.log('[Yupp Chat Capture] 📊 Status:', {
            totalCaptures: capturedSessions.length,
            script: 'Active and monitoring',
            lastCapture: capturedSessions.length > 0 ? capturedSessions[capturedSessions.length - 1].timestamp : 'None'
        });
        return capturedSessions;
    };

    console.log('[Yupp Chat Capture] ========================================');
    console.log('[Yupp Chat Capture]   Yupp.ai Chat Capture v1.1 Active');
    console.log('[Yupp Chat Capture]   - Monitoring: POST /chat/{uuid}');
    console.log('[Yupp Chat Capture]   - Auto-saving captures to Downloads');
    console.log('[Yupp Chat Capture]   - Use exportAllCaptures() to export all');
    console.log('[Yupp Chat Capture]   - Use checkCaptureStatus() for status');
    console.log('[Yupp Chat Capture] ========================================');

})();
