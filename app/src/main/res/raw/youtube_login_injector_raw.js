// youtube_login_injector.js - Put this in res/raw/ folder

(function() {
    'use strict';
    
    console.log('🔧 YTPro Login Injector Started');
    
    function enableYouTubeLogin() {
        if (window.yt && window.yt.config_) {
            window.yt.config_.LOGGED_IN = false;
            window.yt.config_.ENABLE_LOGIN_ACTION = true;
        }
        
        document.querySelectorAll('[aria-label*="Sign in"]').forEach(btn => {
            btn.style.display = 'block';
            btn.style.pointerEvents = 'auto';
            btn.disabled = false;
        });
        
        document.querySelectorAll('ytm-topbar-menu-button-renderer').forEach(btn => {
            btn.style.display = 'block';
            btn.style.pointerEvents = 'auto';
        });
    }
    
    function interceptSignInClicks() {
        document.addEventListener('click', function(e) {
            let target = e.target;
            
            if (target.tagName === 'A' || target.tagName === 'BUTTON') {
                let href = target.getAttribute('href') || '';
                let text = target.textContent || '';
                
                if (href.includes('/ServiceLogin') || 
                    text.toLowerCase().includes('sign in') ||
                    text.toLowerCase().includes('log in')) {
                    
                    e.preventDefault();
                    e.stopPropagation();
                    
                    console.log('🔐 Sign in clicked - Opening account picker');
                    
                    if (window.Android && window.Android.openAccountPicker) {
                        window.Android.openAccountPicker();
                    }
                    
                    return false;
                }
            }
        }, true);
    }
    
    window.onAccountSelected = function(email, cookies) {
        console.log('✅ Account selected: ' + email);
        
        if (cookies && cookies.length > 0) {
            cookies.split(';').forEach(cookie => {
                document.cookie = cookie.trim();
            });
        }
        
        setTimeout(() => {
            window.location.reload();
        }, 500);
    };
    
    function checkLoginStatus() {
        let cookies = document.cookie;
        let isLoggedIn = cookies.includes('SID=') || cookies.includes('SSID=');
        
        if (isLoggedIn) {
            console.log('✓ User is logged in');
            extractUserInfo();
        } else {
            console.log('○ User is not logged in');
        }
        
        return isLoggedIn;
    }
    
    function extractUserInfo() {
        setTimeout(() => {
            let email = '';
            let name = '';
            
            try {
                let accountBtn = document.querySelector('[aria-label*="Account"]');
                if (accountBtn) {
                    let img = accountBtn.querySelector('img');
                    if (img && img.alt) {
                        name = img.alt;
                    }
                }
                
                if (window.ytcfg && window.ytcfg.data_) {
                    if (window.ytcfg.data_.LOGGED_IN_USER_EMAIL) {
                        email = window.ytcfg.data_.LOGGED_IN_USER_EMAIL;
                    }
                    if (window.ytcfg.data_.LOGGED_IN_USER_NAME) {
                        name = window.ytcfg.data_.LOGGED_IN_USER_NAME;
                    }
                }
                
                if (email && window.Android && window.Android.onUserLoggedIn) {
                    window.Android.onUserLoggedIn(name, email);
                    console.log('📤 Sent user info to Android: ' + email);
                }
            } catch (e) {
                console.error('Error extracting user info:', e);
            }
        }, 2000);
    }
    
    function init() {
        console.log('🚀 Initializing YTPro Login System');
        
        enableYouTubeLogin();
        interceptSignInClicks();
        checkLoginStatus();
        
        let lastUrl = location.href;
        new MutationObserver(() => {
            if (location.href !== lastUrl) {
                lastUrl = location.href;
                console.log('📍 Navigation detected: ' + lastUrl);
                checkLoginStatus();
            }
        }).observe(document.body, { childList: true, subtree: true });
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    
    setInterval(() => {
        enableYouTubeLogin();
    }, 3000);
    
    console.log('✅ YTPro Login Injector Ready');
    
})();
