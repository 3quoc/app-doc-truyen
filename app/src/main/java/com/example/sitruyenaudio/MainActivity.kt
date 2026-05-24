package com.example.sitruyenaudio

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.Voice
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.sitruyenaudio.theme.SiTruyenAudioTheme
import org.json.JSONArray

class MainActivity : ComponentActivity() {

    private var audioService: BackgroundAudioService? = null
    private var isBound = false
    private var webViewInstance: WebView? = null

    private var isPlaying by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    
    private var speechRate by mutableStateOf(1.0f)
    private var voices by mutableStateOf<List<Voice>>(emptyList())
    private var selectedVoice by mutableStateOf<Voice?>(null)

    // Browser state
    private var currentUrl by mutableStateOf("https://sitruyencv.com")
    private var urlInput by mutableStateOf("https://sitruyencv.com")
    private var canGoBack by mutableStateOf(false)
    private var canGoForward by mutableStateOf(false)
    private var showHistory by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BackgroundAudioService.LocalBinder
            audioService = binder.getService()
            isBound = true
            
            voices = audioService?.getVietnameseVoices() ?: emptyList()
            if (voices.isNotEmpty()) selectedVoice = voices.first()

            audioService?.onSpeechComplete = {
                runOnUiThread {
                    webViewInstance?.evaluateJavascript("if(window.goToNextChapterAndRead) window.goToNextChapterAndRead();", null)
                }
            }
            
