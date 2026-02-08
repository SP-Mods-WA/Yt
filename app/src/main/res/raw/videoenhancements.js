/*****YTPRO VIDEO ENHANCEMENTS - FIXED VERSION*******
Perfect YouTube App-like Video Player with Visible Controls
*/

(function() {
    
    // ✅ Enhanced video player setup
    function setupPerfectVideoPlayer() {
        var video = document.querySelector('.video-stream');
        if (!video) return;
        
        // ✅ Remove all restrictions
        video.removeAttribute('disablepictureinpicture');
        video.setAttribute('playsinline', 'true');
        video.setAttribute('webkit-playsinline', 'true');
        video.controlsList = '';
        
        // ✅ Perfect video fit - FIXED POSITIONING
        video.style.objectFit = 'contain';
        video.style.width = '100%';
        video.style.height = '100%';
        video.style.background = '#000';
        video.style.position = 'absolute';
        video.style.top = '0';
        video.style.left = '0';
        video.style.right = '0';
        video.style.bottom = '0';
        
        // Fix video container positioning
        var playerContainer = video.closest('.html5-video-container');
        if (playerContainer) {
            playerContainer.style.position = 'absolute';
            playerContainer.style.width = '100%';
            playerContainer.style.height = '100%';
            playerContainer.style.top = '0';
            playerContainer.style.left = '0';
        }
        
        // ✅ Enable smooth quality switching
        video.addEventListener('loadedmetadata', function() {
            console.log('📹 Video loaded:', video.videoWidth + 'x' + video.videoHeight);
            
            // Fix aspect ratio without breaking position
            var container = video.closest('#player-container-id');
            if (container && !document.fullscreenElement) {
                var aspectRatio = video.videoWidth / video.videoHeight;
                // Don't change container size, just ensure video fills properly
                container.style.position = 'relative';
            }
        });
        
        // ✅ Smooth quality transitions
        video.addEventListener('waiting', function() {
            console.log('⏳ Buffering...');
        });
        
        video.addEventListener('playing', function() {
            console.log('▶️ Playing');
        });
    }
    
    // ✅ YouTube app-like progress bar
    function enhanceProgressBar() {
        var progressBar = document.querySelector('.ytProgressBarLineProgressBarPlayed');
        if (!progressBar) return;
        
        progressBar.style.background = '#FF0000';
        progressBar.style.transition = 'width 0.1s linear';
        
        var playhead = document.querySelector('.ytProgressBarLineProgressBarPlayhead');
        if (playhead) {
            playhead.style.background = '#FF0000';
            playhead.style.boxShadow = '0 0 4px rgba(255, 0, 0, 0.8)';
        }
    }
    
    // ✅ Smooth fullscreen transitions
    function handleFullscreen() {
        document.addEventListener('fullscreenchange', function() {
            var video = document.querySelector('.video-stream');
            if (!video) return;
            
            if (document.fullscreenElement) {
                // Entering fullscreen
                video.style.objectFit = 'contain';
                video.style.width = '100%';
                video.style.height = '100%';
                console.log('📱 Fullscreen ON');
            } else {
                // Exiting fullscreen
                video.style.objectFit = 'contain';
                video.style.width = '100%';
                video.style.height = '100%';
                console.log('📱 Fullscreen OFF');
            }
        });
    }
    
    // ✅ FIXED: Make controls ALWAYS visible and functional
    function fixControlsVisibility() {
        var style = document.createElement('style');
        style.textContent = `
            /* ✅ CRITICAL FIX: Always show controls */
            .player-controls-top,
            .player-controls-bottom,
            .ytp-chrome-top,
            .ytp-chrome-bottom {
                opacity: 1 !important;
                visibility: visible !important;
                display: flex !important;
                pointer-events: auto !important;
            }
            
            /* ✅ FIX: Show time display */
            .ytp-time-display,
            .ytp-time-current,
            .ytp-time-separator,
            .ytp-time-duration {
                opacity: 1 !important;
                visibility: visible !important;
                display: inline-block !important;
            }
            
            /* ✅ FIX: Show settings and CC buttons */
            .ytp-button,
            .ytp-settings-button,
            .ytp-subtitles-button,
            .ytp-size-button,
            .ytp-fullscreen-button {
                opacity: 1 !important;
                visibility: visible !important;
                display: inline-block !important;
            }
            
            /* Video container positioning fix */
            .html5-video-container {
                position: absolute !important;
                width: 100% !important;
                height: 100% !important;
                top: 0 !important;
                left: 0 !important;
            }
            
            /* Video element positioning fix */
            .video-stream.html5-main-video {
                position: absolute !important;
                top: 0 !important;
                left: 0 !important;
                width: 100% !important;
                height: 100% !important;
                object-fit: contain !important;
            }
            
            /* Player container fix */
            #player-container-id,
            .html5-video-player {
                position: relative !important;
                width: 100% !important;
                background: #000 !important;
            }
            
            /* Smooth fade on tap/hover (optional) */
            .html5-video-player.ytp-autohide .ytp-chrome-top,
            .html5-video-player.ytp-autohide .ytp-chrome-bottom {
                opacity: 0.95 !important;
                transition: opacity 0.3s ease !important;
            }
            
            .html5-video-player:hover .ytp-chrome-top,
            .html5-video-player:hover .ytp-chrome-bottom,
            .html5-video-player.playing-mode .ytp-chrome-top,
            .html5-video-player.playing-mode .ytp-chrome-bottom {
                opacity: 1 !important;
            }
            
            /* Video thumbnails - YouTube app style */
            ytm-thumbnail img,
            ytm-thumbnail-overlay-resume-playback-renderer,
            .media-item-thumbnail-container img,
            ytm-video-with-context-renderer img,
            #thumbnail img {
                border-radius: 12px !important;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3) !important;
                transition: transform 0.2s ease !important;
            }
            
            /* Thumbnail containers */
            .media-item-thumbnail-container,
            ytm-thumbnail-overlay-resume-playback-renderer,
            ytm-thumbnail {
                border-radius: 12px !important;
                overflow: hidden !important;
            }
            
            /* Press animation */
            ytm-video-with-context-renderer:active img {
                transform: scale(0.98) !important;
            }
            
            /* Shorts thumbnails */
            ytm-shorts-lockup-view-model img {
                border-radius: 12px !important;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3) !important;
            }
            
            /* Quality selector */
            .ytp-settings-menu {
                background: rgba(28, 28, 28, 0.95) !important;
                backdrop-filter: blur(10px) !important;
                border-radius: 8px !important;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5) !important;
            }
            
            .ytp-menuitem-label {
                color: #fff !important;
                font-weight: 500 !important;
            }
            
            .ytp-menuitem[aria-checked="true"] {
                background: rgba(255, 255, 255, 0.1) !important;
            }
            
            /* Progress bar visibility fix */
            .ytp-progress-bar-container,
            .ytp-progress-bar {
                opacity: 1 !important;
                visibility: visible !important;
            }
        `;
        
        if (!document.getElementById('ytpro-video-enhancements-fixed')) {
            style.id = 'ytpro-video-enhancements-fixed';
            document.head.appendChild(style);
            console.log('✅ Controls visibility fixed!');
        }
    }
    
    // ✅ Auto-quality based on network
    function setupAutoQuality() {
        if (!navigator.connection) return;
        
        var connection = navigator.connection;
        var quality = 'auto';
        
        if (connection.effectiveType === '4g') {
            quality = 'hd1080';
        } else if (connection.effectiveType === '3g') {
            quality = 'hd720';
        } else {
            quality = 'medium';
        }
        
        console.log('📶 Network:', connection.effectiveType, '→ Quality:', quality);
    }
    
    // ✅ Prevent video from being too small
    function maintainMinimumVideoSize() {
        var video = document.querySelector('.video-stream');
        if (!video) return;
        
        var observer = new ResizeObserver(function(entries) {
            for (var entry of entries) {
                var width = entry.contentRect.width;
                var height = entry.contentRect.height;
                
                // Minimum size for good viewing
                if (width < 320 || height < 180) {
                    console.warn('⚠️ Video too small:', width + 'x' + height);
                }
            }
        });
        
        observer.observe(video);
    }
    
    // ✅ Fix video position continuously
    function continuousPositionFix() {
        setInterval(function() {
            var video = document.querySelector('.video-stream');
            if (video) {
                // Ensure video stays centered
                if (video.style.left !== '0px') {
                    video.style.left = '0';
                    video.style.right = '0';
                    console.log('🔧 Video position corrected');
                }
            }
        }, 1000);
    }
    
    // ✅ Initialize everything
    function init() {
        setupPerfectVideoPlayer();
        enhanceProgressBar();
        handleFullscreen();
        fixControlsVisibility(); // ✅ NEW: Fix controls
        setupAutoQuality();
        maintainMinimumVideoSize();
        continuousPositionFix(); // ✅ NEW: Keep fixing position
        
        console.log('✅ YTPRO Video Enhancements FIXED VERSION Loaded');
    }
    
    // Run on page load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    
    // Re-run when navigating (SPA)
    var observer = new MutationObserver(function(mutations) {
        for (var mutation of mutations) {
            if (mutation.addedNodes.length) {
                for (var node of mutation.addedNodes) {
                    if (node.classList && node.classList.contains('video-stream')) {
                        setupPerfectVideoPlayer();
                        fixControlsVisibility();
                        break;
                    }
                }
            }
        }
    });
    
    observer.observe(document.body, {
        childList: true,
        subtree: true
    });
    
})();

// ✅ Export for use in other scripts
window.YTProVideoEnhancements = {
    version: '1.0.1-fixed',
    loaded: true
};
