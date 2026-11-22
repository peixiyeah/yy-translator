package com.personal.translator

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioFocusRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.io.File
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val upperBg = Color.rgb(250, 242, 218)
    private val lowerBg = Color.rgb(238, 232, 249)
    private val paper = Color.rgb(250, 249, 252)
    private val ink = Color.rgb(48, 48, 57)
    private val purple = Color.rgb(136, 115, 176)
    private val amber = Color.rgb(173, 126, 40)
    private val creamYellow = Color.rgb(234, 203, 121)
    private val paleCream = Color.rgb(255, 236, 190)
    private val warmInk = Color.rgb(66, 52, 29)
    private val outgoing = Color.rgb(218, 207, 238)
    private val grey = Color.rgb(224, 223, 230)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val recorder = VoiceRecorder()
    private lateinit var secrets: SecretStore
    private var storage: ConversationStore? = null
    private var apiKey = ""
    private var aliyunKey = ""
    private var aliyunInternational = false
    private var messages = mutableListOf<Utterance>()
    private var langs = arrayOf(Language.EN, Language.KO)
    private val auto = booleanArrayOf(true, true)
    private val selected = arrayOfNulls<String>(2)
    private val expanded = booleanArrayOf(true, true)
    private val drafts = arrayOf("", "")
    private val compositions = arrayOf("", "")
    private var keyboardSide: Int? = null
    private val panels = arrayOfNulls<LinearLayout>(2)
    private val statusViews = arrayOfNulls<TextView>(2)
    private var operation = 0
    private val boards = arrayOfNulls<LinearLayout>(2)
    private val microphones = arrayOfNulls<View>(2)
    private var requestSide: Int? = null
    private var retrySide: Int? = null
    private var highlightUntil = 0L
    private var busy = false
    private var recordingSide: Int? = null
    private var statusEn = "Ready to talk"
    private var statusKo = "대화를 시작하세요"
    private var client: DeepSeekClient? = null
    private var frenchVoiceClient: AlibabaFrenchTts? = null
    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var speakingSide: Int? = null
    private val voiceWorker = Executors.newSingleThreadExecutor()
    private var cancelVoice: (() -> Unit)? = null
    @Volatile private var audioGeneration = 0
    private var draftEditor: EditText? = null
    private var settingsDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        secrets = SecretStore(this)
        runCatching { apiKey = secrets.read() }.onFailure { notice("无法读取已保存的 Key，请重新填写。") }
        runCatching { aliyunKey = secrets.read("aliyun") }.onFailure { notice("无法读取已保存的百炼 Key，请重新填写。") }
        aliyunInternational = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("aliyunInternational", false)
        runCatching { storage = ConversationStore(this); messages = storage!!.current().toMutableList() }
            .onFailure { notice("历史记录读取失败。原文件已保留，本次暂不保存。") }
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        runCatching {
            langs[1] = Language.valueOf(preferences.getString("bottomLanguage", "KO") ?: "KO")
            langs[0] = Language.valueOf(preferences.getString("topLanguage", if (langs[1] == Language.EN) "KO" else "EN") ?: "EN")
            if (langs[0] == langs[1]) langs[0] = if (langs[1] == Language.EN) Language.KO else Language.EN
        }
        for (side in 0..1) auto[side] = preferences.getBoolean("auto$side", true)
        drawScreen()
        if (apiKey.isBlank()) setStatus("Add your API key in Settings", "설정에서 API 키를 입력하세요")
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun surface(color: Int, radius: Int = 16, border: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), Color.rgb(207, 201, 219))
    }
    private fun visibleVerticalScroll(view: View, color: Int) {
        view.isVerticalScrollBarEnabled = true
        view.isScrollbarFadingEnabled = false
        view.scrollBarSize = dp(5)
        view.scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            view.verticalScrollbarThumbDrawable = GradientDrawable().apply {
                setColor(color); cornerRadius = dp(4).toFloat(); setSize(dp(5), dp(38))
            }
        }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun label(text: String, size: Float = 14f, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(ink)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun action(text: String, primary: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 13f; gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else ink)
        background = surface(if (primary) purple else paper, 12, !primary)
        setPadding(dp(10), dp(7), dp(10), dp(7)); minHeight = dp(44)
        isClickable = true; isFocusable = true; contentDescription = text
        setOnClickListener { onClick() }
    }
    private fun local(side: Int, en: String, ko: String): String = when (langs[side]) {
        Language.FR, Language.JA -> when (en) {
            "Keyboard" -> if (langs[side] == Language.JA) "キーボード" else "Clavier"
            "Hold to talk" -> if (langs[side] == Language.JA) "長押しで話す" else "Maintenir"
            "Hold to record English" -> if (langs[side] == Language.JA) "長押しして録音" else "Maintenir pour enregistrer"
            "Translate" -> if (langs[side] == Language.JA) "翻訳" else "Traduire"
            "Translation" -> if (langs[side] == Language.JA) "訳文" else "Traduction"
            "Original" -> if (langs[side] == Language.JA) "原文" else "Original"
            "Close" -> if (langs[side] == Language.JA) "閉じる" else "Fermer"
            "space" -> if (langs[side] == Language.JA) "空白" else "Espace"
            "Play" -> if (langs[side] == Language.JA) "再生" else "Lire"
            "Stop" -> if (langs[side] == Language.JA) "停止" else "Arrêter"
            "Auto" -> if (langs[side] == Language.JA) "自動" else "Auto"
            "Auto •" -> if (langs[side] == Language.JA) "自動 ✓" else "Auto ✓"
            "Other side" -> if (langs[side] == Language.JA) "相手側" else "Autre côté"
            "Swap languages" -> if (langs[side] == Language.JA) "言語を交換" else "Échanger"
            "Ready to talk" -> if (langs[side] == Language.JA) "話せます" else "Prêt à parler"
            "Ready to reply" -> if (langs[side] == Language.JA) "返答できます" else "À vous de parler"
            "Type a message…" -> if (langs[side] == Language.JA) "メッセージを入力…" else "Saisir un message…"
            "Listening · release to translate" -> if (langs[side] == Language.JA) "録音中・離すと翻訳" else "Relâchez pour traduire"
            "Recognizing speech…" -> if (langs[side] == Language.JA) "音声認識中…" else "Transcription…"
            "Translating…" -> if (langs[side] == Language.JA) "翻訳中…" else "Traduction…"
            "Preparing voice…" -> if (langs[side] == Language.JA) "音声を準備中…" else "Préparation audio…"
            "Playing translation…" -> if (langs[side] == Language.JA) "再生中…" else "Lecture…"
            "Show latest message" -> if (langs[side] == Language.JA) "最新の発言を表示" else "Afficher le dernier message"
            "History is saved on this phone" -> if (langs[side] == Language.JA) "履歴はこの端末に保存されます" else "Historique enregistré sur cet appareil"
            "Add your API key in Settings" -> if (langs[side] == Language.JA) "設定で API キーを入力" else "Ajoutez votre clé API dans les réglages"
            "Conversation restored" -> if (langs[side] == Language.JA) "会話を再開しました" else "Conversation restaurée"
            "Testing connection…" -> if (langs[side] == Language.JA) "接続を確認中…" else "Test de connexion…"
            "Recording cancelled" -> if (langs[side] == Language.JA) "録音を取り消しました" else "Enregistrement annulé"
            "Try recording again" -> if (langs[side] == Language.JA) "もう一度録音してください" else "Réessayez d’enregistrer"
            "Could not finish · try again" -> if (langs[side] == Language.JA) "もう一度お試しください" else "Réessayez"
            else -> en
        }
        Language.KO -> ko
        Language.ZH -> when (en) {
            "Other side" -> "对方使用中"
            "Swap languages" -> "交换语言"
            "Ready to talk" -> "准备开始对话"
            "Add your API key in Settings" -> "请在设置中填写 API Key"
            "Original" -> "原文"
            "Translation" -> "译文"
            "Show latest message" -> "显示最新消息"
            "History is saved on this phone" -> "历史记录保存在本机"
            "Keyboard" -> "键盘"
            "Hold to talk" -> "按住说话"
            "Hold to record English" -> "按住录入${langs[side].label}"
            "Type a message…" -> "输入消息…"
            "Close" -> "关闭"
            "space" -> "空格"
            "Translate" -> "翻译"
            "Listening · release to translate" -> "正在聆听 · 松开后翻译"
            "Recording cancelled" -> "已取消录音"
            "Try recording again" -> "请重新录音"
            "Recognizing speech…" -> "正在识别语音…"
            "Translating…" -> "正在翻译…"
            "Ready to reply" -> "请对方继续说话"
            "Preparing voice…" -> "正在准备语音…"
            "Playing translation…" -> "正在播放译文…"
            "Auto" -> "自动"
            "Auto •" -> "自动 •"
            "Play" -> "播放"
            "Stop" -> "停止"
            else -> en
        }
        else -> en
    }
    private fun notice(message: String) { if (!isFinishing && !isDestroyed) Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun setStatus(en: String, ko: String) {
        statusEn = en; statusKo = ko
        for (side in 0..1) updateSignal(side)
    }
    private fun updateSignal(side: Int) {
        val panel = panels[side] ?: return
        val board = boards[side] ?: return
        val speaker = recordingSide ?: if (busy) requestSide else null
        val own = side == speaker
        val recording = recordingSide != null
        val normal = if (side == 0) upperBg else lowerBg
        val shade = android.animation.ArgbEvaluator().evaluate(.26f, normal, ink) as Int
        val target = if (own) shade else normal
        val current = (panel.background as? android.graphics.drawable.ColorDrawable)?.color ?: normal
        (panel.tag as? android.animation.ValueAnimator)?.cancel()
        if (current != target) {
            panel.tag = android.animation.ValueAnimator.ofArgb(current, target).apply {
                duration = 180
                addUpdateListener { panel.setBackgroundColor(it.animatedValue as Int) }
                start()
            }
        }
        for (i in 0 until panel.childCount) {
            val child = panel.getChildAt(i)
            if (child !== board && child !== microphones[side]?.parent) {
                child.animate().alpha(if (own) .70f else 1f).setDuration(180).start()
            }
        }
        board.removeAllViews()
        val state = statusViews[side] ?: return
        val retry = retrySide == side && speaker == null
        val active = speaker != null || retry
        state.visibility = if (active) View.GONE else View.VISIBLE
        state.text = local(side, statusEn, statusKo)
        board.visibility = if (active) View.VISIBLE else View.GONE
        val scale = if (own && recording) 1.18f else 1f
        microphones[side]?.apply {
            animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            background = surface(if (side == 0) purple else creamYellow, 100).apply {
                if (own && recording) setStroke(dp(3), if (side == 0) ink else warmInk)
            }
        }
        if (!active) return
        fun words(zh: String, en: String, ja: String, ko: String, fr: String) = when (langs[side]) {
            Language.ZH -> zh; Language.EN -> en; Language.JA -> ja; Language.KO -> ko; Language.FR -> fr
        }
        if (retry) {
            board.background = null
            board.addView(action(words("重新翻译","Retry translation","もう一度翻訳","다시 번역","Réessayer"), true) {
                submitText(side, drafts[side])
            })
            return
        }
        val fg = if (own) Color.WHITE else ink
        board.background = surface(if (own) Color.rgb(53,45,66) else paper, 16)
        if (!recording) board.addView(ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(fg)
        }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12) })
        val title = when {
            recording && own -> words("请说话","Speak now","お話しください","말씀하세요","Parlez")
            recording -> words("对方正在说话","Partner speaking","相手が話しています","상대방이 말하는 중","Votre partenaire parle")
            !own -> words("请稍候","Please wait","お待ちください","잠시만 기다려 주세요","Patientez")
            else -> local(side, statusEn, statusKo)
        }
        val detail = when {
            recording && own -> words("说完后松手","Release when finished","話し終えたら離す","말한 후 손을 떼세요","Relâchez après avoir parlé")
            recording -> ""
            statusEn.startsWith("Recognizing") -> words("正在处理录音","Processing audio","録音を処理中","녹음 처리 중","Traitement audio")
            else -> {
                val targetLanguage = langs[1 - (speaker ?: return)]
                val name = Locale.forLanguageTag(targetLanguage.code).getDisplayLanguage(Locale.forLanguageTag(langs[side].code))
                words("正在译成"+name+"，请稍候","Translating into "+name,name+"に翻訳中",name+" 번역 중","Traduction en "+name)
            }
        }
        board.addView(column().apply {
            addView(label(title, 27f, true).apply { setTextColor(fg) })
            if (detail.isNotEmpty()) addView(label(detail, 15f).apply { setTextColor(fg) })
        }, LinearLayout.LayoutParams(0,-2,1f))
    }
    private fun drawScreen() {
        val root = column().apply { setBackgroundColor(paper); fitsSystemWindows = true }
        for (side in 0..1) {
            val panel = column().apply {
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setBackgroundColor(if (side == 0) upperBg else lowerBg)
                if (side == 0) rotation = 180f
            }
            panels[side] = panel
            root.addView(panel, LinearLayout.LayoutParams(-1, 0, 1f))
            if (side == 0) root.addView(View(this).apply { setBackgroundColor(Color.rgb(190, 173, 215)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(14), 0, dp(14), 0) })
        }
        val nav = row().apply { setPadding(dp(10), dp(4), dp(10), dp(4)); setBackgroundColor(paper) }
        nav.addView(action("＋ 新对话") { newConversation() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        nav.addView(action("历史对话") { showHistory() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        nav.addView(action("设置") { showSettings() }, LinearLayout.LayoutParams(dp(64), dp(44)))
        root.addView(nav)
        setContentView(root)
        renderPanels()
    }
    private fun renderPanels() { for (side in 0..1) renderPanel(side) }
    private fun renderPanel(side: Int) {
        val panel = panels[side] ?: return
        panel.removeAllViews()
        if (languageSide == side) { languageMenu(side, panel); return }
        if (keyboardSide == side) { renderKeyboard(side, panel); return }
        val message = messages.lastOrNull { it.id == selected[side] } ?: messages.lastOrNull()
        // The reader and history share the space above the status and controls.
        val readingArea = column()
        panel.addView(readingArea, LinearLayout.LayoutParams(-1, 0, 1f))
        if (expanded[side]) {
            val cardColor = if (side == 0) Color.rgb(255, 253, 246) else paper
            val card = column().apply { background = surface(cardColor, 20, true); setPadding(dp(12), dp(4), dp(8), dp(4)) }
            val remaining = highlightUntil - android.os.SystemClock.uptimeMillis()
            if (remaining > 0) {
                card.background = surface(cardColor, 20).apply { setStroke(dp(3), if (side == 0) purple else amber) }
                card.postDelayed({ card.background = surface(cardColor, 20, true) }, remaining)
            }
            val heading = row()
            val title = if (message == null) langs[side].label else local(side,
                if (message.source == langs[side]) "Original" else "Translation",
                if (message.source == langs[side]) "원문" else "번역")
            heading.addView(label(title, 11f), LinearLayout.LayoutParams(0, dp(32), 1f))
            val close = TextView(this).apply {
                text = "×"; textSize = 19f; gravity = Gravity.CENTER; setTextColor(ink)
                background = surface(cardColor, 9); isClickable = true; isFocusable = true
                contentDescription = "关闭大字翻译框"
                setOnClickListener { expanded[side] = false; renderPanel(side) }
            }
            heading.addView(close, LinearLayout.LayoutParams(dp(36), dp(32)))
            card.addView(heading)
            val big = label(message?.textFor(langs[side]).orEmpty(), 26f, true).apply {
                setPadding(dp(2), dp(2), dp(7), dp(2)); gravity = Gravity.TOP
                setLineSpacing(dp(3).toFloat(), 1.08f); includeFontPadding = true
            }
            val scroll = ScrollView(this).apply { isFillViewport = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS; addView(big) }
            visibleVerticalScroll(scroll, if (side == 0) amber else purple)
            card.addView(scroll, LinearLayout.LayoutParams(-1, -2, 1f))
            val audio = row().apply { gravity = Gravity.END }
            audio.addView(speakerButton(side, true, auto[side]) {
                if (recordingSide == null) {
                    auto[side] = !auto[side]
                    if (!auto[side] && speakingSide == side) stopAudio()
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("auto$side", auto[side]).apply()
                    renderPanel(side)
                }
            }, LinearLayout.LayoutParams(dp(92), dp(44)).apply { marginEnd = dp(6) })
            audio.addView(speakerButton(side, false, speakingSide == side) { message?.let { playMessage(it, side) } }, LinearLayout.LayoutParams(dp(92), dp(44)))
            card.addView(audio)
            readingArea.addView(card, LinearLayout.LayoutParams(-1, -2))
        } else {
            readingArea.addView(action(local(side, "Show latest message", "최근 메시지 보기")) { expanded[side] = true; renderPanel(side) })
        }
        val history = ListView(this).apply {
            divider = null
            adapter = object : BaseAdapter() {
                override fun getCount() = maxOf(messages.size, 1)
                override fun getItem(position: Int) = messages.getOrNull(position)
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, recycled: View?, parent: android.view.ViewGroup): View {
                    val item = getItem(position) ?: return label(local(side, "History is saved on this phone", "대화 기록은 이 휴대폰에 저장됩니다"), 11f).apply { gravity = Gravity.CENTER }

            val own = item.speaker == side
            val wrapper = row().apply { gravity = if (own) Gravity.END else Gravity.START }
            val bubble = label(item.textFor(langs[side]), 14f).apply {
                val ownColor = if (side == 0) Color.rgb(243, 225, 174) else outgoing
                background = surface(if (own) ownColor else grey, 14)
                setPadding(dp(12), dp(8), dp(12), dp(8)); maxWidth = (resources.displayMetrics.widthPixels * .78f).toInt()
                isClickable = true; isFocusable = true
                setOnClickListener { selected[side] = item.id; expanded[side] = true; renderPanel(side) }
            }
                    wrapper.setPadding(0, dp(3), 0, dp(2))
                    wrapper.addView(bubble)
                    return wrapper
                }
            }
        }
        visibleVerticalScroll(history, if (side == 0) amber else purple)
        readingArea.addView(history, LinearLayout.LayoutParams(-1, 0, 1f))
        history.setSelection(history.count - 1)
        val state = label(local(side, statusEn, statusKo), 10f).apply { gravity = Gravity.CENTER; maxLines = 1; setTextColor(if (side == 0) amber else purple) }
        statusViews[side] = state
        panel.addView(state, LinearLayout.LayoutParams(-1, dp(18)))
        val board = row().apply {
            setPadding(dp(14), dp(3), dp(12), dp(3))
            minimumHeight = dp(68)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        boards[side] = board
        panel.addView(board, LinearLayout.LayoutParams(-1,-2))
        panel.clipChildren = false
        val controls = row().apply { clipChildren = false; gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) }
        controls.addView(action(local(side, "Keyboard", "키보드")) { if (!busy && recordingSide == null) { stopAudio(); keyboardSide = side; renderPanels() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        val mic = column().apply {
            val foreground = if (side == 0) Color.WHITE else warmInk
            gravity = Gravity.CENTER; background = surface(if (side == 0) purple else creamYellow, 100)
            addView(SymbolView(this@MainActivity, "mic", foreground), LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(label(local(side, "Hold to talk", "눌러서 말하기"), 10f).apply { setTextColor(foreground); gravity = Gravity.CENTER })
            isClickable = true; isFocusable = true; contentDescription = local(side, "Hold to record English", "길게 눌러 한국어 녹음")
        }
        mic.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (beginRecording(side)) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        view.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (recordingSide == side) endRecording(false)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (recordingSide == side) endRecording(true)
                    true
                }
                else -> true
            }
        }
        controls.addView(mic, LinearLayout.LayoutParams(dp(76), dp(76)).apply { setMargins(dp(14), 0, dp(14), 0) })
        controls.addView(action(langs[side].label + "⌄") { changeLanguages(side) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(controls, LinearLayout.LayoutParams(-1, dp(80)))
        microphones[side] = mic
        updateSignal(side)
    }

    private fun speakerButton(side: Int, toggle: Boolean, active: Boolean, onClick: () -> Unit): View = row().apply {
        val accent = if (side == 0) purple else warmInk
        val fill = if (!active) paper else if (side == 0) outgoing else paleCream
        gravity = Gravity.CENTER
        background = surface(fill, 12).apply {
            val border = if (side == 0) accent else if (active) paleCream else Color.rgb(178, 171, 184)
            setStroke(dp(1), border)
        }
        addView(SymbolView(this@MainActivity, "speaker", accent), LinearLayout.LayoutParams(dp(19), dp(19)))
        val text = if (!toggle && langs[side] == Language.FR && aliyunKey.isBlank()) "Configurer" else if (toggle) local(side, if (active) "Auto •" else "Auto", if (active) "자동 •" else "자동")
            else local(side, if (active) "Stop" else "Play", if (active) "정지" else "재생")
        addView(label(text, 12f).apply { setPadding(dp(5), 0, 0, 0) })
        isClickable = true; isFocusable = true; contentDescription = text
        setOnClickListener { onClick() }
    }

    private var phoneKeyboard = false
    private fun loadReadings(name: String): Map<String, String> {
        val id = resources.getIdentifier(name, "raw", packageName)
        return resources.openRawResource(id).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.associate { it.substringBefore('\t') to it.substringAfter('\t') }
        }
    }
    private val chineseReadings by lazy { loadReadings("zh_readings") }
    private val japaneseReadings by lazy { loadReadings("ja_readings") }
    private fun candidates(language: Language, pending: String): List<String> {
        val japanese = language == Language.JA
        val key = if (japanese) InputComposition.japaneseKana(pending) else pending
        val preferred = if (japanese) InputComposition.japaneseCandidates(pending) else InputComposition.chineseCandidates(key)
        val entries = if (japanese) japaneseReadings[key] else chineseReadings[key]
        val result = preferred.toMutableList()
        entries?.codePoints()?.forEach { result.add(String(Character.toChars(it))) }
        return result.distinct()
    }
    private fun hideKeyboard(editor: EditText) {
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(editor.windowToken, 0)
    }
    private fun renderKeyboard(side: Int, panel: LinearLayout) {
        val language = langs[side]
        val layout = column()
        panel.addView(layout, LinearLayout.LayoutParams(-1, -1))
        val title = row()
        title.addView(label(language.label, 14f, true), LinearLayout.LayoutParams(0, dp(40), 1f))
        title.addView(action(local(side, "Close", "닫기")) {
            draftEditor?.let { hideKeyboard(it); finishComposition(side, it) }; drafts[side] = draftEditor?.text.toString(); keyboardSide = null; renderPanels()
        })
        layout.addView(title)
        val usePhone = side == 1 && phoneKeyboard
        layout.isFocusableInTouchMode = true
        val editor = EditText(this).apply {
            textSize = 20f; setTextColor(ink); background = surface(paper, 12, true)
            setPadding(dp(12), dp(8), dp(12), dp(8)); gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            showSoftInputOnFocus = usePhone; filters = arrayOf(InputFilter.LengthFilter(2000)); overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setText(drafts[side]); setSelection(text.length)
            hint = local(side, "Type a message…", "메시지를 입력하세요…")
        }
        visibleVerticalScroll(editor, if (side == 0) amber else purple)
        if (side == 1) {
            val modes = row()
            val names = when (language) {
                Language.ZH -> "内置键盘" to "手机输入法"
                Language.JA -> "内蔵キーボード" to "端末キーボード"
                Language.KO -> "내장 키보드" to "휴대폰 키보드"
                Language.FR -> "Clavier intégré" to "Clavier système"
                Language.EN -> "Built-in keyboard" to "Phone keyboard"
            }
            for ((index, name) in listOf(names.first, names.second).withIndex()) {
                val phone = index == 1
                modes.addView(action(name, phone == usePhone) {
                    if (phone != usePhone) {
                        finishComposition(side, editor)
                        drafts[side] = editor.text.toString()
                        hideKeyboard(editor)
                        phoneKeyboard = phone
                        renderPanel(side)
                    }
                }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            layout.addView(modes)
        }
        draftEditor = editor
        layout.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(4) })
        if (usePhone) {
            editor.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { drafts[side] = s.toString() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            layout.addView(action(local(side, "Translate", "번역"), true) {
                if (drafts[side].isNotBlank()) { hideKeyboard(editor); submitText(side, drafts[side]) }
            })
            layout.requestFocus()
            return
        }
        var shifted = false
        var numeric = false
        val keys = column()
        val candidateRow = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val candidateScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; isFillViewport = false; addView(candidateRow)
        }
        layout.addView(candidateScroll, LinearLayout.LayoutParams(-1, dp(40)))
        var refreshCandidates: () -> Unit = {}

        fun replaceSelection(text: String) {
            val value = editor.text.toString()
            val a = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
            val b = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(a)
            val before = value.substring(0, a)
            val next = before + text
            val result = next + value.substring(b)
            if (result.length > 2000) return
            editor.setText(result); editor.setSelection(next.length); drafts[side] = result
        }
        fun insert(char: Char) {
            if (!numeric && language == Language.ZH && char.isLetter()) {
                compositions[side] += char.lowercaseChar(); refreshCandidates(); return
            }
            if (!numeric && language == Language.JA && char.isLetter()) {
                compositions[side] += char.lowercaseChar(); refreshCandidates(); return
            }
            val value = editor.text.toString()
            val a = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
            val b = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(a)
            val before = value.substring(0, a)
            val next = if (language == Language.KO && !numeric) Hangul.append(before, char) else before + char
            val result = next + value.substring(b)
            if (result.length > 2000) return
            editor.setText(result); editor.setSelection(next.length); drafts[side] = result
        }
        var page = 0
        var previousCompose = ""
        refreshCandidates = {
            candidateRow.removeAllViews()
            val compose = compositions[side]
            if (compose != previousCompose) { page = 0; previousCompose = compose }
            val options = when {
                language == Language.ZH && compose.isNotEmpty() -> candidates(language, compose).ifEmpty { listOf(compose) }
                language == Language.JA && compose.isNotEmpty() -> candidates(language, compose)
                language == Language.FR -> listOf("é", "è", "à", "ç", "ù", "â", "ê", "î", "ô", "û", "ë", "ï", "ü")
                else -> emptyList()
            }
            candidateScroll.visibility = if (options.isEmpty()) View.GONE else View.VISIBLE
            candidateScroll.scrollTo(0, 0)
            if (compose.isNotEmpty()) candidateRow.addView(label(compose, 14f))
            val paged = options.size > 12 && compose.isNotEmpty()
            if (paged) {
                for ((symbol, delta) in listOf("‹" to -1, "›" to 1)) {
                    candidateRow.addView(action(symbol) { page += delta; refreshCandidates() }.apply {
                        isEnabled = page + delta in 0..options.lastIndex / 12
                        minWidth = dp(36); setPadding(0,0,0,0)
                    }, LinearLayout.LayoutParams(dp(36), dp(34)))
                }
            }
            val visible = if (paged) options.drop(page * 12).take(12) else options
            visible.forEach { option ->
                val candidate = action(option) {
                    if (language == Language.FR) replaceSelection(option)
                    else { replaceSelection(option); compositions[side] = ""; refreshCandidates() }
                }.apply { minWidth = dp(42); minHeight = dp(34); setPadding(dp(10), dp(2), dp(10), dp(2)); textSize = 15f }
                candidateRow.addView(candidate, LinearLayout.LayoutParams(-2, dp(34)).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
        }
        fun erase() {
            if (compositions[side].isNotEmpty() && !numeric) {
                compositions[side] = compositions[side].dropLast(1); refreshCandidates(); return
            }
            val value = editor.text.toString()
            val a = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
            val b = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(a)
            val before = value.substring(0, a)
            val next = if (a != b) before else if (language == Language.KO) Hangul.backspace(before) else before.dropLast(if (before.isEmpty()) 0 else Character.charCount(before.codePointBefore(before.length)))
            editor.setText(next + value.substring(b)); editor.setSelection(next.length); drafts[side] = editor.text.toString()
        }
        fun drawKeys() {
            keys.removeAllViews()
            val layouts = when {
                numeric -> listOf("1234567890", "@#₩&*()-", ",.!?:;/'")
                language == Language.KO -> if (shifted) listOf("ㅃㅉㄸㄲㅆㅛㅕㅑㅒㅖ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ") else listOf("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ")
                language == Language.FR -> if (shifted) listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN") else listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
                shifted -> listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
                else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            }
            layouts.forEachIndexed { index, chars ->
                val r = row()
                if (index == 2) r.addView(action("⇧") { shifted = !shifted; drawKeys() }, LinearLayout.LayoutParams(0, dp(42), 1.3f))
                chars.forEach { char ->
                    val key = action(char.toString()) { insert(char) }.apply { setPadding(0, 0, 0, 0); textSize = 17f }
                    r.addView(key, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
                }
                if (index == 2) r.addView(action("⌫") { erase() }, LinearLayout.LayoutParams(0, dp(42), 1.3f))
                keys.addView(r)
            }
            val bottom = row()
            bottom.addView(action(if (numeric) "ABC" else "123") { finishComposition(side, editor); refreshCandidates(); numeric = !numeric; drawKeys() }, LinearLayout.LayoutParams(0, dp(42), 1f))
            bottom.addView(action(local(side, "space", "띄어쓰기")) {
                if (!numeric && (language == Language.ZH || language == Language.JA) && compositions[side].isNotEmpty()) { finishComposition(side, editor); refreshCandidates() }
                else insert(' ')
            }, LinearLayout.LayoutParams(0, dp(42), 2f).apply { setMargins(dp(3), 0, dp(3), 0) })
            bottom.addView(action(local(side, "Translate", "번역"), true) {
                finishComposition(side, editor)
                drafts[side] = editor.text.toString()
                if (drafts[side].isNotBlank()) submitText(side, drafts[side])
            }, LinearLayout.LayoutParams(0, dp(42), 1.4f))
            keys.addView(bottom)
        }
        refreshCandidates(); drawKeys(); layout.addView(keys); editor.requestFocus()
    }

    private fun finishComposition(side: Int, editor: EditText) {
        val pending = compositions[side]
        if (pending.isEmpty()) return
        val chosen = when (langs[side]) {
            Language.ZH -> candidates(Language.ZH, pending).firstOrNull() ?: pending
            Language.JA -> InputComposition.japaneseCandidates(pending).firstOrNull() ?: pending
            else -> pending
        }
        val value = editor.text.toString()
        val a = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val b = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(a)
        val result = value.substring(0, a) + chosen + value.substring(b)
        if (result.length <= 2000) {
            editor.setText(result); editor.setSelection(a + chosen.length); drafts[side] = result
        }
        compositions[side] = ""
    }

    private fun requireKey(): Boolean {
        if (apiKey.isNotBlank()) return true
        showSettings(); return false
    }
    private fun requireVoiceKey(language: Language): Boolean {
        val present = if (language == Language.FR) aliyunKey.isNotBlank() else apiKey.isNotBlank()
        if (present) return true
        notice(if (language == Language.FR) "法语朗读需要配置百炼 API Key；翻译仍使用 DeepSeek。" else "请先填写 DeepSeek API Key。")
        showSettings()
        return false
    }
    private fun beginRecording(side: Int): Boolean {
        if (busy || recordingSide != null || !requireKey()) return false
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10); return false
        }
        stopAudio()
        return try {
            recorder.start { main.post { if (recordingSide != null) endRecording(false) } }
            retrySide = null; requestSide = null; recordingSide = side
            if (keyboardSide == 1 - side || languageSide == 1 - side) {
                draftEditor?.let { editor -> keyboardSide?.let { drafts[it] = editor.text.toString() } }
                keyboardSide = null; languageSide = null; renderPanel(1 - side)
            }
            setStatus("Listening · release to translate", "듣는 중 · 손을 떼면 번역합니다")
            true
        } catch (e: Exception) { recorder.cancel(); notice(e.message ?: "无法录音"); false }
    }
    private fun endRecording(cancel: Boolean) {
        val side = recordingSide ?: return
        recordingSide = null
        if (cancel) { recorder.cancel(); setStatus("Recording cancelled", "녹음이 취소되었습니다"); return }
        try {
            val wav = recorder.stop()
            keyboardSide = null
            requestSide = side
            val token = startOperation("Recognizing speech…", "음성을 인식하고 있어요…")
            val request = DeepSeekClient(apiKey).also { client = it }
            val context = messages.toList()
            val language = langs[side]
            worker.execute {
                try {
                    val source = request.recognize(wav)
                    main.post { if (token == operation) { drafts[side] = source; setStatus("Translating…", "번역하고 있어요…") } }
                    val translated = request.translate(source, language, langs[1 - side], context)
                    main.post { if (token == operation) completeTranslation(side, source, translated, token) }
                } catch (e: Exception) { failOperation(token, e) }
            }
        } catch (e: Exception) { notice(e.message ?: "录音失败"); setStatus("Try recording again", "다시 녹음해 주세요") }
    }
    private fun submitText(side: Int, source: String) {
        if (busy || !requireKey()) return
        stopAudio(); keyboardSide = null
        retrySide = null; requestSide = side; drafts[side] = source
        val token = startOperation("Translating…", "번역하고 있어요…")
        val request = DeepSeekClient(apiKey).also { client = it }
        val context = messages.toList(); val language = langs[side]; val target = langs[1 - side]
        worker.execute {
            try {
                val translation = request.translate(source, language, target, context)
                main.post { if (token == operation) completeTranslation(side, source, translation, token) }
            } catch (e: Exception) { failOperation(token, e) }
        }
    }
    private fun startOperation(en: String, ko: String): Int {
        busy = true; operation++; setStatus(en, ko); renderPanels(); return operation
    }
    private fun completeTranslation(side: Int, original: String, translation: String, token: Int) {
        if (token != operation || isDestroyed) return
        busy = false; client = null
        requestSide = null; highlightUntil = android.os.SystemClock.uptimeMillis() + 850
        val item = Utterance(UUID.randomUUID().toString(), side, langs[side], langs[1 - side], original, translation)
        messages.add(item); drafts[side] = ""
        runCatching { storage?.save(messages) }.onFailure { notice("对话暂未保存：${it.message}") }
        for (i in 0..1) { selected[i] = item.id; expanded[i] = true }
        setStatus("Ready to reply", "이어서 말씀해 주세요"); renderPanels()

        if (auto[1 - side] && (item.target != Language.FR || aliyunKey.isNotBlank())) playMessage(item, 1 - side)
    }
    private fun failOperation(token: Int, e: Exception) {
        main.post {
            if (token == operation && !isDestroyed) {
                busy = false; client = null; frenchVoiceClient?.cancel(); frenchVoiceClient = null
                retrySide = requestSide?.takeIf { statusEn == "Translating…" && drafts[it].isNotBlank() }; requestSide = null
                setStatus("Could not finish · try again", "완료하지 못했어요 · 다시 시도해 주세요")
                renderPanels()
                notice(if (e is SocketTimeoutException) "网络超时，请重试。" else e.message ?: "请求失败，请检查网络。")
            }
        }
    }
    private fun audioFile(text: String, language: Language): File {
        val provider = if (language == Language.FR) "qwen-french" else "deepseek"
        val name = MessageDigest.getInstance("SHA-256").digest(("$provider:${language.code}:24kmono:$text").toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$name.pcm")
    }
    private fun playMessage(message: Utterance, side: Int) {
        if (recordingSide != null) return
        if (speakingSide == side) { stopAudio(); renderPanels(); return }
        val language = langs[side]
        if (busy || !requireVoiceKey(language)) return
        stopAudio()
        val text = message.textFor(language); val file = audioFile(text, language)
        
        val token = audioGeneration
        speakingSide = side
        setStatus("Preparing voice…", "음성을 준비하고 있어요…"); renderPanels()
        val request = if (language == Language.FR) AlibabaFrenchTts(aliyunKey, aliyunInternational).also { cancelVoice = it::cancel }
            else DeepSeekClient(apiKey).also { cancelVoice = it::cancel }
        voiceWorker.execute {
            try {
                val cached = file.isFile && file.length() > 0 && file.length() % 2 == 0L
                val audio = if (cached) file.readBytes() else when (request) {
                    is AlibabaFrenchTts -> request.synthesize(text)
                    is DeepSeekClient -> request.synthesize(text)
                    else -> error("未配置语音服务。")
                }
                if (!cached) file.writeBytes(audio)
                main.post { if (token == audioGeneration && !isDestroyed) {
                    cancelVoice = null; playPcm(audio, side)
                } }
            } catch (e: Exception) { file.delete(); main.post { if (token == audioGeneration) { stopAudio(); renderPanels(); notice("语音合成失败：${e.message}") } } }
        }
    }
    private fun playPcm(pcm: ByteArray, side: Int?, finishedMessage: String? = null) {
        stopAudio()
        val generation = audioGeneration
        try {
            check(pcm.size >= 2 && pcm.size % 2 == 0) { "语音数据不是有效的 16 位 PCM。" }
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            val format = AudioFormat.Builder().setSampleRate(AlibabaFrenchTts.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            val minBuffer = AudioTrack.getMinBufferSize(AlibabaFrenchTts.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0) { "手机不支持 24 kHz 单声道语音播放。" }
            val track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minBuffer * 2, 8192)).build()
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Android 没有初始化音频输出。" }
            audioTrack = track; speakingSide = side
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change -> if (change < 0 && generation == audioGeneration) { stopAudio(); renderPanels() } }.build()
            check(audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "媒体音频焦点被占用" }
            check(audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) { "媒体音量为零，请调高音量。" }
            track.setVolume(1f)
            val samples = if (pcm.size < track.bufferSizeInFrames * 2) pcm.copyOf(track.bufferSizeInFrames * 2) else pcm
            track.play()
            setStatus("Playing translation…", "음성을 재생하고 있어요…")
            renderPanels()
            voiceWorker.execute {
                var failure: String? = null
                try {
                    var offset = 0
                    while (offset < samples.size && generation == audioGeneration) {
                        val written = track.write(samples, offset, minOf(8192, samples.size - offset), AudioTrack.WRITE_BLOCKING)
                        if (written <= 0) throw IllegalStateException("AudioTrack.write=$written")
                        offset += written
                    }
                    val frames = samples.size / 2
                    val deadline = android.os.SystemClock.elapsedRealtime() + frames * 1000L / 24000 + 3000
                    while (generation == audioGeneration && track.playbackHeadPosition.toLong() < frames && track.playState == AudioTrack.PLAYSTATE_PLAYING) { check(android.os.SystemClock.elapsedRealtime() < deadline) { "播放超时" }; Thread.sleep(25) }
                } catch (e: Exception) { if (generation == audioGeneration) failure = e.message ?: e.javaClass.simpleName }
                main.post {
                    if (generation == audioGeneration && !isDestroyed) {
                        stopAudio(); renderPanels()
                        if (failure != null) notice("PCM 已生成，但手机播放失败：$failure。请检查媒体音量、蓝牙和扬声器输出。")
                        else if (finishedMessage != null) notice(finishedMessage)
                    }
                }
            }
        } catch (e: Exception) {
            stopAudio(); renderPanels()
            notice("语音数据已返回，但 Android 音频输出初始化失败：${e.message ?: e.javaClass.simpleName}")
        }
    }
    private fun stopAudio() {
        audioGeneration++; cancelVoice?.invoke(); cancelVoice = null
        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.release() }
        audioTrack = null
        audioFocusRequest?.let { request -> runCatching { audioManager?.abandonAudioFocusRequest(request) } }
        audioFocusRequest = null; speakingSide = null
        if (!busy && recordingSide == null) setStatus("Ready to talk", "대화를 시작하세요")
    }
    private fun testPhoneSpeaker() {
        val frames = AlibabaFrenchTts.SAMPLE_RATE / 2
        val pcm = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val fade = minOf(1.0, i / 600.0, (frames - i) / 600.0).coerceAtLeast(0.0)
            val value = (kotlin.math.sin(2.0 * Math.PI * 880.0 * i / AlibabaFrenchTts.SAMPLE_RATE) * 12_000 * fade).toInt()
            pcm[i * 2] = value.toByte(); pcm[i * 2 + 1] = (value shr 8).toByte()
        }
        playPcm(pcm, null, "本机扬声器测试音已播放完。若没听到，请检查媒体音量、静音模式及蓝牙输出。")
    }
    private fun newConversation(): Boolean {
        if (busy || recordingSide != null) return false
        stopAudio()
        runCatching { checkNotNull(storage).archive(messages) }.onFailure { notice("无法保存当前对话，请稍后重试。"); return false }
        languageSide = null; retrySide = null; messages.clear(); selected.fill(null); expanded.fill(true); drafts.fill(""); compositions.fill(""); keyboardSide = null; renderPanels()
        return true
    }
    private var languageSide: Int? = null
    private fun changeLanguages(side: Int) {
        if (busy || recordingSide != null) return
        stopAudio()
        keyboardSide?.let { drafts[it] = draftEditor?.text.toString() }; keyboardSide = null
        languageSide = side
        renderPanels()
    }
    private fun languageMenu(side: Int, panel: LinearLayout) {
        val options = column()
        fun choose(language: Language) {
            if (language != langs[side]) {
                if (!newConversation()) return
                if (language == langs[1-side]) langs[1-side] = langs[side]
                langs[side] = language
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("topLanguage", langs[0].name)
                    .putString("bottomLanguage", langs[1].name).apply()
            }
            languageSide = null
            renderPanels()
        }
        for (language in Language.entries) {
            val suffix = when (language) { langs[side] -> "  ✓"; langs[1-side] -> "  · " + local(side, "Other side", "상대방"); else -> "" }
            options.addView(action(language.label + suffix) { choose(language) }.apply { isEnabled = language != langs[1-side] })
        }
        options.addView(action("⇅ " + local(side, "Swap languages", "언어 교환")) { choose(langs[1-side]) })
        options.addView(action(local(side, "Close", "닫기")) { languageSide = null; renderPanels() })
        panel.addView(ScrollView(this).apply { addView(options) })
    }
    private fun showHistory() {
        if (busy || recordingSide != null) return
        val archives = storage?.archives().orEmpty()
        if (archives.isEmpty()) { notice("还没有历史对话，新开对话后会保存在这里。"); return }
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val cards = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val scroll = ScrollView(this).apply { addView(cards); overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        visibleVerticalScroll(scroll, purple)
        val dialog = AlertDialog.Builder(this).setTitle("历史对话 · 点击可继续")
            .setView(scroll).setNegativeButton("关闭", null).create()
        archives.forEach { (time, items) ->
            val first = items.firstOrNull()
            val title = first?.original?.replace('\n', ' ')?.take(42)?.ifBlank { "（空白对话）" } ?: "（空白对话）"
            val card = column().apply {
                background = surface(paper, 14, true); setPadding(dp(14), dp(10), dp(14), dp(10))
                isClickable = true; isFocusable = true
                setOnClickListener { dialog.dismiss(); resumeConversation(time) }
            }
            card.addView(label(title, 16f, true))
            val direction = first?.let { "${it.source.label} → ${it.target.label}" } ?: ""
            card.addView(label("${date.format(Date(time))}    $direction    ${items.size} 条", 12f).apply {
                setTextColor(Color.rgb(105, 100, 115)); setPadding(0, dp(5), 0, 0)
            })
            cards.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        dialog.show()
    }
    private fun resumeConversation(time: Long) {
        if (busy || recordingSide != null) return
        try {
            stopAudio(); retrySide = null; languageSide = null
            messages = storage?.resume(time, messages)?.toMutableList() ?: error("历史记录不可用。")
            messages.lastOrNull()?.let { latest ->
                langs[latest.speaker] = latest.source
                langs[1 - latest.speaker] = latest.target
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("topLanguage", langs[0].name).putString("bottomLanguage", langs[1].name).apply()
            }
            for (side in 0..1) {
                selected[side] = messages.lastOrNull()?.id
                expanded[side] = true
            }
            drafts.fill(""); compositions.fill(""); keyboardSide = null
            setStatus("Conversation restored", "대화를 복원했어요")
            renderPanels()
        } catch (e: Exception) { notice(e.message ?: "恢复历史对话失败。") }
    }
    private fun showSettings() {
        if (busy || recordingSide != null) { notice("请等待当前操作完成。"); return }
        stopAudio()
        val box = column().apply { setPadding(dp(24), dp(12), dp(24), dp(12)) }
        box.addView(label("YY翻译 · API 设置", 20f, true))
        box.addView(label("DeepSeek 负责中英日韩法识别与翻译，并朗读中英日韩；法语朗读走阿里云百炼。Key 均加密保存在本机。", 13f).apply { setPadding(0, dp(10), 0, dp(12)) })
        val input = EditText(this).apply {
            hint = if (apiKey.isBlank()) "粘贴 DeepSeek API Key" else "已保存；填写新 Key 可替换"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setSingleLine(true)
        }
        box.addView(input)
        val aliInput = EditText(this).apply {
            hint = if (aliyunKey.isBlank()) "百炼 API Key（仅法语朗读需要）" else "百炼 Key 已保存；填写新 Key 可替换"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setSingleLine(true)
        }
        box.addView(aliInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        lateinit var region: TextView
        region = action(if (aliyunInternational) "百炼地域：新加坡" else "百炼地域：中国（北京）") {
            AlertDialog.Builder(this).setTitle("百炼 API Key 所属地域")
                .setItems(arrayOf("中国（北京）", "新加坡")) { _, which ->
                    aliyunInternational = which == 1
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("aliyunInternational", aliyunInternational).apply()
                    region.text = if (aliyunInternational) "百炼地域：新加坡" else "百炼地域：中国（北京）"
                }.show()
        }
        box.addView(region, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        val speakerTest = action("试听手机扬声器（测试音）") { testPhoneSpeaker() }
        box.addView(speakerTest, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        val test = action("保存并测试当前语种对") {}
        box.addView(test, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        val report = label("先用上方测试音确认手机媒体输出，再测当前两种语言的翻译、合成与识别。接口会产生实际用量。", 12f).apply { setPadding(0, dp(12), 0, 0) }
        box.addView(report)
        val scroll = ScrollView(this).apply { addView(box) }
        val dialog = AlertDialog.Builder(this).setView(scroll).setPositiveButton("保存", null).setNegativeButton("关闭", null).setNeutralButton("清除 Key", null).create()
        settingsDialog = dialog
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            fun save(): Boolean = try {
                val value = input.text.toString().trim().ifBlank { apiKey }
                check(value.isNotBlank()) { "请先填写 API Key。" }
                check(value.none { it.isWhitespace() }) { "Key 中不能包含空格或换行。" }
                val aliValue = aliInput.text.toString().trim().ifBlank { aliyunKey }
                check(aliValue.none { it.isWhitespace() }) { "百炼 Key 中不能包含空格或换行。" }
                secrets.save(value); secrets.save(aliValue, "aliyun")
                apiKey = value; aliyunKey = aliValue; input.text.clear(); aliInput.text.clear(); true
            } catch (e: Exception) { report.text = e.message; false }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (!busy && save()) { setStatus("Ready to talk", "대화를 시작하세요"); dialog.dismiss() } }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (!busy) { runCatching {
                    secrets.save(""); secrets.save("", "aliyun"); apiKey = ""; aliyunKey = ""
                    input.text.clear(); aliInput.text.clear(); report.text = "两个 API Key 都已清除。"
                }.onFailure { report.text = "清除失败，请重试。" } }
            }
            test.setOnClickListener {
                if (!busy && save()) {
                    test.isEnabled = false; report.text = "正在测试…"
                    val token = startOperation("Testing connection…", "연결을 테스트하고 있어요…")
                    val request = DeepSeekClient(apiKey).also { client = it }
                    worker.execute {
                        val results = mutableListOf<String>()
                        try {
                            for (side in 0..1) {
                                val sourceLanguage = langs[side]; val targetLanguage = langs[1 - side]
                                val sample = sampleFor(sourceLanguage)
                                val translated = request.translate(sample, sourceLanguage, targetLanguage, emptyList())
                                results.add("✓ ${sourceLanguage.label} → ${targetLanguage.label} 翻译：\n$translated")
                                val audio = if (targetLanguage == Language.FR) {
                                    check(aliyunKey.isNotBlank()) { "当前译文为法语，请填写百炼 API Key 后测试语音。" }
                                    AlibabaFrenchTts(aliyunKey, aliyunInternational).also { frenchVoiceClient = it }.synthesize(translated)
                                } else request.synthesize(translated)
                                results.add("✓ ${targetLanguage.label} 语音合成：${audio.size / 1024} KB PCM")
                                val recognized = request.recognize(Wav.encode(audio, AlibabaFrenchTts.SAMPLE_RATE))
                                results.add("✓ 语音识别回读：$recognized")
                                main.post { if (token == operation) report.text = results.joinToString("\n\n") }
                            }
                            results.add("接口连接成功。请回到对话页试说一句，检查识别、翻译和播放是否正常。")
                        } catch (e: Exception) { results.add("✕ 测试未完成：${e.message ?: "网络请求失败"}") }
                        main.post {
                            if (token == operation && !isDestroyed) {
                                busy = false; client = null; frenchVoiceClient = null; test.isEnabled = true
                                report.text = results.joinToString("\n\n")
                                setStatus("Ready to talk", "대화를 시작하세요"); renderPanels()
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }
    private fun sampleFor(language: Language): String = when (language) {
        Language.ZH -> "请问，餐厅在哪里？"
        Language.EN -> "Where is the restaurant?"
        Language.JA -> "レストランはどこですか？"
        Language.KO -> "식당이 어디에 있나요?"
        Language.FR -> "Où se trouve le restaurant ?"
    }
    @Deprecated("Legacy back callback for app-local keyboard")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        languageSide?.let { languageSide = null; renderPanels(); return }
        val side = keyboardSide
        if (side != null) {
            drafts[side] = draftEditor?.text?.toString().orEmpty()
            draftEditor?.let { finishComposition(side, it) }
            keyboardSide = null; renderPanels()
        } else { super.onBackPressed() }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) notice(if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "麦克风已允许，请再次按住说话。" else "未获得麦克风权限，可以继续用键盘输入；也可到系统设置中开启权限。")
    }
    override fun onStop() {
        super.onStop()
        if (recordingSide != null) endRecording(true)
        requestSide = null; operation++; client?.cancel(); client = null; frenchVoiceClient?.cancel(); frenchVoiceClient = null; busy = false; stopAudio()
        settingsDialog?.dismiss(); settingsDialog = null
        draftEditor?.let { editor -> keyboardSide?.let { drafts[it] = editor.text.toString() } }
    }
    override fun onResume() { super.onResume(); if (::secrets.isInitialized) renderPanels() }
    override fun onDestroy() { recorder.cancel(); worker.shutdownNow(); voiceWorker.shutdownNow(); super.onDestroy() }
}
