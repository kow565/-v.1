package com.ojun.klaswatch

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var targetsBox: LinearLayout
    private lateinit var webhookInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var intervalSpinner: Spinner

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "KLAS Watch"
            textSize = 26f
            setTextColor(Color.BLACK)
        })
        root.addView(TextView(this).apply {
            text = "PC 없이 휴대폰에서 KLAS 공지를 감시하고 Gmail로 중계합니다. KLAS 비밀번호는 앱이 저장하지 않습니다."
            textSize = 14f
        })

        status = TextView(this).apply {
            text = AppPrefs.lastStatus(this@MainActivity)
            setPadding(0, 18, 0, 18)
        }
        root.addView(status)

        root.addView(section("1. Gmail 중계 설정"))
        webhookInput = input("Apps Script 웹 앱 URL", AppPrefs.webhook(this))
        tokenInput = input("중계 보안 토큰", AppPrefs.token(this))
        root.addView(webhookInput)
        root.addView(tokenInput)

        intervalSpinner = Spinner(this)
        val intervals = listOf("15분", "30분", "60분")
        intervalSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervals)
        intervalSpinner.setSelection(when (AppPrefs.intervalMinutes(this)) { 60L -> 2; 30L -> 1; else -> 0 })
        root.addView(intervalSpinner)

        val save = Button(this).apply { text = "설정 저장 + 백그라운드 감시 시작" }
        save.setOnClickListener {
            val min = when (intervalSpinner.selectedItemPosition) { 2 -> 60L; 1 -> 30L; else -> 15L }
            AppPrefs.saveSettings(this, webhookInput.text.toString(), tokenInput.text.toString(), min)
            Scheduler.schedule(this)
            toast("저장했습니다. 앱을 닫아도 감시가 예약됩니다.")
        }
        root.addView(save)

        val test = Button(this).apply { text = "Gmail 중계 테스트 메일 보내기" }
        test.setOnClickListener {
            val url = webhookInput.text.toString().trim()
            val token = tokenInput.text.toString().trim()
            Executors.newSingleThreadExecutor().execute {
                val ok = runCatching { RelayClient.sendTest(url, token) }.getOrDefault(false)
                runOnUiThread { toast(if (ok) "테스트 전송 성공" else "전송 실패: 웹훅 URL/토큰을 확인하세요") }
            }
        }
        root.addView(test)

        root.addView(section("2. KLAS 로그인 및 감시 페이지 추가"))
        root.addView(TextView(this).apply {
            text = "아래 KLAS 창에서 직접 로그인 → 감시하고 싶은 과목 공지 목록까지 이동 → ‘현재 페이지 감시 추가’를 누르세요. 여러 과목을 반복해서 추가할 수 있습니다."
        })

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 900)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString + " KLASWatch/0.1"
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        root.addView(webView)

        val navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val loginBtn = Button(this).apply { text = "KLAS 로그인 열기" }
        loginBtn.setOnClickListener { webView.loadUrl("https://klas.kw.ac.kr/usr/cmn/login/LoginForm.do") }
        val addBtn = Button(this).apply { text = "현재 페이지 감시 추가" }
        addBtn.setOnClickListener {
            val url = webView.url.orEmpty()
            val title = webView.title.orEmpty().ifBlank { "KLAS 공지" }
            if (!url.startsWith("https://")) toast("먼저 KLAS 페이지를 여세요") else {
                AppPrefs.addTarget(this, title, url)
                refreshTargets()
                Scheduler.schedule(this)
                toast("감시 대상에 추가했습니다. 최초 검사는 기준선만 저장합니다.")
            }
        }
        navRow.addView(loginBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        navRow.addView(addBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(navRow)

        root.addView(section("3. 감시 대상"))
        targetsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(targetsBox)
        refreshTargets()

        val now = Button(this).apply { text = "지금 즉시 검사" }
        now.setOnClickListener {
            Scheduler.runNow(this)
            toast("검사를 요청했습니다. 잠시 뒤 상태가 갱신됩니다.")
        }
        root.addView(now)

        val refresh = Button(this).apply { text = "검사 상태 새로고침" }
        refresh.setOnClickListener { status.text = AppPrefs.lastStatus(this) }
        root.addView(refresh)

        root.addView(TextView(this).apply {
            text = "※ Android의 절전/Doze 정책 때문에 15분은 ‘정확히 매 15분’이 아니라 시스템이 허용하는 시점에 실행됩니다. 앱을 강제 종료(Force stop)하면 다시 앱을 열기 전까지 작업이 멈출 수 있습니다."
            textSize = 12f
            setPadding(0, 22, 0, 40)
        })

        if (webView.url == null) webView.loadUrl("https://klas.kw.ac.kr/usr/cmn/login/LoginForm.do")
        Scheduler.schedule(this)
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 19f
        setTextColor(Color.BLACK)
        setPadding(0, 28, 0, 8)
    }

    private fun input(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        isSingleLine = true
    }

    private fun refreshTargets() {
        if (!::targetsBox.isInitialized) return
        targetsBox.removeAllViews()
        val list = AppPrefs.targets(this)
        if (list.isEmpty()) {
            targetsBox.addView(TextView(this).apply { text = "아직 등록된 공지 페이지가 없습니다." })
            return
        }
        list.forEach { t ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply {
                text = "${t.name}\n${t.url}"
                textSize = 12f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "삭제"
                setOnClickListener { AppPrefs.removeTarget(this@MainActivity, t.id); refreshTargets() }
            })
            targetsBox.addView(row)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
