package com.example.moneyhelper

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import org.tensorflow.lite.examples.detection.DetectorActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val yoloBtn=findViewById<Button>(R.id.yoloBtn)
        val ssdBtn=findViewById<Button>(R.id.ssdBtn)
        yoloBtn.setOnClickListener { startYoloActivity(this) }
        ssdBtn.setOnClickListener { startSSDActivity(this)  }
    }
    private fun startYoloActivity(context: Context){
        val intent=Intent(context,YoloActivity::class.java)
        startActivity(intent)
    }
    private fun startSSDActivity(context: Context){
        val intent=Intent(context, DetectorActivity::class.java)
        startActivity(intent)
    }
}