package com.example.win98

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TEAL = Color.rgb(0, 128, 128)
private val GRAY = Color.rgb(192, 192, 192)
private val LIGHT = Color.rgb(223, 223, 223)
private val SHADOW = Color.rgb(128, 128, 128)
private val NAVY = Color.rgb(0, 0, 128)
private const val MP = ViewGroup.LayoutParams.MATCH_PARENT
private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT

/** Классическая рамка Windows 98: выпуклая (кнопка) или вдавленная (поле). */
class Bevel(private val raised: Boolean, private val s: Float) : Drawable() {
    private val p = Paint()

    private fun fill(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        p.color = color
        c.drawRect(l, t, r, b, p)
    }

    override fun draw(c: Canvas) {
        val l = bounds.left.toFloat()
        val t = bounds.top.toFloat()
        val r = bounds.right.toFloat()
        val b = bounds.bottom.toFloat()
        val o1 = if (raised) Color.WHITE else SHADOW
        val o2 = if (raised) Color.BLACK else Color.WHITE
        val i1 = if (raised) LIGHT else Color.BLACK
        val i2 = if (raised) SHADOW else LIGHT
        fill(c, l, t, r, b, o2)
        fill(c, l, t, r - s, b - s, o1)
        fill(c, l + s, t + s, r - s, b - s, i2)
        fill(c, l + s, t + s, r - 2 * s, b - 2 * s, i1)
        fill(c, l + 2 * s, t + 2 * s, r - 2 * s, b - 2 * s, GRAY)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(filter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.OPAQUE
}

/** Синяя вертикальная полоса с надписью в меню «Пуск». */
class Banner(ctx: Context, private val title: String) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(0f, h, 0f, 0f, NAVY, Color.rgb(16, 132, 208), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.color = Color.WHITE
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = w * 0.5f
        canvas.rotate(-90f)
        canvas.drawText(title, -(h - w * 0.4f), w * 0.72f, p)
    }
}

class MainActivity : Activity() {

    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()

    private lateinit var grid: GridView
    private lateinit var clock: TextView
    private lateinit var startBtn: TextView
    private lateinit var menuLayer: FrameLayout
    private var apps: List<ResolveInfo> = emptyList()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = updateClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = TEAL
        window.navigationBarColor = GRAY

        val root = FrameLayout(this)
        root.setBackgroundColor(TEAL)

        // Рабочий стол: сетка иконок
        grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(10)
            setSelector(android.R.color.transparent)
            setPadding(dp(6), dp(14), dp(6), dp(8))
            clipToPadding = false
            adapter = AppAdapter()
            setOnItemClickListener { _, _, pos, _ -> launch(apps[pos]) }
        }

        // Панель задач
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Bevel(true, d)
            setPadding(dp(5), dp(5), dp(5), dp(4))
        }
        startBtn = TextView(this).apply {
            text = "Пуск"
            typeface = Typeface.DEFAULT_BOLD
            textSize = 15f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = Bevel(true, d)
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { setMenu(menuLayer.visibility != View.VISIBLE) }
        }
        clock = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = Bevel(false, d)
            setPadding(dp(12), 0, dp(12), 0)
        }
        bar.addView(startBtn, LinearLayout.LayoutParams(WC, dp(32)))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(clock, LinearLayout.LayoutParams(WC, dp(28)))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(grid, LinearLayout.LayoutParams(MP, 0, 1f))
        col.addView(bar, LinearLayout.LayoutParams(MP, dp(42)))
        root.addView(col, FrameLayout.LayoutParams(MP, MP))

        // Меню «Пуск»
        val items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.addView(menuItem("Телефон") {
            startActivity(Intent(Intent.ACTION_DIAL))
        })
        items.addView(menuItem("Настройки") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Bevel(true, d)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isClickable = true
            minimumHeight = dp(170)
        }
        menu.addView(Banner(this, "Windows 98"), LinearLayout.LayoutParams(dp(30), MP))
        menu.addView(items, LinearLayout.LayoutParams(dp(190), WC))

        menuLayer = FrameLayout(this).apply {
            visibility = View.GONE
            setOnClickListener { setMenu(false) }
        }
        menuLayer.addView(
            menu,
            FrameLayout.LayoutParams(WC, WC, Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = dp(42)
            }
        )
        root.addView(menuLayer, FrameLayout.LayoutParams(MP, MP))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        updateClock()
        registerReceiver(tick, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(tick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setMenu(false)
        grid.setSelection(0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setMenu(false) // лаунчер не закрывается кнопкой «Назад»
    }

    private fun setMenu(open: Boolean) {
        menuLayer.visibility = if (open) View.VISIBLE else View.GONE
        startBtn.background = Bevel(!open, d)
    }

    private fun updateClock() {
        clock.text = fmt.format(Date())
    }

    private fun loadApps() {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
            .map { it.first }
        (grid.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun launch(ri: ResolveInfo) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { startActivity(i) } catch (_: Exception) {}
    }

    private fun menuItem(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 16f
        setTextColor(Color.BLACK)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(8), 0)
        layoutParams = LinearLayout.LayoutParams(MP, dp(44))
        setOnTouchListener { v, e ->
            val tv = v as TextView
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tv.setBackgroundColor(NAVY)
                    tv.setTextColor(Color.WHITE)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tv.setBackgroundColor(Color.TRANSPARENT)
                    tv.setTextColor(Color.BLACK)
                }
            }
            false
        }
        setOnClickListener {
            setMenu(false)
            try { action() } catch (_: Exception) {}
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell = (convertView as? LinearLayout) ?: makeCell()
            val ri = apps[position]
            (cell.getChildAt(0) as ImageView).setImageDrawable(ri.loadIcon(packageManager))
            (cell.getChildAt(1) as TextView).text = ri.loadLabel(packageManager)
            return cell
        }

        private fun makeCell(): LinearLayout {
            val cell = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(4), dp(2), dp(4))
            }
            cell.addView(ImageView(this@MainActivity), LinearLayout.LayoutParams(dp(44), dp(44)))
            cell.addView(TextView(this@MainActivity).apply {
                textSize = 12f
                setTextColor(Color.WHITE)
                setShadowLayer(1f, 1f, 1f, Color.BLACK)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(MP, WC))
            return cell
        }
    }
}
