package com.example.sitruyenaudio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.sitruyenaudio.theme.SiTruyenAudioTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startAudioService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startAudioService()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startAudioService()
        }

        setContent {
            SiTruyenAudioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebViewScreen(url = "https://sitruyencv.com")
                }
            }
        }
    }

    private fun startAudioService() {
        val serviceIntent = Intent(this, BackgroundAudioService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false // Allow autoplay
                
                // Allow third party cookies
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val js = """
                            javascript:(function() {
                                Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; } });
                                Object.defineProperty(document, 'hidden', { get: function() { return false; } });

                                // 1. Tự động bấm nút Play (Nghe) nếu đang ở chế độ auto_play
                                setTimeout(() => {
                                    if (sessionStorage.getItem('sitruyen_autoplay') === 'true') {
                                        let btns = Array.from(document.querySelectorAll('button, div, span'));
                                        let playBtn = btns.find(b => {
                                            let t = b.innerText.toLowerCase().trim();
                                            return t === 'nghe' || t === 'đọc truyện' || t === 'nghe truyện';
                                        });
                                        if (playBtn) {
                                            playBtn.click();
                                        }
                                    }
                                }, 1500);

                                // 2. Hook vào Audio API để biết khi nào đọc xong chương
                                if (!window.audioHooked) {
                                    window.audioHooked = true;
                                    const origPlay = HTMLAudioElement.prototype.play;
                                    const origPause = HTMLAudioElement.prototype.pause;
                                    
                                    window.isPlayingAudio = false;
                                    window.audioEndedTime = 0;

                                    HTMLAudioElement.prototype.play = function() {
                                        window.isPlayingAudio = true;
                                        window.audioEndedTime = 0;
                                        sessionStorage.setItem('sitruyen_autoplay', 'true');
                                        
                                        this.addEventListener('ended', () => {
                                            window.isPlayingAudio = false;
                                            window.audioEndedTime = Date.now();
                                        }, {once: true});
                                        
                                        return origPlay.apply(this, arguments);
                                    };

                                    HTMLAudioElement.prototype.pause = function() {
                                        window.isPlayingAudio = false;
                                        window.audioEndedTime = 0; // User cố tình pause
                                        sessionStorage.setItem('sitruyen_autoplay', 'false');
                                        return origPause.apply(this, arguments);
                                    };

                                    // Vòng lặp kiểm tra
                                    setInterval(() => {
                                        if (sessionStorage.getItem('sitruyen_autoplay') === 'true') {
                                            // Nếu không có audio nào đang chạy và đã dừng được 4 giây (chắc chắn hết chương)
                                            if (!window.isPlayingAudio && window.audioEndedTime > 0 && (Date.now() - window.audioEndedTime > 4000)) {
                                                window.audioEndedTime = 0; // Tránh click nhiều lần
                                                
                                                // Tìm nút chuyển chương
                                                let els = Array.from(document.querySelectorAll('a, button'));
                                                let nextBtn = els.find(el => {
                                                    let txt = el.innerText.trim();
                                                    return txt === 'Chương sau' || txt === 'Chương tiếp' || txt === '>';
                                                });
                                                
                                                if (!nextBtn) {
                                                    let svgs = document.querySelectorAll('svg');
                                                    for (let svg of svgs) {
                                                        let html = svg.innerHTML;
                                                        if (html.includes('7.5 7.5-7.5 7.5') || html.includes('l7 7-7 7') || html.includes('l6-6z')) {
                                                            nextBtn = svg.closest('a') || svg.closest('button');
                                                            break;
                                                        }
                                                    }
                                                }
                                                
                                                if (nextBtn) {
                                                    nextBtn.click();
                                                }
                                            }
                                        }
                                    }, 1000);
                                }
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        return false 
                    }
                }
                webChromeClient = WebChromeClient()

                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
