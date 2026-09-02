package com.ojun.klaswatch

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var loginHint: TextView
    private lateinit var toggleWeb: Button
    private lateinit var relayStatus: TextView
    private var loginDetected = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
        }
        setContentView(root)

        root.addView(TextView(this).apply {
            text = "KLAS Watch · 자동 추적 v0.4"
            textSize = 24f
            setTextColor(Color.BLACK)
        })

        status = TextView(this).apply {
            text = AppPrefs.lastStatus(this@MainActivity)
            textSize = 14f
            setPadding(0, 10, 0, 10)
        }
        root.addView(status)

        loginHint = TextView(this).apply {
            text = "아래 큰 화면에서 KLAS에 한 번만 로그인하세요. 로그인되면 과목별 설정 없이 공지·과제·시험·강의자료를 자동 추적합니다."
            textSize = 14f
            setPadding(0, 4, 0, 10)
        }
        root.addView(loginHint)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.userAgentString = settings.userAgentString + " KLASWatch/0.4"
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (!url.startsWith("https://klas.kw.ac.kr")) return
                    view.evaluateJavascript(
                        "(function(){var t=(document.body&&document.body.innerText)||''; return t.indexOf('로그아웃')>=0 || t.indexOf('개인정보수정')>=0 || t.indexOf('수강')>=0 && t.indexOf('강의')>=0;})()"
                    ) { result ->
                        if (result == "true") onLoginDetected(url)
                    }
                }
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        root.addView(webView)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        toggleWeb = Button(this).apply {
            text = "KLAS 화면 숨기기"
            setOnClickListener {
                if (webView.visibility == View.VISIBLE) {
                    webView.visibility = View.GONE
                    text = "KLAS 화면 열기"
                } else {
                    webView.visibility = View.VISIBLE
                    text = "KLAS 화면 숨기기"
                }
            }
        }
        controls.addView(toggleWeb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val scan = Button(this).apply {
            text = "지금 검사"
            setOnClickListener {
                AppPrefs.requestRediscovery(this@MainActivity)
                Scheduler.schedule(this@MainActivity)
                Scheduler.runNow(this@MainActivity)
                toast("자동 탐색 + 검사를 시작했습니다.")
            }
        }
        controls.addView(scan, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controls)

        val refresh = Button(this).apply {
            text = "상태 새로고침"
            setOnClickListener { status.text = AppPrefs.lastStatus(this@MainActivity) }
        }
        root.addView(refresh)

        val relayToggle = Button(this).apply {
            text = "이메일 중계 설정 ▾"
        }
        root.addView(relayToggle)

        val relayPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 6, 0, 6)
        }
        root.addView(relayPanel)

        relayStatus = TextView(this).apply {
            text = AppPrefs.lastRelayStatus(this@MainActivity)
            textSize = 13f
            setPadding(0, 4, 0, 6)
        }
        relayPanel.addView(relayStatus)

        relayPanel.addView(TextView(this).apply {
            text = "Google Apps Script 웹앱 주소를 한 번만 붙여넣으면 이후 새 KLAS 항목을 Gmail로 자동 중계합니다."
            textSize = 12f
        })

        val webhookInput = EditText(this).apply {
            hint = "https://script.google.com/macros/s/.../exec"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(AppPrefs.webhook(this@MainActivity))
        }
        relayPanel.addView(webhookInput)

        val relayButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val saveRelay = Button(this).apply {
            text = "주소 저장"
            setOnClickListener {
                val url = webhookInput.text.toString().trim()
                AppPrefs.saveWebhook(this@MainActivity, url)
                val text = if (url.isBlank()) "이메일 중계 미설정" else "중계 주소 저장됨 · 테스트 필요"
                AppPrefs.setLastRelayStatus(this@MainActivity, text)
                relayStatus.text = text
                toast("이메일 중계 주소를 저장했습니다.")
            }
        }
        relayButtons.addView(saveRelay, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val testRelay = Button(this).apply {
            text = "테스트 메일"
            setOnClickListener {
                val url = webhookInput.text.toString().trim()
                if (url.isBlank()) {
                    relayStatus.text = "웹앱 주소를 먼저 붙여넣어 주세요."
                    return@setOnClickListener
                }
                AppPrefs.saveWebhook(this@MainActivity, url)
                relayStatus.text = "테스트 메일 전송 중…"
                isEnabled = false
                Thread {
                    val result = RelayClient.sendTest(url, AppPrefs.token(this@MainActivity))
                    val text = if (result.ok) {
                        "이메일 중계 정상 · ${result.displayMessage()}"
                    } else {
                        "이메일 중계 실패 · ${result.displayMessage()}"
                    }
                    AppPrefs.setLastRelayStatus(this@MainActivity, text)
                    runOnUiThread {
                        relayStatus.text = text
                        isEnabled = true
                        toast(if (result.ok) "테스트 메일을 보냈습니다." else "테스트 메일 전송에 실패했습니다.")
                    }
                }.start()
            }
        }
        relayButtons.addView(testRelay, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        relayPanel.addView(relayButtons)

        relayToggle.setOnClickListener {
            val opening = relayPanel.visibility != View.VISIBLE
            relayPanel.visibility = if (opening) View.VISIBLE else View.GONE
            relayToggle.text = if (opening) "이메일 중계 설정 ▴" else "이메일 중계 설정 ▾"
        }

        root.addView(TextView(this).apply {
            text = "로그인 후에는 앱을 닫아도 Android가 허용하는 시점에 백그라운드 검사합니다. 앱을 강제 종료(Force stop)하면 다시 열기 전까지 중단될 수 있습니다."
            textSize = 12f
            setPadding(0, 8, 0, 4)
        })

        val seed = AppPrefs.autoSeedUrl(this)
        if (seed.isBlank()) {
            webView.loadUrl("https://klas.kw.ac.kr/usr/cmn/login/LoginForm.do")
        } else {
            loginDetected = true
            loginHint.text = "이전에 로그인한 KLAS 세션을 사용합니다. 자동으로 공지·과제·시험·강의 관련 페이지를 탐색합니다."
            webView.loadUrl(seed)
            Scheduler.schedule(this)
            Scheduler.runNow(this)
        }
    }

    private fun onLoginDetected(url: String) {
        if (loginDetected && AppPrefs.autoSeedUrl(this) == url) return
        loginDetected = true
        CookieManager.getInstance().flush()
        AppPrefs.setAutoSeedUrl(this, url)
        AppPrefs.requestRediscovery(this)
        AppPrefs.setLastStatus(this, "KLAS 로그인 확인됨 · 자동 감시 페이지 탐색 중…")
        status.text = AppPrefs.lastStatus(this)
        loginHint.text = "로그인 완료. 이제 과목별 공지 화면을 직접 등록할 필요 없습니다."
        Scheduler.schedule(this)
        Scheduler.runNow(this)

        webView.postDelayed({
            webView.visibility = View.GONE
            toggleWeb.text = "KLAS 화면 열기"
            toast("로그인 완료. 자동 추적을 시작했습니다.")
        }, 900)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.visibility == View.VISIBLE && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}

