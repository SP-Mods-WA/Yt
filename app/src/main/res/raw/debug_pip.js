// 🔍 YTPRO PIP Debug Monitor
// Add this to the TOP of script.js to diagnose the issue

(function() {
    console.log('🔍 PIP Debug Monitor Started');
    
    // 1. Monitor ALL pause attempts
    const video = document.getElementsByClassName('video-stream')[0];
    if (video) {
        const originalPause = video.pause.bind(video);
        video.pause = function() {
            const stack = new Error().stack;
            console.error('⚠️ PAUSE CALLED FROM:', stack);
            console.error('⚠️ isPIP:', window.isPIP);
            console.error('⚠️ pauseAllowed:', window.pauseAllowed);
            console.error('⚠️ document.hidden:', document.hidden);
            console.error('⚠️ visibilityState:', document.visibilityState);
            
            if (window.isPIP && !window.pauseAllowed) {
                console.error('🛑 BLOCKING PAUSE IN PIP MODE');
                return;
            }
            return originalPause();
        };
    }
    
    // 2. Monitor visibility changes
    let originalHidden;
    Object.defineProperty(document, 'hidden', {
        get: function() {
            const value = originalHidden;
            console.log('📊 document.hidden GET:', value, 'isPIP:', window.isPIP);
            return window.isPIP ? false : value;
        },
        set: function(val) {
            console.log('📊 document.hidden SET:', val);
            originalHidden = val;
        },
        configurable: true
    });
    
    // 3. Monitor visibility events
    document.addEventListener('visibilitychange', function(e) {
        console.error('🔔 visibilitychange EVENT');
        console.error('   hidden:', document.hidden);
        console.error('   visibilityState:', document.visibilityState);
        console.error('   isPIP:', window.isPIP);
        console.error('   Video paused:', video?.paused);
    }, true);
    
    // 4. Monitor page lifecycle
    document.addEventListener('freeze', () => console.error('❄️ PAGE FREEZE'), true);
    document.addEventListener('resume', () => console.error('▶️ PAGE RESUME'), true);
    document.addEventListener('pagehide', () => console.error('👋 PAGE HIDE'), true);
    document.addEventListener('pageshow', () => console.error('👀 PAGE SHOW'), true);
    
    // 5. Monitor video state changes
    setInterval(() => {
        if (video && window.isPIP) {
            console.log('🎬 Video State:', {
                paused: video.paused,
                currentTime: video.currentTime,
                readyState: video.readyState,
                networkState: video.networkState,
                hidden: document.hidden,
                visibilityState: document.visibilityState
            });
        }
    }, 2000);
    
    console.log('✅ Debug monitor active');
})();
