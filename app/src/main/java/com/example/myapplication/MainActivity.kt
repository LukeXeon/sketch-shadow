package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.os.*
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.WebView
import android.widget.Toast
import com.google.android.material.shape.MaterialShapeDrawable
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gcta.shadow.ShadowDrawable
import org.gcta.shadow.ShadowFactory
import org.gcta.shadow.ShadowOptions
import java.io.*
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {

        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val view = findViewById<View>(R.id.root_bg)
        GlobalScope.launch(Dispatchers.Main) {
            val factory = ShadowFactory.create(this@MainActivity)
            val last = SystemClock.uptimeMillis()
            val dat = File(cacheDir, "sk1.dat")
            val background = if (false) {
                withContext(Dispatchers.IO) {
                    val stream = ObjectInputStream(FileInputStream(dat))
                    stream.readObject() as ShadowDrawable
                }
            } else {
                factory.newDrawable(ShadowOptions().apply {
                    fillColor = Color.WHITE
                    shadowBlur = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        20f,
                        resources.displayMetrics
                    ).toInt()
                    setRoundRadius(
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            20f,
                            resources.displayMetrics
                        ).toInt()
                    )
                })
            }
            view.background = background
            Toast.makeText(
                application,
                "渲染完成" + (SystemClock.uptimeMillis() - last),
                Toast.LENGTH_LONG
            ).show()
            withContext(Dispatchers.IO) {
                dat.createNewFile()
                val stream = ObjectOutputStream(FileOutputStream(dat))
                stream.writeObject(background)
                stream.close()
            }
        }
        val view2 = findViewById<View>(R.id.root_bg2)
        view2.background = MaterialShapeDrawable().apply {
            elevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f,
                resources.displayMetrics
            )
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
            setCornerSize(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    20f,
                    resources.displayMetrics
                )
            )
            setShadowColor(Color.parseColor("#757575"))
            fillColor = ColorStateList.valueOf(Color.WHITE)
        }
        val view3 = findViewById<View>(R.id.root_bg3)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view3.clipToOutline = true
            view3.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        Rect(0, 0, view.width, view.height),
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            20f,
                            resources.displayMetrics
                        )
                    )
                }
            }
        }
        val view4 = findViewById<View>(R.id.root_bg4)
        view4.background = PaintShadowDrawable().apply {
            radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f,
                resources.displayMetrics
            )
            shadow = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f,
                resources.displayMetrics
            )
        }
        val classPath = System.getProperty("java.class.path", ".")
        val librarySearchPath = System.getProperty("java.library.path", "")
//        val cl = object : PathClassLoader(
//            classPath,
//            librarySearchPath,
//            Activity::class.java.classLoader
//        ) {
//            override fun loadClass(name: String?, resolve: Boolean): Class<*> {
//                return if (name == WebView::class.java.name) {
//                    findClass(name)
//                } else {
//                    super.loadClass(name, resolve)
//                }
//            }
//        }
//        Log.d(
//            TAG,
//            "WebView " + (cl.loadClass(WebView::class.java.name) == WebView::class.java).toString()
//        )
        Log.d(TAG, "W CL" + WebView::class.java.classLoader)
        Log.d(TAG, Class.forName("android.webkit.WebViewFactory").classLoader.toString())
    }

}