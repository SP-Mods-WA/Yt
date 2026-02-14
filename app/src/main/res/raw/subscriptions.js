// YouTube Subscriptions - Simple Login Screen

(function() {
    'use strict';
    
    function replaceSubscriptionsEmpty() {
        // Subscriptions page එකේ ද කියලා check කරන්න
        var isSubscriptionsPage = window.location.pathname.includes('/feed/subscriptions') ||
                                 window.location.hash.includes('subscriptions');
        
        if (!isSubscriptionsPage) {
            return false;
        }
        
        // Login required / empty state messages හොයාගන්න
        var pageText = document.body.innerText || document.body.textContent;
        var hasEmptyState = pageText.includes('Sign in to see') || 
                           pageText.includes('subscriptions') && pageText.includes('sign') ||
                           pageText.includes('No subscriptions') ||
                           pageText.includes('Don\'t miss') ||
                           pageText.length < 500;
        
        if (!hasEmptyState) {
            return false;
        }
        
        // Main content container හොයාගන්න
        var container = document.querySelector('ytm-browse') ||
                       document.querySelector('ytm-section-list-renderer') ||
                       document.querySelector('#page-manager');
        
        if (!container) {
            return false;
        }
        
        // දැනටමත් custom message එකක් තියෙනවද
        if (document.getElementById('spmods-subs-screen')) {
            return true;
        }
        
        // Original content hide කරන්න
        var children = container.children;
        for (var i = 0; i < children.length; i++) {
            children[i].style.display = 'none';
        }
        
        // Simple login screen
        var customScreen = document.createElement('div');
        customScreen.id = 'spmods-subs-screen';
        customScreen.setAttribute('style', `
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 60vh;
            padding: 40px 20px;
            text-align: center;
        `);
        
        // Icon
        var iconDiv = document.createElement('div');
        iconDiv.setAttribute('style', `
            font-size: 80px;
            margin-bottom: 25px;
        `);
        iconDiv.textContent = '📺';
        
        // Title
        var titleDiv = document.createElement('div');
        titleDiv.setAttribute('style', `
            font-size: 22px;
            font-weight: 600;
            color: #ffffff;
            margin-bottom: 10px;
            font-family: 'Roboto', Arial, sans-serif;
        `);
        titleDiv.textContent = 'Sign in to see subscriptions';
        
        // Description
        var descDiv = document.createElement('div');
        descDiv.setAttribute('style', `
            font-size: 14px;
            color: #aaaaaa;
            max-width: 300px;
            margin-bottom: 30px;
            line-height: 1.5;
            font-family: 'Roboto', Arial, sans-serif;
        `);
        descDiv.textContent = 'Follow your favorite channels and get updates when they post new content.';
        
        // Sign in button
        var signinBtn = document.createElement('button');
        signinBtn.setAttribute('style', `
            background: #3ea6ff;
            color: white;
            border: none;
            font-size: 14px;
            font-weight: 500;
            padding: 10px 20px;
            border-radius: 18px;
            font-family: 'Roboto', Arial, sans-serif;
            cursor: pointer;
            margin-bottom: 40px;
        `);
        signinBtn.textContent = 'Sign in';
        
        signinBtn.addEventListener('click', function() {
            var youTab = document.querySelector('ytm-pivot-bar-item-renderer[tab-identifier="FEwhat_to_watch"]') ||
                        document.querySelector('a[href*="account"]') ||
                        document.querySelector('[aria-label*="You"]') ||
                        document.querySelector('ytm-pivot-bar-item-renderer:last-child');
            
            if (youTab) {
                youTab.click();
            } else {
                window.location.href = '/account';
            }
        });
        
        // Info text
        var infoDiv = document.createElement('div');
        infoDiv.setAttribute('style', `
            font-size: 13px;
            color: #888888;
            font-family: 'Roboto', Arial, sans-serif;
        `);
        infoDiv.textContent = 'Don\'t have an account? Create one for free';
        
        // Assemble
        customScreen.appendChild(iconDiv);
        customScreen.appendChild(titleDiv);
        customScreen.appendChild(descDiv);
        customScreen.appendChild(signinBtn);
        customScreen.appendChild(infoDiv);
        
        container.appendChild(customScreen);
        
        return true;
    }
    
    // Repeatedly check කරන්න
    var attempts = 0;
    var maxAttempts = 25;
    
    var checkInterval = setInterval(function() {
        attempts++;
        
        var success = replaceSubscriptionsEmpty();
        
        if (success) {
            clearInterval(checkInterval);
        } else if (attempts >= maxAttempts) {
            clearInterval(checkInterval);
        }
    }, 500);
    
    // URL changes watch කරන්න
    var lastUrl = location.href;
    new MutationObserver(function() {
        var currentUrl = location.href;
        if (currentUrl !== lastUrl) {
            lastUrl = currentUrl;
            
            if (currentUrl.includes('subscriptions')) {
                attempts = 0;
                setTimeout(function() {
                    checkInterval = setInterval(function() {
                        attempts++;
                        var success = replaceSubscriptionsEmpty();
                        if (success || attempts >= maxAttempts) {
                            clearInterval(checkInterval);
                        }
                    }, 500);
                }, 300);
            }
        }
    }).observe(document.body, {
        childList: true,
        subtree: true
    });
    
})();