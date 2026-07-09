package com.cashguard.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.cashguard.app.MainActivity
import com.cashguard.app.data.TransactionEntity
import com.cashguard.app.data.TxType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Truecaller-style transaction popup: a branded card drawn over whatever app
 * is open, using a TYPE_APPLICATION_OVERLAY window. Auto-dismisses after 8 s;
 * swipe sideways to dismiss; tap "View" to open the app.
 *
 * Requires the "Display over other apps" permission — callers must check
 * [canShow] and fall back to a plain notification otherwise.
 */
object TransactionPopupWindow {

    private const val AUTO_DISMISS_MS = 8000L
    private const val SWIPE_DISMISS_PX = 250f

    // Palette mirrors ui/theme/Color.kt (View system can't use Compose colors)
    private const val COLOR_SURFACE = 0xFF1E1E30.toInt()
    private const val COLOR_GRADIENT_START = 0xFF6C5CE7.toInt()
    private const val COLOR_GRADIENT_END = 0xFF5B7CFA.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFF2F2F7.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFA0A0B8.toInt()
    private const val COLOR_GREEN = 0xFF00D9A3.toInt()
    private const val COLOR_RED = 0xFFFF6B6B.toInt()

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Safe to call from any thread. */
    fun show(context: Context, transaction: TransactionEntity) {
        Handler(Looper.getMainLooper()).post {
            runCatching { showOnMainThread(context.applicationContext, transaction) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOnMainThread(context: Context, tx: TransactionEntity) {
        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val isDebit = tx.type == TxType.DEBIT
        val handler = Handler(Looper.getMainLooper())
        var dismissed = false

        val root = FrameLayout(context)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadius = dp(20).toFloat()
            }
            clipToOutline = true
            elevation = dp(12).toFloat()
        }

        // ---- Gradient header ----
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(COLOR_GRADIENT_START, COLOR_GRADIENT_END)
            ).apply {
                cornerRadii = floatArrayOf(
                    dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
        }
        val bankBadge = TextView(context).apply {
            text = tx.source.firstOrNull()?.uppercase() ?: "B"
            setTextColor(COLOR_GRADIENT_START)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                shape = GradientDrawable.OVAL
            }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }
        val bankName = TextView(context).apply {
            text = tx.source
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val appTag = TextView(context).apply {
            text = "CashGuard"
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 11f
        }
        header.addView(bankBadge)
        header.addView(bankName)
        header.addView(appTag)
        card.addView(header)

        // ---- Body ----
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(6))
        }
        val amountText = TextView(context).apply {
            text = (if (isDebit) "− Rs %,.2f spent" else "+ Rs %,.2f received").format(tx.amount)
            setTextColor(if (isDebit) COLOR_RED else COLOR_GREEN)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        val balanceText = TextView(context).apply {
            text = "Remaining balance: Rs %,.2f".format(tx.balanceAfter)
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 14f
            setPadding(0, dp(4), 0, 0)
        }
        val metaText = TextView(context).apply {
            text = "${tx.merchant} • " +
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(tx.timestamp))
            setTextColor(COLOR_TEXT_SECONDARY)
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        body.addView(amountText)
        body.addView(balanceText)
        body.addView(metaText)
        card.addView(body)

        fun dismiss() {
            if (dismissed) return
            dismissed = true
            handler.removeCallbacksAndMessages(null)
            runCatching { windowManager.removeView(root) }
        }

        // ---- Actions ----
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        fun actionButton(label: String, color: Int, onClick: () -> Unit) =
            TextView(context).apply {
                text = label
                setTextColor(color)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener { onClick() }
            }
        actions.addView(actionButton("DISMISS", COLOR_TEXT_SECONDARY) { dismiss() })
        actions.addView(actionButton("VIEW", 0xFF9C8CFF.toInt()) {
            dismiss()
            runCatching { context.startActivity(MainActivity.newIntent(context)) }
        })
        card.addView(actions)

        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), 0, dp(12), 0) }
        )

        // ---- Swipe sideways to dismiss ----
        var downX = 0f
        card.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX = event.rawX - downX
                    view.alpha = 1f - (abs(view.translationX) / (SWIPE_DISMISS_PX * 2)).coerceIn(0f, 0.7f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (abs(view.translationX) > SWIPE_DISMISS_PX) {
                        dismiss()
                    } else {
                        view.animate().translationX(0f).alpha(1f).setDuration(150).start()
                    }
                    true
                }
                else -> false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            // Below the heads-up notification zone so both stay visible
            y = dp(180)
        }

        runCatching { windowManager.addView(root, params) }.onFailure { return }

        // Slide-in
        card.translationY = -dp(120).toFloat()
        card.alpha = 0f
        card.animate().translationY(0f).alpha(1f).setDuration(250).start()

        handler.postDelayed({ dismiss() }, AUTO_DISMISS_MS)
    }
}
