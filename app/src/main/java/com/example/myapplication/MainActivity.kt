package com.example.myapplication

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Toast
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gcta.shadow.ShadowDrawable
import org.gcta.shadow.ShadowFactory
import org.gcta.shadow.ShadowOptions
import java.io.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val view = findViewById<View>(R.id.root_bg)
        GlobalScope.launch(Dispatchers.Main) {
            val factory = ShadowFactory.create(this@MainActivity)
            val last = SystemClock.uptimeMillis()
            val dat = File(cacheDir, "sk1.dat")
            val background = if (dat.exists()) {
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
    }
}