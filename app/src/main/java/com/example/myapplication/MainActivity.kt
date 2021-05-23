package com.example.myapplication

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.gcta.shadow.ShadowFactory
import org.gcta.shadow.ShadowOptions

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val view = findViewById<View>(R.id.root_bg)
        GlobalScope.launch(Dispatchers.Main) {
            view.background = ShadowFactory.create(application)
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
    }
}