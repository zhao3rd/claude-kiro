// ==UserScript==
// @name         Yupp.ai Full Request Capture V3
// @namespace    http://tampermonkey.net/
// @version      3.0
// @description  Complete network capture with recording control, cookies, and advanced filtering
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

    console.log('[Yupp Capture V3] ================================');
    console.log('[Yupp Capture V3] Full Request Capture V3 initializing...');
    console.log('[Yupp Capture V3] Using window:', win === window ? 'window' : 'unsafeWindow');
    console.log('[Yupp Capture V3] ================================');

    // ========================================
    // Configuration & State
    // ========================================
    const config = {
        isRecording: true,              // Recording state
        captureMode: 'all',             // 'all' | 'chat-only' | 'custom'
        maxCaptures: 1000,              // Max stored captures
        filter: {
            paths: [],                  // Empty = capture all
            methods: [],                // Empty = capture all
            statusCodes: []             // Empty = capture all
        }
    };

    const capturedSessions = [];
    let fetchCallCount = 0;
    let injectionCount = 0;
    let requestIdCounter = 0;

    // Save original fetch reference IMMEDIATELY
    const originalFetch = win.fetch;
    console.log('[Yupp Capture V3] Original fetch saved:', typeof originalFetch);

    // ========================================
    // Helper Functions
    // ========================================

    // Parse cookies
    function parseCookies() {
        const cookies = {};
        document.cookie.split(';').forEach(cookie => {
            const [key, value] = cookie.trim().split('=');
            if (key) cookies[key] = value || '';
        });
        return cookies;
    }

    // Parse URL parameters
    function parseUrlParams(url) {
        try {
            const urlObj = new URL(url);
            const params = {};
            urlObj.searchParams.forEach((value, key) => {
                params[key] = value;
            });
            return {
                params,
                pathname: urlObj.pathname,
                search: urlObj.search,
                hash: urlObj.hash
            };
        } catch (e) {
            return { params: {}, pathname: url, search: '', hash: '' };
        }
    }

    // Update page title with recording status
    function updatePageTitle() {
        const prefix = config.isRecording ? '🔴 ' : '⏸️ ';
        if (!document.title.startsWith('🔴') && !document.title.startsWith('⏸️')) {
            document.title = prefix + document.title;
        } else {
            document.title = prefix + document.title.substring(2);
        }
    }

    // Check if request should be captured
    function shouldCapture(url, method, status) {
        // Check recording state
        if (!config.isRecording) return false;

        // Check capture mode
        if (config.captureMode === 'chat-only') {
            return url.includes('/chat/') && method === 'POST';
        }

        if (config.captureMode === 'custom') {
            // Path filter
            if (config.filter.paths.length > 0) {
                const matchesPath = config.filter.paths.some(path =>
                    url.includes(path) || new RegExp(path).test(url)
                );
                if (!matchesPath) return false;
            }

            // Method filter
            if (config.filter.methods.length > 0) {
                if (!config.filter.methods.includes(method)) return false;
            }

            // Status code filter (for responses)
            if (status && config.filter.statusCodes.length > 0) {
                if (!config.filter.statusCodes.includes(status)) return false;
            }
        }

        // 'all' mode - capture everything from yupp.ai
        return true;
    }

    // Manage capture storage
    function addCapture(captureData) {
        capturedSessions.push(captureData);

        // Limit storage
        if (capturedSessions.length > config.maxCaptures) {
            const removed = capturedSessions.shift();
            console.log('[Yupp Capture V3] 🗑️ Removed oldest capture:', removed.requestId);
        }
    }

    // ========================================
    // Fetch Interceptor
    // ========================================

    function createFetchInterceptor(original) {
        return async function (...args) {
            fetchCallCount++;
            const requestId = `req_${++requestIdCounter}`;
            const startTime = performance.now();

            const [resource, config] = args;
            let url = '';

            if (typeof resource === 'string') {
                url = resource;
            } else if (resource instanceof Request) {
                url = resource.url;
            } else if (resource instanceof URL) {
                url = resource.href;
            }

            const method = config?.method || 'GET';

            // Log every fetch call
            const logStyle = shouldCapture(url, method)
                ? 'color: green; font-weight: bold;'
                : 'color: gray;';
            console.log(`%c[Yupp Capture V3] 📡 #${fetchCallCount} [${requestId}]:`, logStyle, {
                url: url.substring(0, 100),
                method
            });

            // Check if should capture
            if (!shouldCapture(url, method)) {
                return original.apply(this, args);
            }

            console.log(`[Yupp Capture V3] ✅ CAPTURING [${requestId}]`);

            // Capture request data
            const urlInfo = parseUrlParams(url);
            const captureData = {
                requestId,
                timestamp: new Date().toISOString(),
                timing: { startTime },

                // Request data
                requestUrl: url,
                requestMethod: method,
                requestPath: urlInfo.pathname,
                requestParams: urlInfo.params,
                requestSearch: urlInfo.search,
                requestHash: urlInfo.hash,
                requestHeaders: {},
                requestPayload: null,
                requestCookies: parseCookies(),

                // Response data
                responseStatus: null,
                responseHeaders: {},
                responseChunks: [],
                fullResponse: '',

                // Metadata
                error: null,
                captureMode: config.captureMode
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
                    console.log(`[Yupp Capture V3] 📦 Payload captured [${requestId}]`);
                } catch (e) {
                    console.warn(`[Yupp Capture V3] ⚠️ Error capturing payload [${requestId}]:`, e);
                }
            }

            try {
                const response = await original.apply(this, args);
                const responseTime = performance.now();

                captureData.responseStatus = response.status;
                captureData.timing.responseTime = responseTime;
                captureData.timing.duration = responseTime - startTime;

                response.headers.forEach((value, key) => {
                    captureData.responseHeaders[key] = value;
                });

                // Check status code filter
                if (!shouldCapture(url, method, response.status)) {
                    console.log(`[Yupp Capture V3] ⏭️ Skipping due to status filter [${requestId}]`);
                    return response;
                }

                const clonedResponse = response.clone();
                processStream(clonedResponse, captureData);

                return response;
            } catch (error) {
                console.error(`[Yupp Capture V3] ❌ Error [${requestId}]:`, error);
                captureData.error = error.message;
                captureData.timing.duration = performance.now() - startTime;
                saveCapture(captureData);
                throw error;
            }
        };
    }

    // Process stream
    async function processStream(response, captureData) {
        try {
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let chunkCount = 0;

            console.log(`[Yupp Capture V3] 🔄 Reading stream [${captureData.requestId}]...`);

            while (true) {
                const { value, done } = await reader.read();
                if (done) {
                    captureData.timing.endTime = performance.now();
                    captureData.timing.totalDuration = captureData.timing.endTime - captureData.timing.startTime;
                    console.log(`[Yupp Capture V3] ✅ Stream complete [${captureData.requestId}], chunks:`, chunkCount);
                    break;
                }

                const chunk = decoder.decode(value, { stream: true });
                captureData.responseChunks.push(chunk);
                captureData.fullResponse += chunk;
                chunkCount++;
            }

            saveCapture(captureData);
        } catch (error) {
            console.error(`[Yupp Capture V3] ❌ Stream error [${captureData.requestId}]:`, error);
            captureData.error = error.message;
            saveCapture(captureData);
        }
    }

    // Save capture
    function saveCapture(captureData) {
        try {
            addCapture(captureData);

            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            const filename = `yupp-capture-${captureData.requestId}-${timestamp}.json`;
            const jsonContent = JSON.stringify(captureData, null, 2);

            GM_download({
                url: 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonContent),
                name: filename,
                saveAs: false
            });

            console.log(`[Yupp Capture V3] 💾 Saved [${captureData.requestId}]: ${filename}`);
        } catch (error) {
            console.error('[Yupp Capture V3] ❌ Save error:', error);
        }
    }

    // ========================================
    // Interceptor Management
    // ========================================

    function injectInterceptor() {
        injectionCount++;
        console.log(`[Yupp Capture V3] 💉 Injecting interceptor #${injectionCount}...`);

        if (win.fetch !== originalFetch && win.fetch.name !== 'yuppInterceptor') {
            console.warn('[Yupp Capture V3] ⚠️ Fetch was overridden by page! Re-injecting...');
        }

        const interceptor = createFetchInterceptor(originalFetch);
        Object.defineProperty(interceptor, 'name', { value: 'yuppInterceptor' });

        win.fetch = interceptor;
        console.log('[Yupp Capture V3] ✅ Interceptor installed');
    }

    function monitorFetchOverride() {
        let lastFetch = win.fetch;
        setInterval(() => {
            if (win.fetch !== lastFetch) {
                console.warn('[Yupp Capture V3] ⚠️ Fetch was changed!');
                if (win.fetch.name !== 'yuppInterceptor') {
                    console.log('[Yupp Capture V3] 🔄 Re-injecting interceptor...');
                    injectInterceptor();
                }
                lastFetch = win.fetch;
            }
        }, 1000);
    }

    // ========================================
    // Control Functions
    // ========================================

    win.yuppStartRecording = function () {
        config.isRecording = true;
        updatePageTitle();
        console.log('%c[Yupp Capture V3] ▶️ Recording STARTED', 'color: green; font-weight: bold; font-size: 14px;');
        return { status: 'recording', mode: config.captureMode };
    };

    win.yuppStopRecording = function () {
        config.isRecording = false;
        updatePageTitle();
        console.log('%c[Yupp Capture V3] ⏸️ Recording STOPPED', 'color: orange; font-weight: bold; font-size: 14px;');
        return { status: 'stopped', captured: capturedSessions.length };
    };

    win.yuppToggleRecording = function () {
        config.isRecording = !config.isRecording;
        updatePageTitle();
        const status = config.isRecording ? '▶️ RECORDING' : '⏸️ STOPPED';
        const color = config.isRecording ? 'green' : 'orange';
        console.log(`%c[Yupp Capture V3] ${status}`, `color: ${color}; font-weight: bold; font-size: 14px;`);
        return { status: config.isRecording ? 'recording' : 'stopped', captured: capturedSessions.length };
    };

    win.yuppSetCaptureMode = function (mode) {
        if (!['all', 'chat-only', 'custom'].includes(mode)) {
            console.error('[Yupp Capture V3] ❌ Invalid mode. Use: all, chat-only, custom');
            return { error: 'Invalid mode' };
        }
        config.captureMode = mode;
        console.log(`[Yupp Capture V3] 📝 Capture mode set to: ${mode}`);
        return { mode: config.captureMode };
    };

    win.yuppSetFilter = function (filterConfig) {
        if (filterConfig.paths) config.filter.paths = filterConfig.paths;
        if (filterConfig.methods) config.filter.methods = filterConfig.methods;
        if (filterConfig.statusCodes) config.filter.statusCodes = filterConfig.statusCodes;

        console.log('[Yupp Capture V3] 🔍 Filter updated:', config.filter);
        return { filter: config.filter };
    };

    win.yuppClearCaptures = function () {
        const count = capturedSessions.length;
        capturedSessions.length = 0;
        console.log(`[Yupp Capture V3] 🗑️ Cleared ${count} captures`);
        return { cleared: count };
    };

    win.yuppGetStats = function () {
        const stats = {
            totalCaptures: capturedSessions.length,
            isRecording: config.isRecording,
            captureMode: config.captureMode,
            fetchCalls: fetchCallCount,

            byMethod: {},
            byPath: {},
            byStatus: {},

            avgDuration: 0,
            errors: 0
        };

        let totalDuration = 0;
        capturedSessions.forEach(capture => {
            // By method
            stats.byMethod[capture.requestMethod] = (stats.byMethod[capture.requestMethod] || 0) + 1;

            // By path
            const path = capture.requestPath || capture.requestUrl;
            stats.byPath[path] = (stats.byPath[path] || 0) + 1;

            // By status
            if (capture.responseStatus) {
                stats.byStatus[capture.responseStatus] = (stats.byStatus[capture.responseStatus] || 0) + 1;
            }

            // Duration
            if (capture.timing?.totalDuration) {
                totalDuration += capture.timing.totalDuration;
            }

            // Errors
            if (capture.error) stats.errors++;
        });

        stats.avgDuration = capturedSessions.length > 0
            ? (totalDuration / capturedSessions.length).toFixed(2) + 'ms'
            : 'N/A';

        console.log('[Yupp Capture V3] 📊 Statistics:', stats);
        return stats;
    };

    win.yuppExportAll = function () {
        if (capturedSessions.length === 0) {
            console.log('[Yupp Capture V3] ⚠️ No captures to export');
            return { error: 'No captures' };
        }

        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const filename = `yupp-captures-all-${timestamp}.json`;
        const jsonContent = JSON.stringify(capturedSessions, null, 2);

        GM_download({
            url: 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonContent),
            name: filename,
            saveAs: true
        });

        console.log(`[Yupp Capture V3] 💾 Exported ${capturedSessions.length} captures`);
        return { exported: capturedSessions.length, filename };
    };

    win.yuppExportFiltered = function (options = {}) {
        let filtered = [...capturedSessions];

        // Filter by method
        if (options.method) {
            filtered = filtered.filter(c => c.requestMethod === options.method);
        }

        // Filter by path
        if (options.path) {
            filtered = filtered.filter(c => c.requestPath?.includes(options.path));
        }

        // Filter by time range
        if (options.startTime || options.endTime) {
            filtered = filtered.filter(c => {
                const time = new Date(c.timestamp).getTime();
                if (options.startTime && time < new Date(options.startTime).getTime()) return false;
                if (options.endTime && time > new Date(options.endTime).getTime()) return false;
                return true;
            });
        }

        if (filtered.length === 0) {
            console.log('[Yupp Capture V3] ⚠️ No captures match filter');
            return { error: 'No matches' };
        }

        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const filename = `yupp-captures-filtered-${timestamp}.json`;
        const jsonContent = JSON.stringify(filtered, null, 2);

        GM_download({
            url: 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonContent),
            name: filename,
            saveAs: true
        });

        console.log(`[Yupp Capture V3] 💾 Exported ${filtered.length} filtered captures`);
        return { exported: filtered.length, filename };
    };

    win.yuppCaptureStatus = function () {
        const status = {
            isRecording: config.isRecording,
            captureMode: config.captureMode,
            totalCaptures: capturedSessions.length,
            fetchCalls: fetchCallCount,
            injections: injectionCount,
            filter: config.filter,
            maxCaptures: config.maxCaptures,
            currentFetchName: win.fetch.name,
            isOurInterceptor: win.fetch.name === 'yuppInterceptor'
        };
        console.log('[Yupp Capture V3] 📊 Status:', status);
        return { status, captures: capturedSessions };
    };

    // ========================================
    // Initialization
    // ========================================

    // Initial injection
    injectInterceptor();

    // Update page title
    updatePageTitle();

    // Delayed injection (after page loads)
    setTimeout(() => {
        console.log('[Yupp Capture V3] ⏰ Delayed injection check...');
        if (win.fetch.name !== 'yuppInterceptor') {
            console.log('[Yupp Capture V3] 🔄 Fetch was overridden, re-injecting...');
            injectInterceptor();
        }
    }, 2000);

    // Another delayed check and start monitoring
    setTimeout(() => {
        console.log('[Yupp Capture V3] ⏰ Second delayed check...');
        if (win.fetch.name !== 'yuppInterceptor') {
            console.log('[Yupp Capture V3] 🔄 Fetch still overridden, re-injecting...');
            injectInterceptor();
        }
        monitorFetchOverride();
    }, 5000);

    // ========================================
    // Startup Message
    // ========================================

    console.log('[Yupp Capture V3] ========================================');
    console.log('[Yupp Capture V3]   Yupp.ai Full Request Capture V3 Active');
    console.log('[Yupp Capture V3]   📍 Mode:', config.captureMode);
    console.log('[Yupp Capture V3]   🔴 Recording:', config.isRecording ? 'ON' : 'OFF');
    console.log('[Yupp Capture V3]   ');
    console.log('[Yupp Capture V3]   🎮 Control Commands:');
    console.log('[Yupp Capture V3]     • yuppStartRecording() - Start recording');
    console.log('[Yupp Capture V3]     • yuppStopRecording() - Stop recording');
    console.log('[Yupp Capture V3]     • yuppToggleRecording() - Toggle recording');
    console.log('[Yupp Capture V3]     ');
    console.log('[Yupp Capture V3]   ⚙️ Configuration:');
    console.log('[Yupp Capture V3]     • yuppSetCaptureMode("all"|"chat-only"|"custom")');
    console.log('[Yupp Capture V3]     • yuppSetFilter({ paths: [...], methods: [...] })');
    console.log('[Yupp Capture V3]     ');
    console.log('[Yupp Capture V3]   📊 Analysis:');
    console.log('[Yupp Capture V3]     • yuppCaptureStatus() - View current status');
    console.log('[Yupp Capture V3]     • yuppGetStats() - View statistics');
    console.log('[Yupp Capture V3]     ');
    console.log('[Yupp Capture V3]   💾 Export:');
    console.log('[Yupp Capture V3]     • yuppExportAll() - Export all captures');
    console.log('[Yupp Capture V3]     • yuppExportFiltered({ method, path, startTime, endTime })');
    console.log('[Yupp Capture V3]     • yuppClearCaptures() - Clear all captures');
    console.log('[Yupp Capture V3] ========================================');

    // Test after delay
    setTimeout(() => {
        console.log('[Yupp Capture V3] 🧪 Testing function access...');
        console.log('  ✅ Control: yuppToggleRecording -', typeof win.yuppToggleRecording);
        console.log('  ✅ Config: yuppSetCaptureMode -', typeof win.yuppSetCaptureMode);
        console.log('  ✅ Stats: yuppGetStats -', typeof win.yuppGetStats);
        console.log('  ✅ Export: yuppExportAll -', typeof win.yuppExportAll);
        console.log('[Yupp Capture V3] 💡 Try: yuppToggleRecording() or yuppGetStats()');
    }, 3000);

})();
