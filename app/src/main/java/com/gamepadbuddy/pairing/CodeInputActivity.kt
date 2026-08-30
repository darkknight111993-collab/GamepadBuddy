package com.gamepadbuddy.pairing

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gamepadbuddy.R

/**
 * Activity dạng dialog (theme Dialog) nổi TRÊN mọi ứng dụng (kể cả màn hình Cài đặt),
 * để người dùng nhập mã 6 số ghép nối ngay tại chỗ — không cần chuyển qua lại giữa
 * Cài đặt và ứng dụng.
 *
 * Thay thế cho RemoteInput (bị Android 11+ cấm trên notification của foreground service).
 * Được mở từ notification "Nhập mã" do PairingService post khi dò được IP:port.
 */
class CodeInputActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_input)

        val endpoint = intent.getStringExtra("endpoint") ?: ""
        findViewById<TextView>(R.id.tvEndpoint).text = endpoint

        val et = findViewById<EditText>(R.id.etCode)
        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            val c = et.text.toString().trim()
            if (c.length != 6) {
                et.error = "Nhập đủ 6 số"
                return@setOnClickListener
            }
            // Gửi mã vào service (service sẽ gọi maybeRun khi có cả endpoint + code).
            PairingService.submitCode(this, c)
            finish()
        }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
    }
}
