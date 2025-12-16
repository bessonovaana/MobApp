package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.zeromq.ZMQ
import java.io.File
import kotlin.concurrent.thread



class Socket : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_socket)

        val sendButton = findViewById<Button>(R.id.st)
        val responseText = findViewById<TextView>(R.id.tvSocket)
        val button_b: Button = findViewById(R.id.bk)


        button_b.setOnClickListener {
            val intent = Intent(this, MenuActivity::class.java)
            startActivity(intent)
            finish()

        }

        sendButton.setOnClickListener {
            thread {
                try {
                    val context = ZMQ.context(2)
                    val socket = context.socket(ZMQ.REQ)

                    // Сначала подключаемся, потом отправляем
                    socket.connect("tcp://192.168.137.1:5500")
                    // Отправляем файл или сообщение

                    socket.send("Hello!".toByteArray(ZMQ.CHARSET), 0)

                    val reply = socket.recv(0)
                    runOnUiThread {
                        responseText.text = "Ответ сервера: ${String(reply, ZMQ.CHARSET)}"
                    }

                    socket.close()
                    context.close()
                } catch (e: Exception) {
                    runOnUiThread {
                        responseText.text = "Ошибка: ${e.message}"
                    }
                }
            }
        }
    }
    override fun onPause() {
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
    }
}
