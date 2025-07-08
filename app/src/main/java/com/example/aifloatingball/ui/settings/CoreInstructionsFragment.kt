package com.example.aifloatingball.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptProfile

class CoreInstructionsFragment : Fragment() {

    private lateinit var editPersona: EditText
    private lateinit var editTone: EditText
    private lateinit var editOutputFormat: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_core_instructions, container, false)
        editPersona = view.findViewById(R.id.edit_persona)
        editTone = view.findViewById(R.id.edit_tone)
        editOutputFormat = view.findViewById(R.id.edit_output_format)
        return view
    }

    fun updateUI(profile: PromptProfile) {
        editPersona.setText(profile.persona)
        editTone.setText(profile.tone)
        editOutputFormat.setText(profile.outputFormat)
    }

    fun getPersona(): String = editPersona.text.toString()
    fun getTone(): String = editTone.text.toString()
    fun getOutputFormat(): String = editOutputFormat.text.toString()

    companion object {
        fun newInstance(): CoreInstructionsFragment {
            return CoreInstructionsFragment()
        }
    }
}