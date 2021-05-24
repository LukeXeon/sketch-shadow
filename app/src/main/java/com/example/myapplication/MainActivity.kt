package com.example.myapplication

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Space
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.gcta.shadow.ShadowFactory
import org.gcta.shadow.ShadowOptions
import java.lang.Exception

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val view = findViewById<View>(R.id.root_bg)
        GlobalScope.launch(Dispatchers.Main) {
            view.background = ShadowFactory.create(this@MainActivity)
                .newDrawable(ShadowOptions().apply {
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
            Toast.makeText(application, "渲染完成", Toast.LENGTH_LONG).show()
        }
        createTest()
    }

    fun createTest() {
        val toast = Toast(application)
        toast.view = Space(application).apply {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {

                }

                override fun onViewDetachedFromWindow(v: View) {
                    toast.view = v
                    toast.duration = Toast.LENGTH_LONG
                    toast.show()
                    Exception().printStackTrace()
                    Log.d("TEST", "onViewDetachedFromWindow")
                }
            })
        }
        toast.duration = Toast.LENGTH_LONG
        toast.show()
    }
}