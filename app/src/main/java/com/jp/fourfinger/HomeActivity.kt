package com.jp.fourfinger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class HomeActivity : AppCompatActivity() {

    lateinit var btn : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        btn = findViewById(R.id.btnClick)

        btn.setOnClickListener {
            Intent(this , com.jp.cameramodule.MainActivity::class.java).apply {
                startActivity(this)
            }
        }
    }
}

