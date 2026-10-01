package com.carlauncher.player

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AppsActivity : Activity() {

    companion object {
        const val EXTRA_PICK = "pick"
        const val EXTRA_PKG = "pkg"
    }

    private class AppItem(val label: String, val pkg: String, val icon: Drawable)

    private var pick = false
    private val items = ArrayList<AppItem>()
    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
            val v = cv as? LinearLayout ?: LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(8), dp(6), dp(8))
                addView(ImageView(context), LinearLayout.LayoutParams(dp(64), dp(64)))
                addView(TextView(context).apply {
                    setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            }
            val it = items[p]
            (v.getChildAt(0) as ImageView).setImageDrawable(it.icon)
            (v.getChildAt(1) as TextView).text = it.label
            return v
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        pick = intent.getBooleanExtra(EXTRA_PICK, false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B10"))
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(TextView(this).apply {
            text = if (pick) "یک برنامه انتخاب کنید" else "همه برنامه‌ها"
            setTextColor(Color.WHITE); textSize = 22f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(TextView(this).apply {
            text = "✕"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { finish() }
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(130)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(12)
            adapter = this@AppsActivity.adapter
            setOnItemClickListener { _, _, pos, _ ->
                val app = items[pos]
                if (pick) {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_PKG, app.pkg))
                    finish()
                } else {
                    packageManager.getLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
                }
            }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { items.addAll(list); adapter.notifyDataSetChanged() }
        }.start()
    }
}
