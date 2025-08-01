package com.example.aifloatingball.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.aifloatingball.R
import com.example.aifloatingball.model.LocalDynamic
// import de.hdodenhof.circleimageview.CircleImageView // 不再使用

/**
 * B站动态适配器
 */
class BilibiliDynamicAdapter(
    private val context: Context,
    private var dynamics: List<LocalDynamic> = emptyList()
) : RecyclerView.Adapter<BilibiliDynamicAdapter.DynamicViewHolder>() {
    
    companion object {
        private const val TAG = "BilibiliDynamicAdapter"
    }
    
    interface OnDynamicClickListener {
        fun onDynamicClick(dynamic: LocalDynamic)
        fun onAuthorClick(dynamic: LocalDynamic)
        fun onMoreClick(dynamic: LocalDynamic, view: View)
    }
    
    private var onDynamicClickListener: OnDynamicClickListener? = null
    
    fun setOnDynamicClickListener(listener: OnDynamicClickListener) {
        this.onDynamicClickListener = listener
    }
    
    fun updateDynamics(newDynamics: List<LocalDynamic>) {
        this.dynamics = newDynamics
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DynamicViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_bilibili_dynamic, parent, false)
        return DynamicViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DynamicViewHolder, position: Int) {
        holder.bind(dynamics[position])
    }
    
    override fun getItemCount(): Int = dynamics.size
    
    inner class DynamicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.iv_avatar)
        private val tvAuthorName: TextView = itemView.findViewById(R.id.tv_author_name)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val layoutTypeTag: LinearLayout = itemView.findViewById(R.id.layout_type_tag)
        private val ivTypeIcon: ImageView = itemView.findViewById(R.id.iv_type_icon)
        private val tvTypeText: TextView = itemView.findViewById(R.id.tv_type_text)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btn_more)
        
        fun bind(dynamic: LocalDynamic) {
            // 设置用户头像
            Glide.with(context)
                .load(dynamic.authorAvatar)
                .transform(CircleCrop())
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(ivAvatar)
            
            // 设置用户名
            tvAuthorName.text = dynamic.authorName
            
            // 设置时间
            val timeText = DateUtils.getRelativeTimeSpanString(
                dynamic.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            tvTime.text = timeText
            
            // 设置内容
            tvContent.text = dynamic.content
            
            // 设置类型标签
            setupTypeTag(dynamic)
            
            // 设置点击事件
            itemView.setOnClickListener {
                if (dynamic.jumpUrl.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dynamic.jumpUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 如果无法打开链接，则触发点击回调
                        onDynamicClickListener?.onDynamicClick(dynamic)
                    }
                } else {
                    onDynamicClickListener?.onDynamicClick(dynamic)
                }
            }
            
            // 头像点击事件
            ivAvatar.setOnClickListener {
                onDynamicClickListener?.onAuthorClick(dynamic)
            }
            
            // 更多按钮点击事件
            btnMore.setOnClickListener {
                onDynamicClickListener?.onMoreClick(dynamic, it)
            }
        }
        
        private fun setupTypeTag(dynamic: LocalDynamic) {
            when (dynamic.type) {
                "video" -> {
                    layoutTypeTag.visibility = View.VISIBLE
                    ivTypeIcon.setImageResource(R.drawable.ic_video)
                    tvTypeText.text = "视频"
                }
                "article" -> {
                    layoutTypeTag.visibility = View.VISIBLE
                    ivTypeIcon.setImageResource(R.drawable.ic_article)
                    tvTypeText.text = "文章"
                }
                "opus" -> {
                    layoutTypeTag.visibility = View.VISIBLE
                    ivTypeIcon.setImageResource(R.drawable.ic_image)
                    tvTypeText.text = "图文"
                }
                "repost" -> {
                    layoutTypeTag.visibility = View.VISIBLE
                    ivTypeIcon.setImageResource(R.drawable.ic_share)
                    tvTypeText.text = "转发"
                }
                else -> {
                    layoutTypeTag.visibility = View.GONE
                }
            }
        }
    }
}

/**
 * B站动态卡片视图
 */
class BilibiliDynamicCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "BilibiliDynamicCardView"
        private const val MAX_DISPLAY_COUNT = 5 // 最多显示5条动态
    }
    
    private lateinit var tvUpdateTime: TextView
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnManage: ImageButton
    private lateinit var rvDynamics: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var layoutLoading: LinearLayout
    private lateinit var btnAddSubscription: com.google.android.material.button.MaterialButton
    private lateinit var tvViewMore: TextView
    
    private lateinit var adapter: BilibiliDynamicAdapter
    private var onCardActionListener: OnCardActionListener? = null
    
    interface OnCardActionListener {
        fun onRefreshClick()
        fun onManageClick()
        fun onAddSubscriptionClick()
        fun onViewMoreClick()
        fun onDynamicClick(dynamic: LocalDynamic)
        fun onAuthorClick(dynamic: LocalDynamic)
    }
    
    init {
        initView()
    }
    
    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.card_bilibili_dynamics, this, true)
        
        tvUpdateTime = findViewById(R.id.tv_update_time)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnManage = findViewById(R.id.btn_manage)
        rvDynamics = findViewById(R.id.rv_dynamics)
        layoutEmpty = findViewById(R.id.layout_empty)
        layoutLoading = findViewById(R.id.layout_loading)
        btnAddSubscription = findViewById(R.id.btn_add_subscription)
        tvViewMore = findViewById(R.id.tv_view_more)
        
        setupRecyclerView()
        setupClickListeners()
    }
    
    private fun setupRecyclerView() {
        adapter = BilibiliDynamicAdapter(context)
        rvDynamics.adapter = adapter
        rvDynamics.layoutManager = LinearLayoutManager(context)
        
        adapter.setOnDynamicClickListener(object : BilibiliDynamicAdapter.OnDynamicClickListener {
            override fun onDynamicClick(dynamic: LocalDynamic) {
                onCardActionListener?.onDynamicClick(dynamic)
            }
            
            override fun onAuthorClick(dynamic: LocalDynamic) {
                onCardActionListener?.onAuthorClick(dynamic)
            }
            
            override fun onMoreClick(dynamic: LocalDynamic, view: View) {
                // 可以在这里显示更多操作菜单
            }
        })
    }
    
    private fun setupClickListeners() {
        btnRefresh.setOnClickListener {
            onCardActionListener?.onRefreshClick()
        }
        
        btnManage.setOnClickListener {
            onCardActionListener?.onManageClick()
        }
        
        btnAddSubscription.setOnClickListener {
            onCardActionListener?.onAddSubscriptionClick()
        }
        
        tvViewMore.setOnClickListener {
            onCardActionListener?.onViewMoreClick()
        }
    }
    
    fun setOnCardActionListener(listener: OnCardActionListener) {
        this.onCardActionListener = listener
    }
    
    fun updateDynamics(dynamics: List<LocalDynamic>) {
        if (dynamics.isEmpty()) {
            showEmptyState()
        } else {
            showDynamics(dynamics)
        }
    }
    
    private fun showDynamics(dynamics: List<LocalDynamic>) {
        layoutEmpty.visibility = View.GONE
        layoutLoading.visibility = View.GONE
        rvDynamics.visibility = View.VISIBLE
        
        // 只显示前几条动态
        val displayDynamics = dynamics.take(MAX_DISPLAY_COUNT)
        adapter.updateDynamics(displayDynamics)
        
        // 显示查看更多按钮
        if (dynamics.size > MAX_DISPLAY_COUNT) {
            tvViewMore.visibility = View.VISIBLE
            tvViewMore.text = "查看全部 ${dynamics.size} 条动态 >"
        } else {
            tvViewMore.visibility = View.GONE
        }
        
        // 更新时间
        updateTimeText()
    }
    
    private fun showEmptyState() {
        rvDynamics.visibility = View.GONE
        layoutLoading.visibility = View.GONE
        tvViewMore.visibility = View.GONE
        layoutEmpty.visibility = View.VISIBLE
    }
    
    fun showLoading() {
        rvDynamics.visibility = View.GONE
        layoutEmpty.visibility = View.GONE
        tvViewMore.visibility = View.GONE
        layoutLoading.visibility = View.VISIBLE
    }
    
    private fun updateTimeText() {
        val currentTime = System.currentTimeMillis()
        val timeText = DateUtils.getRelativeTimeSpanString(
            currentTime,
            currentTime,
            DateUtils.MINUTE_IN_MILLIS
        )
        tvUpdateTime.text = "刚刚更新"
    }
    
    fun setUpdateTime(time: Long) {
        val timeText = DateUtils.getRelativeTimeSpanString(
            time,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        tvUpdateTime.text = "更新于 $timeText"
    }
}
