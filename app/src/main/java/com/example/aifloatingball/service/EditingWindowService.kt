package com.example.aifloatingball.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.aifloatingball.R

class EditingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var editingView: FrameLayout? = null
    private lateinit var editText: EditText

    companion object {
        const val EXTRA_INITIAL_TEXT = "initial_text"
        const val ACTION_UPDATE_TEXT = "com.example.aifloatingball.ACTION_UPDATE_TEXT"
        const val EXTRA_UPDATED_TEXT = "updated_text"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialText = intent?.getStringExtra(EXTRA_INITIAL_TEXT) ?: ""
        showEditingWindow(initialText)
        return START_NOT_STICKY
    }

    private fun showEditingWindow(initialText: String) {
        if (editingView != null) return

        val inflater = LayoutInflater.from(this)
        editingView = inflater.inflate(R.layout.layout_editing_window, null) as FrameLayout
        editText = editingView!!.findViewById(R.id.editing_input)
        val doneButton = editingView!!.findViewById<Button>(R.id.done_button)

        editText.setText(initialText)
        editText.setSelection(initialText.length)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
             params.blurBehindRadius = 25
        } else {
            editingView?.setBackgroundColor(Color.parseColor("#CC000000"))
        }

        doneButton.setOnClickListener {
            val updatedText = editText.text.toString()
            val updateIntent = Intent(ACTION_UPDATE_TEXT).apply {
                putExtra(EXTRA_UPDATED_TEXT, updatedText)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(updateIntent)
            stopSelf()
        }

        windowManager.addView(editingView, params)

        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroy() {
        super.onDestroy()
        editingView?.let { windowManager.removeView(it) }
        editingView = null
    }
} 