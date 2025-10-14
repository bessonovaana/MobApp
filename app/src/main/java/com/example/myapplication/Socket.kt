package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import kotlin.concurrent.thread

class Socket : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_socket)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val sendButton = findViewById<Button>(R.id.st)
        val responseText = findViewById<TextView>(R.id.tvSocket)
        val button_b: Button=findViewById(R.id.bk)

        button_b.setOnClickListener {
            val intent = Intent(this, MenuActivity::class.java)
            startActivity(intent)
        }
        sendButton.setOnClickListener {
            thread {
                try {
                    val context = ZMQ.context(1)
                    val socket = context.socket(ZMQ.REQ)
                    socket.connect("tcp://192.168.1.125:5000")

                    val message = "Hello!"
                    socket.send(message.toByteArray(ZMQ.CHARSET), 0)
                    val reply = socket.recv(0)

                    runOnUiThread {
                        responseText.text = "Ответ сервера: ${String(reply, ZMQ.CHARSET)}"
                    }

                    socket.close()
                    context.close()
                }
                catch (e: Exception) {
                    runOnUiThread {
                        responseText.text = "Ошибка: ${e.message}"
                    }
                }
            }
        }
    }
}