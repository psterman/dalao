package com.example.aifloatingball.preference

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager

class AlphaSliderDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "AlphaSliderDialog"
        fun newInstance(): AlphaSliderDialogFragment {
            return AlphaSliderDialogFragment()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val settingsManager = SettingsManager.getInstance(requireContext())
        val inflater = requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.dialog_alpha_slider, null)

        val seekBar = view.findViewById<SeekBar>(R.id.alpha_seekbar)
        val valueText = view.findViewById<TextView>(R.id.alpha_value_text)

        val currentAlpha = settingsManager.getBallAlpha()
        seekBar.progress = currentAlpha
        valueText.text = currentAlpha.toString()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueText.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        return AlertDialog.Builder(requireContext())
            .setTitle("设置透明度")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val newAlpha = seekBar.progress
                settingsManager.setBallAlpha(newAlpha)
                // Send broadcast to update the floating ball immediately
                val intent = android.content.Intent("com.example.aifloatingball.ACTION_UPDATE_ALPHA")
                intent.putExtra("alpha", newAlpha)
                requireContext().sendBroadcast(intent)
            }
            .setNegativeButton("取消", null)
            .create()
    }
} 