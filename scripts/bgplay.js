/*****YTPRO*******
Author: Sandun Piumal
Version: 3.9.3 - Fixed PIP Mode Stuttering
URI: https://www.spmods.download
*/

if (typeof MediaMetadata === 'undefined') {
    window.MediaMetadata = class {
        constructor(data = {}) {
            this.title = data.title || '';
            this.artist = data.artist || '';
            this.album = data.album || '';
            this.artwork = data.artwork || [];
        }
    };
}

if (!('mediaSession' in navigator)) {

    window.handlers = {};
    window.serviceRunning = false;
    window.isPIPMode = false; // ✅ PIP mode track කරන්න

    let _state = 'none';
    let _metadata = null;

    Object.defineProperty(navigator, 'mediaSession', {
        value: {},
        configurable: true
    });

    Object.defineProperty(navigator.mediaSession, 'metadata', {
        get() {
            return _metadata;
        },
        set(value) {
            bgPlay(value);
            _metadata = value;
        },
        configurable: true
    });

    navigator.mediaSession.setActionHandler = (action, handler) => {
        if (typeof handler === 'function') {
            handlers[action] = handler;
        }
    };

    Object.defineProperty(navigator.mediaSession, 'playbackState', {
        get() {
            return _state;
        },
        set(value) {
            _state = value;

            var ytproAud = document.getElementsByClassName('video-stream')[0];
            if (!ytproAud) return;

            if (value === 'playing') {
                setTimeout(() => {
                    Android.bgPlay(ytproAud.currentTime * 1000);
                    
                    // ✅ PIP mode එකේ නම් video play වෙන්න සහතික කරන්න
                    if (window.isPIPMode && ytproAud.paused) {
                        ytproAud.play().catch(err => console.log('Play error:', err));
                    }
                }, 50); // ✅ delay අඩු කරලා
            } else if (value === 'paused') {
                // ✅ PIP mode එකේ නම් pause එක ignore කරන්න
                if (window.isPIPMode) {
                    console.log('🛑 Pause blocked in playbackState setter');
                    if (ytproAud.paused) {
                        ytproAud.play().catch(err => console.log('Play error:', err));
                    }
                    return; // ✅ Android.bgPause() call එක නවත්තන්න
                }
                
                if (pauseAllowed || PIPause) {
                    setTimeout(() => {
                        Android.bgPause(ytproAud.currentTime * 1000);
                    }, 50);
                }
            } else if (value === "none" && !(window.location.href.indexOf("youtube.com/watch") > -1 || window.location.href.indexOf("youtube.com/shorts") > -1)) {
                Android.bgStop();
                window.serviceRunning = false;
            }
        },
        configurable: true
    });
}

async function bgPlay(info) {
    if (!(window.location.href.indexOf("youtube.com/watch") > -1 || window.location.href.indexOf("youtube.com/shorts") > -1)) return;
    if (!info) return;

    var ytproAud = document.getElementsByClassName('video-stream')[0];
    if (!ytproAud) return;

    var iconBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    var img = new Image();
    img.crossOrigin = "anonymous";
    img.src = info?.artwork?.[0]?.src;

    var canvas = document.createElement('canvas');
    canvas.style.width = "1600px";
    canvas.style.height = "900px";
    canvas.style.background = "black";
    var context = canvas.getContext('2d');

    canvas.width = 160;
    canvas.height = 90;

    await new Promise((res, rej) => {
        img.onload = () => res();
    });

    try {
        context.drawImage(img, 0, 0, 160, 90);
        iconBase64 = canvas.toDataURL('image/png', 1.0);
    } catch {}

    if (window.serviceRunning) {
        setTimeout(() => {
            Android.bgUpdate(iconBase64.replace("data:image/png;base64,", ""), info.title, info.artist, ytproAud.duration * 1000);
        }, 50);
        setTimeout(() => {
            Android.bgPlay(ytproAud.currentTime * 1000);
        }, 100);
    } else {
        window.serviceRunning = true;
        setTimeout(() => {
            Android.bgStart(iconBase64.replace("data:image/png;base64,", ""), info.title, info.artist, ytproAud.duration * 1000);
        }, 50);
        setTimeout(() => {
            Android.bgPlay(ytproAud.currentTime * 1000);
        }, 100);
    }
}

