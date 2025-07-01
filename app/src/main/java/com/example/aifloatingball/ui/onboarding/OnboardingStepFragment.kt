package com.example.aifloatingball.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.aifloatingball.R
import com.example.aifloatingball.databinding.FragmentOnboardingStepBinding

class OnboardingStepFragment : Fragment() {

    private var _binding: FragmentOnboardingStepBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingStepBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            binding.titleText.text = it.getString(ARG_TITLE)
            binding.animationView.setAnimation(it.getString(ARG_ANIMATION_FILE))
            binding.animationView.playAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_ANIMATION_FILE = "animation_file"

        @JvmStatic
        fun newInstance(title: String, animationFile: String) =
            OnboardingStepFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_ANIMATION_FILE, animationFile)
                }
            }
    }
} 