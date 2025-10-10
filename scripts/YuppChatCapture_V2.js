// ==UserScript==
// @name         Yupp.ai Chat Capture V2
// @namespace    http://tampermonkey.net/
// @version      2.0
// @description  Enhanced capture with unsafeWindow and delayed injection
// @author       Lianues
// @match        https://yupp.ai/*
// @match        https://*.yupp.ai/*
// @icon         https://www.google.com/s2/favicons?sz=64&domain=yupp.ai
// @grant        GM_download
// @grant        unsafeWindow
// @run-at       document-start
// ==/UserScript==

(function () {
    'use strict';

    const win = typeof unsafeWindow !== 'undefined' ? unsafeWindow : window;

    console.log('[Yupp Capture V2] ================================');
    console.log('[Yupp Capture V2] Script initializing...');
    console.log('[Yupp Capture V2] Using window:', win === window ? 'window' : 'unsafeWindow');
    console.log('[Yupp Capture V2] ================================');

    // Store captured data
    const capturedSessions = [];
    let fetchCallCount = 0;
    let xhrCallCount = 0;
    let injectionCount = 0;

    // Save original fetch reference IMMEDIATELY
    const originalFetch = win.fetch;
    console.log('[Yupp Capture V2] Original fetch saved:', typeof originalFetch);

    // Interception function
    function createFetchInterceptor(original) {
        return async function (...args) {
            fetchCallCount++;
            const [resource, config] = args;
            let url = '';

            if (typeof resource === 'string') {
                url = resource;
            } else if (resource instanceof Request) {
                url = resource.url;
            } else if (resource instanceof URL) {
                url = resource.href;
            }

            // Log EVERY fetch call
            console.log(`[Yupp Capture V2] 📡 Fetch #${fetchCallCount}:`, {
                url: url.substring(0, 100),
                method: config?.method || 'GET'
            });

            // Check if this is a chat request
            const isChatUrl = url.includes('/chat/');
            const isPost = config?.method === 'POST';

            if (isChatUrl) {
                console.log('[Yupp Capture V2] 🎯 CHAT URL DETECTED:', {
                    url,
                    method: config?.method,
                    isPost
                });
            }

            if (isChatUrl && isPost) {
                console.log('[Yupp Capture V2] ✅ CAPTURING request');
                return await captureRequest(original, args, url, config);
            }

            return original.apply(this, args);
        };
    }

    // Capture request function
    async function captureRequest(originalFetch, args, url, config) {
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

        // Capture headers
        if (config?.headers) {
            if (config.headers instanceof Headers) {
                config.headers.forEach((value, key) => {
                    captureData.requestHeaders[key] = value;
                });
            } else {
                captureData.requestHeaders = { ...config.headers };
            }
        }

        // Capture body
        if (config?.body) {
            try {
                if (typeof config.body === 'string') {
                    try {
                        captureData.requestPayload = JSON.parse(config.body);
                    } catch {
                        captureData.requestPayload = config.body;
                    }
                } else {
                    captureData.requestPayload = config.body;
                }
                console.log('[Yupp Capture V2] 📦 Payload captured');
            } catch (e) {
                console.warn('[Yupp Capture V2] ⚠️ Error capturing payload:', e);
            }
        }

        try {
            const response = await originalFetch.apply(this, args);
            captureData.responseStatus = response.status;

            response.headers.forEach((value, key) => {
                captureData.responseHeaders[key] = value;
            });

            const clonedResponse = response.clone();
            processStream(clonedResponse, captureData);

            return response;
        } catch (error) {
            console.error('[Yupp Capture V2] ❌ Error:', error);
            captureData.error = error.message;
            saveCapture(captureData);
            throw error;
        }
    }

    // Process stream
    async function processStream(response, captureData) {
        try {
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let chunkCount = 0;

            console.log('[Yupp Capture V2] 🔄 Reading stream...');

            while (true) {
                const { value, done } = await reader.read();
                if (done) {
                    console.log('[Yupp Capture V2] ✅ Stream complete, chunks:', chunkCount);
                    break;
                }

                const chunk = decoder.decode(value, { stream: true });
                captureData.responseChunks.push(chunk);
                captureData.fullResponse += chunk;
                chunkCount++;
            }

            saveCapture(captureData);
        } catch (error) {
            console.error('[Yupp Capture V2] ❌ Stream error:', error);
            captureData.error = error.message;
            saveCapture(captureData);
        }
    }

    // Save capture
    function saveCapture(captureData) {
        try {
            capturedSessions.push(captureData);

            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            const filename = `yupp-chat-capture-${timestamp}.json`;
            const jsonContent = JSON.stringify(captureData, null, 2);

            GM_download({
                url: 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonContent),
                name: filename,
                saveAs: false
            });

            console.log(`[Yupp Capture V2] 💾 Saved: ${filename}`);
        } catch (error) {
            console.error('[Yupp Capture V2] ❌ Save error:', error);
        }
    }

    // Inject interceptor
    function injectInterceptor() {
        injectionCount++;
        console.log(`[Yupp Capture V2] 💉 Injecting interceptor #${injectionCount}...`);

        // Check if fetch was overridden
        if (win.fetch !== originalFetch && win.fetch.name !== 'interceptedFetch') {
            console.warn('[Yupp Capture V2] ⚠️ Fetch was overridden by page! Re-injecting...');
        }

        // Create and inject interceptor
        const interceptor = createFetchInterceptor(originalFetch);
        Object.defineProperty(interceptor, 'name', { value: 'interceptedFetch' });

        win.fetch = interceptor;
        console.log('[Yupp Capture V2] ✅ Interceptor installed');
    }

    // Monitor fetch override
    function monitorFetchOverride() {
        let lastFetch = win.fetch;
        setInterval(() => {
            if (win.fetch !== lastFetch) {
                console.warn('[Yupp Capture V2] ⚠️ Fetch was changed! Current:', typeof win.fetch);
                if (win.fetch.name !== 'interceptedFetch') {
                    console.log('[Yupp Capture V2] 🔄 Re-injecting interceptor...');
                    injectInterceptor();
                }
                lastFetch = win.fetch;
            }
        }, 1000);
    }

    // Export functions to window
    win.yuppCaptureStatus = function () {
        const status = {
            totalCaptures: capturedSessions.length,
            fetchCalls: fetchCallCount,
            xhrCalls: xhrCallCount,
            injections: injectionCount,
            currentFetchName: win.fetch.name,
            isOurInterceptor: win.fetch.name === 'interceptedFetch'
        };
        console.log('[Yupp Capture V2] 📊 Status:', status);
        return { status, captures: capturedSessions };
    };

    win.yuppExportAll = function () {
        if (capturedSessions.length === 0) {
            console.log('[Yupp Capture V2] ⚠️ No captures to export');
            return;
        }

        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const filename = `yupp-chat-captures-all-${timestamp}.json`;
        const jsonContent = JSON.stringify(capturedSessions, null, 2);

        GM_download({
            url: 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonContent),
            name: filename,
            saveAs: true
        });

        console.log(`[Yupp Capture V2] 💾 Exported ${capturedSessions.length} captures`);
    };

    win.yuppForceCapture = function () {
        console.log('[Yupp Capture V2] 🔧 Forcing re-injection...');
        injectInterceptor();
    };

    // Initial injection
    injectInterceptor();

    // Delayed injection (after page loads)
    setTimeout(() => {
        console.log('[Yupp Capture V2] ⏰ Delayed injection check...');
        if (win.fetch.name !== 'interceptedFetch') {
            console.log('[Yupp Capture V2] 🔄 Fetch was overridden, re-injecting...');
            injectInterceptor();
        }
    }, 2000);

    // Another delayed check
    setTimeout(() => {
        console.log('[Yupp Capture V2] ⏰ Second delayed check...');
        if (win.fetch.name !== 'interceptedFetch') {
            console.log('[Yupp Capture V2] 🔄 Fetch still overridden, re-injecting...');
            injectInterceptor();
        }
        // Start monitoring
        monitorFetchOverride();
    }, 5000);

    // Startup message
    console.log('[Yupp Capture V2] ========================================');
    console.log('[Yupp Capture V2]   Yupp.ai Chat Capture V2 Active');
    console.log('[Yupp Capture V2]   - Monitoring /chat/ POST requests');
    console.log('[Yupp Capture V2]   - Commands (use these in console):');
    console.log('[Yupp Capture V2]     * yuppCaptureStatus()');
    console.log('[Yupp Capture V2]     * yuppExportAll()');
    console.log('[Yupp Capture V2]     * yuppForceCapture()');
    console.log('[Yupp Capture V2] ========================================');

    // Test after delay
    setTimeout(() => {
        console.log('[Yupp Capture V2] 🧪 Testing function access...');
        console.log('  - yuppCaptureStatus:', typeof win.yuppCaptureStatus);
        console.log('  - yuppExportAll:', typeof win.yuppExportAll);
        console.log('  - yuppForceCapture:', typeof win.yuppForceCapture);
        console.log('[Yupp Capture V2] Try running: yuppCaptureStatus()');
    }, 3000);

})();
