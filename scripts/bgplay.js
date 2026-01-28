/*****YTPRO*******
Author: Prateek Chaubey
Version: 3.9.2 - Fixed PIP Mode
URI: https://github.com/prateek-chaubey/YTPRO
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

            if (value === 'playing') {
                setTimeout(() => {
                    Android.bgPlay(ytproAud.currentTime * 1000);
                    
                    // ✅ PIP mode එකේ නම් video play වෙන්න සහතික කරන්න
                    if (window.isPIPMode && ytproAud.paused) {
                        ytproAud.play();
                    }
                }, 100);
            } else if (value === 'paused' && (pauseAllowed || PIPause)) {
                // ✅ PIP mode එකේ නම් pause එක ignore කරන්න
                if (!window.isPIPMode) {
                    setTimeout(() => {
                        Android.bgPause(ytproAud.currentTime * 1000);
                    }, 100);
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

// ✅ PIP mode detect කරන function එක
function PIPlayer() {
    console.log('✅ Entering PIP mode');
    window.isPIPMode = true;
    
    var ytproAud = document.getElementsByClassName('video-stream')[0];
    if (ytproAud && ytproAud.paused) {
        ytproAud.play();
    }
    
    // ✅ PIP mode එකේ video pause වෙනවා නම් auto-play කරන්න
    if (ytproAud) {
        ytproAud.addEventListener('pause', function pipPauseHandler() {
            if (window.isPIPMode) {
                setTimeout(() => {
                    if (ytproAud.paused) {
                        ytproAud.play();
                    }
                }, 100);
            }
        });
    }
}

// ✅ PIP mode එකෙන් exit වෙද්දී
function removePIP() {
    console.log('✅ Exiting PIP mode');
    window.isPIPMode = false;
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

function pauseVideo() {
    // ✅ PIP mode එකේ නම් pause ignore කරන්න
    if (window.isPIPMode) {
        console.log('🛑 Pause blocked in PIP mode');
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

// ✅ Screen off වුනාට පස්සේත් PIP mode එකේ play කරන්න
document.addEventListener('visibilitychange', function() {
    if (window.isPIPMode && document.hidden) {
        var ytproAud = document.getElementsByClassName('video-stream')[0];
        if (ytproAud && ytproAud.paused) {
            setTimeout(() => {
                ytproAud.play();
            }, 200);
        }
    }
});
