// Hide YouTube Mobile Bottom Navigation Bar - 100%

(function() {
    'use strict';
    
    console.log('🚫 YouTube Bottom Bar Hider Starting...');
    
    // ✅ 1. Inject CSS to force hide
    function injectHideCSS() {
        if (document.getElementById('ytpro-hide-bottombar')) return;
        
        const style = document.createElement('style');
        style.id = 'ytpro-hide-bottombar';
        style.textContent = `
            /* ✅ Hide bottom navigation bar */
            ytm-pivot-bar-renderer,
            ytm-pivot-bar-item-renderer,
            c3-tab-bar-renderer,
            .pivot-bar-renderer,
            .pivot-bar,
            #tab-bar,
            #bottom-bar,
            [role="tablist"],
            ytm-app > ytm-pivot-bar-renderer,
            div[class*="pivot"],
            div[id*="pivot"],
            [class*="bottom-bar"],
            [id*="bottom-bar"] {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                height: 0 !important;
                min-height: 0 !important;
                max-height: 0 !important;
                overflow: hidden !important;
                pointer-events: none !important;
                position: absolute !important;
                left: -9999px !important;
            }
            
            /* ✅ Remove bottom padding from page */
            body,
            ytm-app,
            #page-manager,
            #player-container-id {
                padding-bottom: 0 !important;
                margin-bottom: 0 !important;
            }
            
            /* ✅ Adjust content height */
            ytm-browse,
            ytm-search,
            ytm-watch {
                min-height: 100vh !important;
                padding-bottom: 0 !important;
            }
        `;
        
        document.head.appendChild(style);
        console.log('✅ Bottom bar hide CSS injected');
    }
    
    // ✅ 2. Manually remove bottom bar elements
    function removeBottomBar() {
        const selectors = [
            'ytm-pivot-bar-renderer',
            'ytm-pivot-bar-item-renderer',
            'c3-tab-bar-renderer',
            '.pivot-bar-renderer',
            '.pivot-bar',
            '#tab-bar',
            '#bottom-bar',
            '[role="tablist"]'
        ];
        
        let removed = 0;
        
        selectors.forEach(selector => {
            const elements = document.querySelectorAll(selector);
            elements.forEach(el => {
                if (el && el.parentNode) {
                    el.style.display = 'none';
                    el.style.visibility = 'hidden';
                    el.style.height = '0';
                    el.style.opacity = '0';
                    el.style.pointerEvents = 'none';
                    el.remove();
                    removed++;
                }
            });
        });
        
        if (removed > 0) {
            console.log(`🗑️ Removed ${removed} bottom bar elements`);
        }
    }
    
    // ✅ 3. Watch for new bottom bar elements
    function startObserver() {
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                mutation.addedNodes.forEach((node) => {
                    if (node.nodeType === 1) { // Element node
                        // Check if it's a bottom bar element
                        if (node.tagName === 'YTM-PIVOT-BAR-RENDERER' ||
                            node.tagName === 'C3-TAB-BAR-RENDERER' ||
                            node.classList?.contains('pivot-bar') ||
                            node.id === 'tab-bar' ||
                            node.id === 'bottom-bar') {
                            
                            node.style.display = 'none';
                            node.remove();
                            console.log('🚫 Blocked new bottom bar element');
                        }
                        
                        // Check children
                        const pivotBars = node.querySelectorAll('ytm-pivot-bar-renderer, c3-tab-bar-renderer, .pivot-bar, #tab-bar, #bottom-bar');
                        if (pivotBars.length > 0) {
                            pivotBars.forEach(bar => {
                                bar.style.display = 'none';
                                bar.remove();
                            });
                            console.log(`🚫 Blocked ${pivotBars.length} bottom bar elements in new content`);
                        }
                    }
                });
            });
        });
        
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
        
        console.log('👁️ Bottom bar observer started');
    }
    
    // ✅ 4. Remove bottom padding from body
    function removeBottomPadding() {
        const elements = [
            document.body,
            document.querySelector('ytm-app'),
            document.querySelector('#page-manager'),
            document.querySelector('#player-container-id')
        ];
        
        elements.forEach(el => {
            if (el) {
                el.style.paddingBottom = '0';
                el.style.marginBottom = '0';
            }
        });
    }
    
    // ✅ 5. Initialize everything
    function init() {
        console.log('🚀 Initializing bottom bar hider...');
        
        // Inject CSS first
        injectHideCSS();
        
        // Remove existing elements
        removeBottomBar();
        
        // Remove padding
        removeBottomPadding();
        
        // Start observer
        startObserver();
        
        // Re-run every 2 seconds as backup
        setInterval(() => {
            removeBottomBar();
            removeBottomPadding();
        }, 2000);
        
        console.log('✅ Bottom bar hider active!');
    }
    
    // ✅ 6. Run on load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    
    // Also run immediately
    injectHideCSS();
    removeBottomBar();
    
})();
