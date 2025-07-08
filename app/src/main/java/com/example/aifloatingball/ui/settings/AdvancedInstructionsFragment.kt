package com.example.aifloatingball.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptProfile

class AdvancedInstructionsFragment : Fragment() {

    private lateinit var editCustomInstructions: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_advanced_instructions, container, false)
        editCustomInstructions = view.findViewById(R.id.edit_custom_instructions)
        return view
    }

    fun updateUI(profile: PromptProfile) {
        editCustomInstructions.setText(profile.customInstructions)
    }

    fun getCustomInstructions(): String = editCustomInstructions.text.toString()

    companion object {
        fun newInstance(): AdvancedInstructionsFragment {
            return AdvancedInstructionsFragment()
        }
    }
}