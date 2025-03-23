package com.example.myapplication


import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity




class MainActivity : AppCompatActivity() {
    private var canAddOperation=false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val result: TextView = findViewById(R.id.result) as TextView
        val working: TextView = findViewById(R.id.working) as TextView


        val button1: Button =findViewById(R.id.b_1)
        val button2: Button =findViewById(R.id.b_2)
        val button3: Button =findViewById(R.id.b_3)
        val button4: Button =findViewById(R.id.b_4)
        val button5: Button =findViewById(R.id.b_5)
        val button6: Button =findViewById(R.id.b_6)
        val button7: Button =findViewById(R.id.b_7)
        val button8: Button =findViewById(R.id.b_8)
        val button9: Button =findViewById(R.id.b_9)
        val button0: Button =findViewById(R.id.b_0)

        val buttonAdd: Button =findViewById(R.id.b_p)
        val buttonSubtract: Button =findViewById(R.id.b_m)
        val buttonEquals: Button =findViewById(R.id.b_eq)
        val buttonClear: Button =findViewById(R.id.b_AC)
        val buttonMultiply: Button = findViewById(R.id.b_um)
        val buttonDevid: Button =findViewById(R.id.b_del)
        val buttonDot: Button =findViewById(R.id.b_d)

        button1.setOnClickListener {
            working.append("1")
            canAddOperation=true
        }
        button2.setOnClickListener {
            working.append("2")
            canAddOperation=true
        }
        button3.setOnClickListener {
            working.append("3")
            canAddOperation=true
        }
        button4.setOnClickListener {
            working.append("4")
            canAddOperation=true
        }
        button5.setOnClickListener {
            working.append("5")
            canAddOperation=true
        }
        button6.setOnClickListener {
            working.append("6")
            canAddOperation=true
        }
        button7.setOnClickListener {
            working.append("7")
            canAddOperation=true
        }
        button8.setOnClickListener {
            working.append("8")
            canAddOperation=true
        }
        button9.setOnClickListener {
            working.append("9")
            canAddOperation=true
        }
        button0.setOnClickListener {
            working.append("0")
            canAddOperation=true
        }


        buttonAdd.setOnClickListener {
            working.append("+")
            canAddOperation=false
        }
        buttonDevid.setOnClickListener {
            working.append("/")
            canAddOperation=false
        }
        buttonSubtract.setOnClickListener {
            working.append("-")
            canAddOperation=false
        }
        buttonMultiply.setOnClickListener {
            working.append("*")
            canAddOperation=false
        }
        buttonDot.setOnClickListener {
                working.append(".")
                canAddOperation=false

        }

        buttonClear.setOnClickListener {
            working.text=""
            result.text=""
        }

        buttonEquals.setOnClickListener { result.text = calculateResult() }


    }
    private fun calculateResult(): String {
        val working = findViewById<TextView>(R.id.working).text.toString()
        if (working.isEmpty()) return ""
        val tokens = mutableListOf<String>()
        var currentToken = ""
        for (char in working) {
            if (char.isDigit() || char == '.') {
                currentToken += char
            } else {
                if (currentToken.isNotEmpty()) {
                    tokens.add(currentToken)
                    currentToken = ""
                }
                tokens.add(char.toString())
            }
        }
        if (currentToken.isNotEmpty()) {
            tokens.add(currentToken)
        }

        if (tokens.isEmpty()) return ""
        var result = tokens[0].toDouble()
        var i = 1
        while (i < tokens.size) {
            val operator = tokens[i]
            val nextNumber = tokens[i + 1].toDouble()
            when (operator) {
                "+" -> result += nextNumber
                "-" -> result -= nextNumber
                "*" -> result *= nextNumber
                "/" -> result /= nextNumber
            }
            i += 2
        }

        return if (result % 1 == 0.0) {
            result.toInt().toString()
        } else {
            result.toString()
        }
    }

}