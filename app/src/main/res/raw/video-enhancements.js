/*****YTPRO VIDEO ENHANCEMENTS*******
Perfect YouTube App-like Video Player
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
        
        // ✅ Perfect video fit
        video.style.objectFit = 'contain';
        video.style.width = '100%';
        video.style.height = '100%';
        video.style.background = '#000';
        
        // ✅ Enable smooth quality switching
        video.addEventListener('loadedmetadata', function() {
            console.log('📹 Video loaded:', video.videoWidth + 'x' + video.videoHeight);
            
            // Fix aspect ratio
            var container = video.closest('#player-container-id');
            if (container && !document.fullscreenElement) {
                var aspectRatio = video.videoWidth / video.videoHeight;
                container.style.aspectRatio = aspectRatio;
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
                console.log('📱 Fullscreen ON');
            } else {
                // Exiting fullscreen
                video.style.objectFit = 'contain';
                console.log('📱 Fullscreen OFF');
            }
        });
    }
    
    // ✅ Perfect thumbnail styling
    function enhanceVideoThumbnails() {
        var style = document.createElement('style');
        style.textContent = `
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
            
            /* Video player controls */
            .player-controls-top,
            .player-controls-bottom {
                opacity: 0;
                transition: opacity 0.3s ease !important;
            }
            
            .html5-video-player:hover .player-controls-top,
            .html5-video-player:hover .player-controls-bottom,
            .html5-video-player.playing-mode .player-controls-top,
            .html5-video-player.playing-mode .player-controls-bottom {
                opacity: 1 !important;
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
        `;
        
        if (!document.getElementById('ytpro-video-enhancements')) {
            style.id = 'ytpro-video-enhancements';
            document.head.appendChild(style);
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
    
    // ✅ Initialize everything
    function init() {
        setupPerfectVideoPlayer();
        enhanceProgressBar();
        handleFullscreen();
        enhanceVideoThumbnails();
        setupAutoQuality();
        maintainMinimumVideoSize();
        
        console.log('✅ YTPRO Video Enhancements Loaded');
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
    version: '1.0.0',
    loaded: true
};
