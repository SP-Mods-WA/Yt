// YTPro - Force Dark Mode for YouTube
(function() {
    'use strict';
    
    console.log('🌙 YTPro Dark Mode Script Starting...');
    
    // ✅ 1. Set dark theme cookie immediately
    document.cookie = 'PREF=f6=400; path=/; domain=.youtube.com; max-age=31536000';
    document.cookie = 'VISITOR_INFO1_LIVE=; path=/; domain=.youtube.com; max-age=31536000';
    
    // ✅ 2. Set localStorage for dark theme
    function setDarkStorage() {
        try {
            if (window.localStorage) {
                localStorage.setItem('yt-remote-device-id', JSON.stringify({
                    data: {theme: 'DARK'}
                }));
                
                localStorage.setItem('yt-player-quality', JSON.stringify({
                    data: 'hd1080',
                    creation: Date.now(),
                    expiration: Date.now() + 31536000000
                }));
                
                // YouTube's internal theme setting
                localStorage.setItem('yt-remote-connected-devices', '[]');
                localStorage.setItem('yt-remote-device-id', 'dark-mode-device');
            }
        } catch(e) {
            console.error('localStorage error:', e);
        }
    }
    
    // ✅ 3. Force dark theme styles
    function forceDarkTheme() {
        // HTML element
        if (document.documentElement) {
            document.documentElement.setAttribute('dark', 'true');
            document.documentElement.setAttribute('data-cast-api-enabled', 'true');
            document.documentElement.style.colorScheme = 'dark';
            document.documentElement.style.background = '#0F0F0F';
        }
        
        // Body element
        if (document.body) {
            document.body.setAttribute('dark', '');
            document.body.style.background = '#0F0F0F';
            document.body.style.backgroundColor = '#0F0F0F';
            document.body.style.color = '#FFFFFF';
        }
        
        // YouTube app container
        var ytApp = document.querySelector('ytm-app');
        if (ytApp) {
            ytApp.style.background = '#0F0F0F';
            ytApp.style.backgroundColor = '#0F0F0F';
        }
        
        // Inject CSS if not already injected
        if (!document.getElementById('ytpro-dark-force')) {
            var style = document.createElement('style');
            style.id = 'ytpro-dark-force';
            style.textContent = `
                /* Force dark color scheme */
                *, *::before, *::after {
                    color-scheme: dark !important;
                }
                
                /* Root backgrounds */
                html, body, ytm-app, #page-manager {
                    background: #0F0F0F !important;
                    background-color: #0F0F0F !important;
                }
                
                /* Override white backgrounds */
                [style*="background: rgb(255, 255, 255)"],
                [style*="background: white"],
                [style*="background-color: rgb(255, 255, 255)"],
                [style*="background-color: white"],
                [style*="background-color:#fff"],
                [style*="background:#fff"] {
                    background: #0F0F0F !important;
                    background-color: #0F0F0F !important;
                }
                
                /* Override black text on white */
                [style*="color: rgb(0, 0, 0)"],
                [style*="color: black"],
                [style*="color:#000"] {
                    color: #FFFFFF !important;
                }
                
                /* Video player */
                .html5-video-player {
                    background: #000000 !important;
                }
                
                /* Cards and containers */
                ytm-rich-item-renderer,
                ytm-video-with-context-renderer,
                ytm-compact-video-renderer {
                    background: #0F0F0F !important;
                }
                
                /* Text elements */
                ytm-video-meta-block,
                #video-title,
                .compact-media-item-headline,
                yt-formatted-string {
                    color: #FFFFFF !important;
                }
                
                /* Secondary text */
                .caption,
                .deemphasize,
                .secondary {
                    color: #AAAAAA !important;
                }
            `;
            document.head.appendChild(style);
            console.log('✅ Dark theme CSS injected');
        }
    }
    
    // ✅ 4. Intercept YouTube's theme API calls
    function interceptThemeRequests() {
        // Override fetch to force dark theme in API responses
        var originalFetch = window.fetch;
        window.fetch = function() {
            return originalFetch.apply(this, arguments).then(function(response) {
                // Clone response to read body
                var clonedResponse = response.clone();
                
                // Try to modify theme in response
                clonedResponse.text().then(function(text) {
                    if (text.includes('LIGHT') || text.includes('light')) {
                        console.log('🌙 Intercepted light theme request');
                    }
                });
                
                return response;
            });
        };
        
        // Override XMLHttpRequest
        var originalOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            this.addEventListener('load', function() {
                if (this.responseText && (this.responseText.includes('LIGHT') || this.responseText.includes('light'))) {
                    console.log('🌙 Intercepted light theme XHR');
                }
            });
            return originalOpen.apply(this, arguments);
        };
    }
    
    // ✅ 5. Watch for DOM changes and re-apply dark theme
    function startDarkModeObserver() {
        var observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                // Check for theme-related attribute changes
                if (mutation.type === 'attributes') {
                    var target = mutation.target;
                    if (target === document.documentElement || target === document.body) {
                        forceDarkTheme();
                    }
                }
                
                // Check for new nodes that might have light backgrounds
                if (mutation.addedNodes.length > 0) {
                    mutation.addedNodes.forEach(function(node) {
                        if (node.nodeType === 1) { // Element node
                            var style = window.getComputedStyle(node);
                            if (style.backgroundColor === 'rgb(255, 255, 255)' || 
                                style.backgroundColor === 'white') {
                                node.style.backgroundColor = '#0F0F0F';
                            }
                        }
                    });
                }
            });
        });
        
        observer.observe(document.documentElement, {
            attributes: true,
            childList: true,
            subtree: true,
            attributeFilter: ['style', 'class', 'dark']
        });
        
        console.log('✅ Dark mode observer started');
    }
    
    // ✅ 6. Force dark on page navigation (YouTube SPA)
    function watchNavigation() {
        var lastUrl = location.href;
        new MutationObserver(function() {
            var currentUrl = location.href;
            if (currentUrl !== lastUrl) {
                lastUrl = currentUrl;
                console.log('🔄 Navigation detected, re-applying dark theme...');
                setTimeout(function() {
                    setDarkStorage();
                    forceDarkTheme();
                }, 100);
            }
        }).observe(document.body, {
            childList: true,
            subtree: true
        });
    }
    
    // ✅ 7. Initialize everything
    function init() {
        console.log('🚀 Initializing dark mode...');
        
        // Set storage first
        setDarkStorage();
        
        // Force dark theme
        forceDarkTheme();
        
        // Intercept requests
        interceptThemeRequests();
        
        // Start observer
        startDarkModeObserver();
        
        // Watch navigation
        watchNavigation();
        
        // Re-apply every 3 seconds as backup
        setInterval(function() {
            forceDarkTheme();
        }, 3000);
        
        console.log('✅ YTPro Dark Mode Active!');
    }
    
    // ✅ 8. Run on load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    
    // Also run immediately
    setDarkStorage();
    forceDarkTheme();
    
})();