// ✅ PIP mode detect කරන function එක - IMPROVED
function PIPlayer() {
    console.log('✅ Entering PIP mode');
    window.isPIPMode = true;
    
    var ytproAud = document.getElementsByClassName('video-stream')[0];
    if (!ytproAud) return;
    
    // ✅ වැදගත්: pause event listener එක එක පාරක් විතරක් add කරන්න
    if (!ytproAud.pipPauseListenerAdded) {
        ytproAud.addEventListener('pause', function pipPauseHandler(e) {
            if (window.isPIPMode) {
                console.log('🔄 Auto-resuming in PIP mode');
                e.preventDefault(); // ✅ pause event එක block කරන්න
                setTimeout(() => {
                    if (ytproAud.paused) {
                        ytproAud.play().catch(err => console.log('Play error:', err));
                    }
                }, 50); // ✅ delay එක අඩු කරලා
            }
        }, true); // ✅ capture phase එකේ handle කරන්න
        
        ytproAud.pipPauseListenerAdded = true;
    }
    
    // ✅ already paused නම් play කරන්න
    if (ytproAud.paused) {
        ytproAud.play().catch(err => console.log('Play error:', err));
    }
}

// ✅ PIP mode එකෙන් exit වෙද්දී - IMPROVED
function removePIP() {
    console.log('✅ Exiting PIP mode');
    window.isPIPMode = false;
    
    // ✅ cleanup කරන්න අවශ්‍ය නම්
    var ytproAud = document.getElementsByClassName('video-stream')[0];
    if (ytproAud) {
        ytproAud.pipPauseListenerAdded = false; // reset කරන්න
    }
}

function seekTo(t) {
    handlers.seekto({ seekTime: t / 1000 });
}

function playVideo() {
    if (!pauseAllowed) {
        window.PIPause = false;
        navigator.mediaSession.playbackState = 'playing';
    }
    handlers.play();
}

// ✅ pauseVideo function එක - IMPROVED
function pauseVideo() {
    // ✅ PIP mode එකේ නම් pause ignore කරන්න
    if (window.isPIPMode) {
        console.log('🛑 Pause blocked in PIP mode');
        
        // ✅ video එක pause වෙලා තිබ්බොත් play කරන්න
        var ytproAud = document.getElementsByClassName('video-stream')[0];
        if (ytproAud && ytproAud.paused) {
            ytproAud.play().catch(err => console.log('Play error:', err));
        }
        return;
    }
    
    if (!pauseAllowed) {
        window.PIPause = true;
        navigator.mediaSession.playbackState = 'paused';
    }
    handlers.pause();
}

async function playNext() {
    handlers.nexttrack();
}

function playPrev() {
    handlers.previoustrack();
}

// ✅ visibilitychange handler - IMPROVED
document.addEventListener('visibilitychange', function() {
    if (window.isPIPMode) {
        var ytproAud = document.getElementsByClassName('video-stream')[0];
        if (ytproAud && document.hidden && ytproAud.paused) {
            setTimeout(() => {
                ytproAud.play().catch(err => console.log('Play error:', err));
            }, 100);
        }
    }
});

// ✅ EXTRA: video element එකම monitor කරන්න
setInterval(() => {
    if (window.isPIPMode) {
        var ytproAud = document.getElementsByClassName('video-stream')[0];
        if (ytproAud && ytproAud.paused && !ytproAud.ended) {
            console.log('⚠️ Video paused unexpectedly, resuming...');
            ytproAud.play().catch(err => console.log('Play error:', err));
        }
    }
}, 500); // ✅ every 500ms check කරන්න