            audioService?.onParagraphStarted = { index ->
                runOnUiThread {
                    webViewInstance?.evaluateJavascript("highlightParagraph($index);", null)
                }
            }
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun simulateClick(x: Float, y: Float) {
            runOnUiThread {
                val webView = webViewInstance ?: return@runOnUiThread
                val density = resources.displayMetrics.density
                val realX = x * density
                val realY = y * density
                val time = SystemClock.uptimeMillis()
                val actionDown = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, realX, realY, 0)
                val actionUp = MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, realX, realY, 0)
                webView.dispatchTouchEvent(actionDown)
                webView.dispatchTouchEvent(actionUp)
                actionDown.recycle()
                actionUp.recycle()
            }
        }

        @JavascriptInterface
        fun receiveParagraphs(jsonArrayStr: String) {
            try {
                val jsonArray = JSONArray(jsonArrayStr)
                val paragraphs = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    paragraphs.add(jsonArray.getString(i))
                }
                runOnUiThread {
                    audioService?.setParagraphs(paragraphs)
                    audioService?.playFromIndex(0)
                    isPlaying = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JavascriptInterface
        fun playFromIndex(index: Int) {
            runOnUiThread {
                isPlaying = true
                audioService?.playFromIndex(index)
            }
        }

        @JavascriptInterface
        fun notifyNoNextChapter() {
            runOnUiThread {
                isPlaying = false
            }
        }

        @JavascriptInterface
        fun saveHistory(storyId: String, storyName: String, chapterName: String, url: String) {
            runOnUiThread {
                val historyManager = HistoryManager(this@MainActivity)
                historyManager.saveHistoryItem(storyId, storyName, chapterName, url)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startAudioService()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startAudioService()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startAudioService()
        }

        setContent {
            SiTruyenAudioTheme {
                Scaffold(
                    topBar = {
                        Column {
                            TopAppBar(
                                title = {
                                    OutlinedTextField(
                                        value = urlInput,
                                        onValueChange = { urlInput = it },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                        keyboardActions = KeyboardActions(
                                            onGo = {
                                                var finalUrl = urlInput
                                                if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                                                    finalUrl = "https://" + finalUrl
                                                }
                                                currentUrl = finalUrl
                                                webViewInstance?.loadUrl(finalUrl)
                                            }
                                        )
                                    )
                                },
                                actions = {
                                    IconButton(onClick = { showHistory = true }) {
                                        Text("🕒")
                                    }
                                    IconButton(onClick = { webViewInstance?.reload() }) {
                                        Text("🔄")
                                    }
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row {
                                    TextButton(
                                        onClick = { webViewInstance?.goBack() },
                                        enabled = canGoBack
                                    ) { Text("⬅️ Back") }
                                    TextButton(
                                        onClick = { webViewInstance?.goForward() },
                                        enabled = canGoForward
                                    ) { Text("Forward ➡️") }
                                }
                            }
                        }
                    },
                    // Removed native FABs to use web player automation
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        WebViewScreen(
                            url = currentUrl,
                            activity = this@MainActivity,
                            onUrlChanged = { newUrl -> urlInput = newUrl },
                            onNavStateChanged = { back, forward ->
                                canGoBack = back
                                canGoForward = forward
                            }
                        )
                        
                        if (showHistory) {
                            ModalBottomSheet(
                                onDismissRequest = { showHistory = false }
                            ) {
                                Column(modifier = Modifier.padding(16.dp).fillMaxWidth().height(400.dp)) {
                                    Text("Lịch sử Đọc Truyện", style = MaterialTheme.typography.titleLarge)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    val historyManager = remember { HistoryManager(this@MainActivity) }
                                    val historyList = remember { historyManager.getHistory() }
                                    
                                    if (historyList.isEmpty()) {
                                        Text("Chưa có lịch sử đọc.")
                                    } else {
                                        LazyColumn {
                                            items(historyList) { item ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                                        showHistory = false
                                                        currentUrl = item.url
                                                        urlInput = item.url
                                                        webViewInstance?.loadUrl(item.url)
                                                    }
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(item.storyName, style = MaterialTheme.typography.titleMedium)
                                                        Text(item.chapterName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (showSettings) {
                            ModalBottomSheet(
                                onDismissRequest = { showSettings = false }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Tốc độ đọc: ${"%.1f".format(speechRate)}x", style = MaterialTheme.typography.titleMedium)
                                    Slider(
                                        value = speechRate,
                                        onValueChange = { 
                                            speechRate = it
                                            audioService?.setSpeechRate(it)
                                        },
                                        valueRange = 0.5f..2.0f,
                                        steps = 14
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Giọng đọc", style = MaterialTheme.typography.titleMedium)
                                    LazyColumn(modifier = Modifier.height(200.dp)) {
                                        items(voices) { voice ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedVoice = voice
                                                        audioService?.setVoice(voice.name)
                                                    }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = (voice.name == selectedVoice?.name),
                                                    onClick = null
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(voice.name.replace("vi-vn-x-", "").replace("-local", ""))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    fun setWebView(webView: WebView) {
        this.webViewInstance = webView
    }
    
    fun stopAudioOnNavigation() {
        if (isPlaying) {
            runOnUiThread {
                audioService?.stopSpeaking()
                isPlaying = false
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, activity: MainActivity, onUrlChanged: (String) -> Unit, onNavStateChanged: (Boolean, Boolean) -> Unit) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                activity.setWebView(this)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(activity.WebAppInterface(), "AndroidTTS")

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        activity.stopAudioOnNavigation()
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        url?.let { onUrlChanged(it) }
                        onNavStateChanged(canGoBack(), canGoForward())
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onNavStateChanged(canGoBack(), canGoForward())
                        val js = """
                            javascript:(function() {
                                let style = document.createElement("style");
                                style.innerHTML = ".tts-highlight { background-color: #ffff99 !important; color: black !important; border-radius: 4px; } .tts-clickable { cursor: pointer; }";
                                document.head.appendChild(style);

                                window.startTTSReading = function() {
                                    function findMainContentContainer() {
                                        let current = document.body;
                                        while (current) {
                                            let maxChild = null;
                                            let maxLen = 0;
                                            for (let i = 0; i < current.children.length; i++) {
                                                let child = current.children[i];
                                                let len = child.innerText ? child.innerText.length : 0;
                                                if (len > maxLen) {
                                                    maxLen = len;
                                                    maxChild = child;
                                                }
                                            }
                                            let parentLen = current.innerText ? current.innerText.length : 0;
                                            if (maxChild && maxLen > parentLen * 0.8 && maxLen > 500) {
                                                current = maxChild;
                                            } else {
                                                return current;
                                            }
                                        }
                                        return document.body;
                                    }

                                    let contentDiv = findMainContentContainer();
                                    
                                    if (contentDiv) {
                                        // Wrap loose text nodes
                                        let childNodes = Array.from(contentDiv.childNodes);
                                        for (let i = 0; i < childNodes.length; i++) {
                                            let node = childNodes[i];
                                            if (node.nodeType === Node.TEXT_NODE) {
                                                let text = node.textContent.trim();
                                                if (text.length > 10) {
                                                    let span = document.createElement("span");
                                                    span.textContent = text;
                                                    span.style.display = "block";
                                                    contentDiv.insertBefore(span, node);
                                                    contentDiv.removeChild(node);
                                                }
                                            }
                                        }

                                        let elements = contentDiv.children;
                                        let textArr = [];
                                        let pIndex = 0;
                                        
                                        for (let i = 0; i < elements.length; i++) {
                                            let el = elements[i];
                                            let text = el.innerText ? el.innerText.trim() : "";
                                            // Ignore nav/header elements
                                            if (el.tagName === "NAV" || el.tagName === "HEADER" || el.tagName === "SCRIPT" || el.tagName === "STYLE" || el.tagName === "A" || el.tagName === "BUTTON") continue;
                                            
                                            if (text.length > 15) {
                                                textArr.push(text + ". ");
                                                
                                                if (!el.classList.contains("tts-clickable")) {
                                                    el.classList.add("tts-clickable");
                                                    el.setAttribute("data-tts-index", pIndex);
                                                    let clickHandler = function(e) {
                                                        AndroidTTS.playFromIndex(parseInt(this.getAttribute("data-tts-index")));
                                                        e.stopPropagation();
                                                    };
                                                    el.addEventListener("click", clickHandler, true);
                                                    el.addEventListener("touchend", clickHandler, true);
                                                }
                                                pIndex++;
                                            }
                                        }
                                        
                                        if (textArr.length > 0) {
                                            AndroidTTS.receiveParagraphs(JSON.stringify(textArr));
                                        }
                                    }
                                };

                                window.highlightParagraph = function(index) {
                                    let els = document.querySelectorAll(".tts-highlight");
                                    for (let i=0; i<els.length; i++) {
                                        els[i].classList.remove("tts-highlight");
                                    }
                                    
                                    let target = document.querySelector('[data-tts-index="' + index + '"]');
                                    if (target) {
                                        target.classList.add("tts-highlight");
                                        target.scrollIntoView({ behavior: "smooth", block: "center" });
                                    }
                                };

                                window.goToNextChapterAndRead = function() {
                                    let els = document.querySelectorAll("a, button, div.btn, span.btn, .next");
                                    let candidates = [];
                                    for(let i=0; i<els.length; i++) {
                                        let el = els[i];
                                        let txt = el.innerText ? el.innerText.trim().toLowerCase() : "";
                                        let className = typeof el.className === "string" ? el.className.toLowerCase() : "";
                                        
                                        let isNext = false;
                                        if (txt === "chương sau" || txt === "chương tiếp" || txt === "next" || txt === "chương sau >" || txt.includes("chương sau") || txt.includes("chương tiếp")) {
                                            isNext = true;
                                        }
                                        
                                        if (txt === ">" || txt === ">>" || txt === "›" || txt === "»" || txt === "→") {
                                            let offsetTop = el.getBoundingClientRect().top + window.scrollY;
                                            if (offsetTop > 500 || className.includes("next") || className.includes("btn")) {
                                                isNext = true;
                                            }
                                        }
                                        
                                        if (className.includes("next") && !className.includes("prev")) {
                                            isNext = true;
                                        }
                                        
                                        if (isNext) candidates.push(el);
                                    }
                                    
                                    candidates.sort((a, b) => {
                                        return (b.getBoundingClientRect().top + window.scrollY) - (a.getBoundingClientRect().top + window.scrollY);
                                    });
                                    
                                    let nextBtn = candidates[0];

                                    if (nextBtn) {
                                        let oldText = document.body.innerText;
                                        if (nextBtn.href && nextBtn.href.startsWith("http")) {
                                            window.location.href = nextBtn.href;
                                        } else {
                                            nextBtn.click();
                                        }
                                        
                                        let attempts = 0;
                                        let checkInterval = setInterval(function() {
                                            attempts++;
                                            if (document.body.innerText !== oldText || attempts > 20) {
                                                clearInterval(checkInterval);
                                                setTimeout(() => window.startTTSReading(), 1500);
                                            }
                                        }, 500);
                                    } else {
                                        // Fallback: Auto-increment the last number in the URL
                                        let url = window.location.href;
                                        try {
                                            let urlObj = new URL(url);
                                            let path = urlObj.pathname;
                                            let match = path.match(/(\d+)(?!.*\d)/);
                                            if (match) {
                                                let oldNumStr = match[1];
                                                let newNum = parseInt(oldNumStr) + 1;
                                                let newPath = path.substring(0, match.index) + newNum + path.substring(match.index + oldNumStr.length);
                                                urlObj.pathname = newPath;
                                                window.location.href = urlObj.toString();
                                                
                                                let oldText = document.body.innerText;
                                                let attempts = 0;
                                                let checkInterval = setInterval(function() {
                                                    attempts++;
                                                    if (document.body.innerText !== oldText || attempts > 20) {
                                                        clearInterval(checkInterval);
                                                        setTimeout(() => window.startTTSReading(), 1500);
                                                    }
                                                }, 500);
                                            } else {
                                                AndroidTTS.notifyNoNextChapter();
                                            }
                                        } catch(e) {
                                            // Ignore
                                        }
                                    }
                                };
                                
                                // --- BẮT ĐẦU: TỰ ĐỘNG HÓA WEB PLAYER ---
                                window.webPlayerTransitioning = false;
                                window.playerStatusContainer = null;
                                let userDelayMs = 2000;

                                function nativeClick(el) {
                                    let rect = el.getBoundingClientRect();
                                    let x = rect.left + rect.width / 2;
                                    let y = rect.top + rect.height / 2;
                                    if (window.AndroidTTS && window.AndroidTTS.simulateClick) {
                                        window.AndroidTTS.simulateClick(x, y);
                                    } else {
                                        el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
                                        if (el.parentElement) el.parentElement.click();
                                    }
                                }

                                setInterval(function() {
                                    if (window.webPlayerTransitioning) return;
                                    
                                    if (!window.playerStatusContainer || !document.body.contains(window.playerStatusContainer)) {
                                        let walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                                        let node;
                                        while (node = walker.nextNode()) {
                                            if (node.nodeValue.match(/Đoạn\s*\d+\s*\/\s*\d+/i) || node.nodeValue === 'Kết thúc' || node.nodeValue === 'Tạm dừng') {
                                                let parentText = node.parentElement.innerText || "";
                                                if (parentText.includes('Đoạn') || parentText.includes('Kết thúc') || parentText.includes('Tạm dừng')) {
                                                    window.playerStatusContainer = node.parentElement;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    
                                    let autoplayStep = localStorage.getItem('sitruyen_autoplay_step');
                                    
                                    if (autoplayStep === 'open_player') {
                                        let stickyDiv = document.querySelector('div.sticky.top-0.z-50');
                                        if (stickyDiv) {
                                            let svgs = stickyDiv.querySelectorAll('svg');
                                            if (svgs.length >= 3) {
                                                nativeClick(svgs[2]);
                                                localStorage.setItem('sitruyen_autoplay_step', 'click_play');
                                            }
                                        }
                                    } else if (autoplayStep === 'click_play') {
                                        if (window.playerStatusContainer) {
                                            let statusText = window.playerStatusContainer.innerText || "";
                                            if (statusText.includes("Tạm dừng")) {
                                                let container = window.playerStatusContainer.parentElement;
                                                for(let i=0; i<6; i++) {
                                                    if(!container) break;
                                                    let svgs = container.querySelectorAll('svg');
                                                    if(svgs.length >= 3) {
                                                        let targetSvg = svgs[2] || svgs[1];
                                                        if (targetSvg) {
                                                            nativeClick(targetSvg);
                                                            localStorage.removeItem('sitruyen_autoplay_step');
                                                        }
                                                        break;
                                                    }
                                                    container = container.parentElement;
                                                }
                                            } else if (statusText.includes("Đang đọc") || statusText.includes("Đoạn")) {
                                                // Already playing or preparing
                                                localStorage.removeItem('sitruyen_autoplay_step');
                                            }
                                        }
                                    }
                                    
                                    if (window.playerStatusContainer) {
                                        let statusText = window.playerStatusContainer.innerText || "";
                                        let isPaused = statusText.includes("Tạm dừng");
                                        let isFinished = statusText.includes("Kết thúc");
                                        
                                        let m = statusText.match(/Đoạn\s*(\d+)\s*\/\s*(\d+)/i);
                                        if (m) {
                                            let current = parseInt(m[1]);
                                            let total = parseInt(m[2]);
                                            if (current === total && total > 0 && isPaused) {
                                                isFinished = true;
                                            }
                                        }
                                        
                                        if (isFinished) {
                                            window.webPlayerTransitioning = true;
                                            setTimeout(function() {
                                                localStorage.setItem('sitruyen_autoplay_step', 'open_player');
                                                if (window.goToNextChapterAndRead) {
                                                    window.goToNextChapterAndRead();
                                                }
                                            }, userDelayMs);
                                        }
                                    }
                                }, 1000);
                                // --- KẾT THÚC: TỰ ĐỘNG HÓA WEB PLAYER ---
                                // --- BẮT ĐẦU: LƯU LỊCH SỬ ---
                                setTimeout(function() {
                                    try {
                                        let url = window.location.href;
                                        if (url.includes('/read/')) {
                                            // Extract storyId
                                            let m = url.match(/\/read\/(\d+)/);
                                            let storyId = m ? m[1] : "";
                                            
                                            // Extract chapter name (usually in h1 or h2 with class text-2xl or similar, containing "Chương")
                                            let chapterName = "";
                                            let headings = document.querySelectorAll('h1, h2, h3');
                                            for(let h of headings) {
                                                if (h.innerText && h.innerText.toLowerCase().includes('chương')) {
                                                    chapterName = h.innerText.trim();
                                                    break;
                                                }
                                            }
                                            
                                            // Extract story name (usually in breadcrumb or <title>)
                                            let storyName = "";
                                            let title = document.title || "";
                                            // "Chương 3: ... - Tên Truyện"
                                            if (title.includes('-')) {
                                                let parts = title.split('-');
                                                storyName = parts[parts.length - 1].trim();
                                            }
                                            // Try breadcrumb
                                            let as = document.querySelectorAll('a');
                                            for(let a of as) {
                                                if (a.href && a.href.includes('/truyen/') && a.innerText) {
                                                    storyName = a.innerText.trim();
                                                    break;
                                                }
                                            }
                                            
                                            if (storyId && storyName && chapterName) {
                                                if (window.AndroidTTS && window.AndroidTTS.saveHistory) {
                                                    window.AndroidTTS.saveHistory(storyId, storyName, chapterName, url);
                                                }
                                            }
                                        }
                                    } catch(e) {}
                                }, 3000); // Đợi 3s cho web load xong
                                // --- KẾT THÚC: LƯU LỊCH SỬ ---
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
