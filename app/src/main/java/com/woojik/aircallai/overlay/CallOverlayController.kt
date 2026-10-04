package com.woojik.aircallai.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout

/**
 * PRD-07 플로팅/백그라운드 제어.
 * 다른 앱 사용 중 작은 오버레이 컨트롤(음소거/일시정지/종료)을 제공한다.
 *
 * - 오버레이 권한이 없으면 show()가 조용히 무시된다 (권한 거부 상태도 정상 동작).
 *   이때는 PRD-05의 시스템 지속 알림이 기본 제어 수단으로 남는다.
 * - 오버레이는 앱의 핵심 기능을 방해하지 않는 크기/위치로 띄운다.
 */
class CallOverlayController(
    private val context: Context,
    private val actions: OverlayActions,
    private val onAction: (String) -> Unit,
) {

    /** 오버레이 버튼이 Service로 보내는 액션 모음. */
    data class OverlayActions(
        val mute: String,
        val unmute: String,
        val pause: String,
        val resume: String,
        val end: String,
    )

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var rootView: LinearLayout? = null
    private var muteButton: Button? = null
    private var pauseButton: Button? = null
    private var muteAction: String = actions.mute
    private var pauseAction: String = actions.pause

    val isOverlayAllowed: Boolean
        get() = Settings.canDrawOverlays(context)

    val isShowing: Boolean
        get() = rootView != null

    @SuppressLint("InflateParams")
    fun show() {
        if (!isOverlayAllowed || isShowing) return

        val dp: (Int) -> Int = { value ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                context.resources.displayMetrics,
            ).toInt()
        }

        val darkTheme = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val overlayBackground = if (darkTheme) Color.rgb(32, 33, 36) else Color.WHITE
        val overlayForeground = if (darkTheme) Color.WHITE else Color.rgb(32, 33, 36)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(overlayBackground)
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        fun button(label: String): Button = Button(context).apply {
            text = label
            isAllCaps = false
            minWidth = dp(72)
            minHeight = dp(48)
            setTextColor(overlayForeground)
            // PRD-07 접근성: 명확한 텍스트 라벨 제공.
            contentDescription = label
        }

        muteButton = button("음소거").apply { setOnClickListener { onAction(muteAction) } }
        pauseButton = button("일시정지").apply { setOnClickListener { onAction(pauseAction) } }
        val endButton = button("종료").apply { setOnClickListener { onAction(actions.end) } }

        row.addView(muteButton)
        row.addView(pauseButton)
        row.addView(endButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(96)
        }

        windowManager.addView(row, params)
        rootView = row
    }

    /** 세션 상태에 맞춰 버튼 라벨/액션을 갱신한다. */
    fun refresh(paused: Boolean, muted: Boolean) {
        pauseAction = if (paused) actions.resume else actions.pause
        muteAction = if (muted) actions.unmute else actions.mute
        pauseButton?.text = if (paused) "재개" else "일시정지"
        muteButton?.text = if (muted) "음소거 해제" else "음소거"
    }

    /** 세션 활성 여부에 따라 오버레이를 띄우거나 내린다. */
    fun sync(active: Boolean) {
        if (active) show() else hide()
    }

    fun hide() {
        val row = rootView ?: return
        rootView = null
        muteButton = null
        pauseButton = null
        runCatching { windowManager.removeView(row) }
    }
}
