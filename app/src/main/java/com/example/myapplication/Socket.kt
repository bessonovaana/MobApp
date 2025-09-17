package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.zeromq.SocketType
import org.zeromq.ZContext
import zmq.ZMQ

class Socket : AppCompatActivity() {
    private var log_tag: String ="MY_LOG_TAG"
    private lateinit var tsocket: TextView
    private lateinit var handler: Handler

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
        tsocket=findViewById(R.id.tvSocket)
        handler= Handler(Looper.getMainLooper())
    }
    fun startServer(){
        val context = ZContext(1)
        val socket=ZContext().createSocket(SocketType.REP)
        socket.bind("tcp://*:3333")

        while (true){
            val requestBytes = socket.recv(0)
            val request = String(requestBytes, ZMQ.CHARSET)
            println("[SERVER] Received request: [$request]")

            // Здесь имитируется какая-то сложная работа
            handler.postDelayed({
                tsocket.text = "Received MSG from Client = "
            }, 0) // Это безопасная работа с основным потоком UI Thread
            Thread.sleep(1000)
            // Закончили очень сложную работу

            // Подготавливаем и отправляем ответ Клиенту
            val response = "Hello from Android ZMQ Server!"
            socket.send(response.toByteArray(ZMQ.CHARSET), 0)
            println("[SERVER] Sent reply: [$response]")
        }
        // Безопасно закрываем контекст и сокет.
        socket.close();
        context.close();
    }

    fun startClient() {
        val context = ZContext(1)
        val socket = ZContext().createSocket(SocketType.REQ)
        socket.connect("tcp://localhost:3333") // Запрос на соединение с сервером
        val request = "Hello from Android client!"
        for(i in 0..10){
            socket.send(request.toByteArray(ZMQ.CHARSET), 0)
            Log.d(log_tag, "[CLIENT] SendT: $request")

            val reply = socket.recv(0)
            Log.d(log_tag, "[CLIENT] Received: " + String(reply, ZMQ.CHARSET))
        }
        socket.close()
        context.close()
    }
    override fun onResume() {
        super.onResume()
        val runnableServer = Runnable{startServer()}
        val threadServer = Thread(runnableServer)
        threadServer.start()

        Thread.sleep(1000)

        val runnableClient = Runnable{startClient()}
        val threadClient = Thread(runnableClient)
        threadClient.start()
    }
}
