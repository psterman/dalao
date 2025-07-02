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
        arguments?.let { args ->
            val title = args.getString(ARG_TITLE)
            val animationFile = args.getString(ARG_ANIMATION_FILE)
            val isPermissionStep = args.getBoolean(ARG_IS_PERMISSION_STEP, false)
            
            // 设置标题和动画
            binding.titleText.text = title
            binding.animationView.setAnimation(animationFile)
            binding.animationView.playAnimation()
            
            // 根据步骤设置描述和功能列表
            when {
                isPermissionStep -> {
                    binding.descriptionText.text = "为了提供最佳体验，应用需要您授予一些必要的权限。这些权限仅用于核心功能，我们承诺保护您的隐私安全。"
                    binding.featuresList.text = "• 🔄 悬浮窗权限：显示智能悬浮球\n• 🎤 录音权限：语音识别和对话\n• 📢 通知权限：重要消息提醒\n• 🔒 所有权限均可稍后设置\n• 🛡️ 隐私安全，数据不外泄"
                }
                title == "🎯 第一步：启动智能悬浮球" -> {
                    binding.descriptionText.text = "智能悬浮球会在您的屏幕上优雅地漂浮，随时准备为您提供AI助手服务。支持多种显示模式，包括悬浮球、灵动岛和简易模式。"
                    binding.featuresList.text = "• 🎨 三种显示模式自由切换\n• 📍 智能位置记忆功能\n• 🎯 一键快速启动搜索\n• ⚡ 轻量化运行，不占资源\n• 🔒 隐私保护，本地处理"
                }
                title == "👤 第二步：设定您的专属身份" -> {
                    binding.descriptionText.text = "通过设定您的身份角色，AI助手能够更好地理解您的需求，提供个性化的专业建议和帮助。"
                    binding.featuresList.text = "• 🎓 学生：学习辅导和研究支持\n• 💼 职场人士：工作效率优化\n• 🎨 创作者：创意灵感和内容建议\n• 🔬 研究者：学术资料整理分析\n• ⚙️ 开发者：代码优化和技术支持"
                }
                title == "🤖 第三步：选择AI智能助手" -> {
                    binding.descriptionText.text = "从多种专业AI助手中选择最适合您的那一个。每个助手都有独特的专长领域，能够为您提供精准的帮助。"
                    binding.featuresList.text = "• 📝 通用助手：日常问答和任务处理\n• 📚 学术助手：论文写作和研究支持\n• 💻 编程助手：代码生成和调试帮助\n• 🎯 写作助手：文案创作和润色\n• 🔍 搜索助手：信息检索和整理"
                }
                title == "💬 第四步：开始智能对话" -> {
                    binding.descriptionText.text = "一切准备就绪！现在您可以通过多种方式与AI助手进行对话，享受智能化的交互体验。"
                    binding.featuresList.text = "• 🎤 语音输入，自然交流\n• ⌨️ 文字输入，精确表达\n• 🔍 智能搜索，即时响应\n• 💾 对话历史，随时回顾\n• 🌐 多引擎支持，结果全面"
                }
                else -> {
                    binding.descriptionText.text = "欢迎使用智能AI助手！"
                    binding.featuresList.text = "• 开始您的智能体验之旅"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_ANIMATION_FILE = "animation_file"
        private const val ARG_IS_PERMISSION_STEP = "is_permission_step"

        @JvmStatic
        fun newInstance(title: String, animationFile: String, isPermissionStep: Boolean = false) =
            OnboardingStepFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_ANIMATION_FILE, animationFile)
                    putBoolean(ARG_IS_PERMISSION_STEP, isPermissionStep)
                }
            }
    }
} 