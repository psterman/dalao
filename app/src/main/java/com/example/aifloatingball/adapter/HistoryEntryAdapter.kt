package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.HistoryEntry
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.*

/**
 * 鍘嗗彶璁板綍閫傞厤鍣? */
class HistoryEntryAdapter(
    private var entries: List<HistoryEntry> = emptyList(),
    private val onItemClick: (HistoryEntry) -> Unit = {},
    private val onMoreClick: (HistoryEntry) -> Unit = {},
    private val onSwipeFavorite: (HistoryEntry) -> Unit = {},
    private val onSwipeDelete: (HistoryEntry) -> Unit = {},
    private val isLeftHandedMode: Boolean = false
) : RecyclerView.Adapter<HistoryEntryAdapter.ViewHolder>() {
    // 鍐呴儴鍙彉鍒楄〃锛屾敮鎸佸垹闄ゅ姩鐢荤瓑鍗虫椂鏇存柊
    private var items: MutableList<HistoryEntry> = entries.toMutableList()
    
    // 璺熻釜褰撳墠婊戝姩鐨刅iewHolder锛岀‘淇濇瘡娆″彧婊戝姩涓€涓?    private var currentSwipedHolder: ViewHolder? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardContent: MaterialCardView = itemView.findViewById(R.id.card_content)
        val swipeBackgroundRight: LinearLayout = itemView.findViewById(R.id.swipe_background_right)
        val swipeBackgroundLeft: LinearLayout = itemView.findViewById(R.id.swipe_background_left)
        val ivSiteIcon: ImageView = itemView.findViewById(R.id.iv_site_icon)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        val tvUrl: TextView = itemView.findViewById(R.id.tv_url)
        val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        val btnMore: ImageButton = itemView.findViewById(R.id.btn_more)
        val btnSwipeFavorite: ImageButton = itemView.findViewById(R.id.btn_swipe_favorite)
        val btnSwipeDelete: ImageButton = itemView.findViewById(R.id.btn_swipe_delete)
        val btnSwipeFavoriteLeft: ImageButton = itemView.findViewById(R.id.btn_swipe_favorite_left)
        val btnSwipeDeleteLeft: ImageButton = itemView.findViewById(R.id.btn_swipe_delete_left)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        
        holder.tvTitle.text = entry.title
        holder.tvUrl.text = entry.url
        holder.tvTime.text = entry.getFormattedTime()
        
        // 璁剧疆缃戠珯鍥炬爣锛堝姞杞絝avicon锛?        holder.ivSiteIcon.setImageResource(R.drawable.ic_web) // 鍏堣缃粯璁ゅ浘鏍?        
try {
            // 浣跨敤FaviconLoader鍔犺浇缃戠珯鍥炬爣
            com.example.aifloatingball.utils.FaviconLoader.loadIcon(
                holder.ivSiteIcon,
                entry.url,
                R.drawable.ic_web
            )
        } catch (e: Exception) {
            android.util.Log.e("HistoryEntryAdapter", "鍔犺浇缃戠珯鍥炬爣澶辫触: ${entry.url}", e)
            holder.ivSiteIcon.setImageResource(R.drawable.ic_web)
        }
        
        // 閲嶇疆婊戝姩浣嶇疆
        holder.cardContent.translationX = 0f
        holder.swipeBackgroundRight.visibility = View.GONE
        holder.swipeBackgroundLeft.visibility = View.GONE
        
        // 鑳屾櫙鍖哄煙锛氫娇鐢ㄨЕ鎽镐簨浠跺鐞嗭紝鍦ㄧ偣鍑绘椂妫€鏌ユ槸鍚︾偣鍑讳簡鎸夐挳
        // 鍏抽敭锛氭寜閽繀椤诲湪鑳屾櫙涔嬩笂锛坺-order锛夛紝浣嗘垜浠渶瑕佸湪鑳屾櫙灞傛娴嬬偣鍑讳綅缃?        holder.swipeBackgroundRight.isClickable = true
        holder.swipeBackgroundRight.isFocusable = true
        holder.swipeBackgroundLeft.isClickable = true
        holder.swipeBackgroundLeft.isFocusable = true
        
        // 鐐瑰嚮浜嬩欢
        holder.cardContent.setOnClickListener {
            // 濡傛灉鍗＄墖宸茬粡婊戝姩锛屼笉鍝嶅簲鐐瑰嚮
            if (abs(holder.cardContent.translationX) < 10f) {
                onItemClick(entry)
            }
        }
        
        holder.btnMore.setOnClickListener {
            onMoreClick(entry)
        }
        
        // 为按钮添加触摸监听，防止 RecyclerView/ItemTouchHelper 拦截点击
        val disallowInterceptTouch: View.OnTouchListener = View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    var current: android.view.ViewParent? = v.parent
                    while (current != null) {
                        current.requestDisallowInterceptTouchEvent(true)
                        current = (current as? View)?.parent
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    var current: android.view.ViewParent? = v.parent
                    while (current != null) {
                        current.requestDisallowInterceptTouchEvent(true)
                        current = (current as? View)?.parent
                    }
                    false
                }
                MotionEvent.ACTION_CANCEL -> {
                    var current: android.view.ViewParent? = v.parent
                    while (current != null) {
                        current.requestDisallowInterceptTouchEvent(false)
                        current = (current as? View)?.parent
                    }
                    false
                }
                else -> false
            }
        }
        holder.btnSwipeFavorite.setOnTouchListener(disallowInterceptTouch)
        holder.btnSwipeDelete.setOnTouchListener(disallowInterceptTouch)
        holder.btnSwipeFavoriteLeft.setOnTouchListener(disallowInterceptTouch)
        holder.btnSwipeDeleteLeft.setOnTouchListener(disallowInterceptTouch)
        
        // 鑳屾櫙鍖哄煙锛氬畬鍏ㄧЩ闄よЕ鎽哥洃鍚櫒锛屼笉鎷︽埅浠讳綍浜嬩欢
        // 杩欐牱鎸夐挳鍙互姝ｅ父鎺ユ敹鐐瑰嚮浜嬩欢锛岃儗鏅尯鍩熶篃涓嶄細瑙﹀彂鑷姩杩樺師null)
        holder.swipeBackgroundRight.setOnTouchListener(null)
        // 杩樺師浣嶇疆鍙兘閫氳繃锛?. 婊戝姩鎿嶄綔锛坔andleSwipeEnd锛?2. 鎸夐挳鐐瑰嚮鍚庤嚜鍔ㄨ繕鍘?        holder.swipeBackgroundRight.setOnTouchListener(null)
        holder.swipeBackgroundLeft.setOnTouchListener(null)

        // 鍙充晶婊戝姩鎸夐挳鐐瑰嚮浜嬩欢锛堟櫘閫氭ā寮忓乏婊戞樉绀猴級
        holder.btnSwipeFavorite.setOnClickListener { view ->
            android.util.Log.d("HistoryEntryAdapter", "鐐瑰嚮鏀惰棌鎸夐挳: ${entry.title}, URL: ${entry.url}")
            try {
                onSwipeFavorite(entry)
            } catch (e: Exception) {
                android.util.Log.e("HistoryEntryAdapter", "鏀惰棌鎸夐挳鐐瑰嚮寮傚父", e)
            }
            resetSwipeImmediate(holder)
        }
        
        holder.btnSwipeDelete.setOnClickListener { view ->
            android.util.Log.d("HistoryEntryAdapter", "鐐瑰嚮鍒犻櫎鎸夐挳: ${entry.title}, URL: ${entry.url}")
            try {
                onSwipeDelete(entry)
            } catch (e: Exception) {
                android.util.Log.e("HistoryEntryAdapter", "鍒犻櫎鎸夐挳鐐瑰嚮寮傚父", e)
            }
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                removeAt(pos)
            } else {
                resetSwipeImmediate(holder)
            }
        }
        
        // 宸︿晶婊戝姩鎸夐挳鐐瑰嚮浜嬩欢锛堝乏鎾囧瓙妯″紡鍙虫粦鏄剧ず锛?        holder.btnSwipeFavoriteLeft.setOnClickListener { view ->
            android.util.Log.d("HistoryEntryAdapter", "鐐瑰嚮鏀惰棌鎸夐挳(宸?: ${entry.title}, URL: ${entry.url}")
            try {
                onSwipeFavorite(entry)
            } catch (e: Exception) {
                android.util.Log.e("HistoryEntryAdapter", "鏀惰棌鎸夐挳鐐瑰嚮寮傚父", e)
            }
            val posLeftDel = holder.bindingAdapterPosition
            if (posLeftDel != RecyclerView.NO_POSITION) {
                removeAt(posLeftDel)
            } else {
                resetSwipeImmediate(holder)
            }
        }
        
        holder.btnSwipeDeleteLeft.setOnClickListener { view ->
            android.util.Log.d("HistoryEntryAdapter", "鐐瑰嚮鍒犻櫎鎸夐挳(宸?: ${entry.title}, URL: ${entry.url}")
            try {
                onSwipeDelete(entry)
            } catch (e: Exception) {
                android.util.Log.e("HistoryEntryAdapter", "鍒犻櫎鎸夐挳鐐瑰嚮寮傚父", e)
            }
            resetSwipeImmediate(holder)
        }
        
        // 纭繚鎸夐挳鍙互鎺ユ敹鐐瑰嚮浜嬩欢锛屼笉琚埗瑙嗗浘鎷︽埅
        holder.btnSwipeFavorite.isClickable = true
        holder.btnSwipeFavorite.isFocusable = true
        holder.btnSwipeFavorite.isEnabled = true
        holder.btnSwipeDelete.isClickable = true
        holder.btnSwipeDelete.isFocusable = true
        holder.btnSwipeDelete.isEnabled = true
        holder.btnSwipeFavoriteLeft.isClickable = true
        holder.btnSwipeFavoriteLeft.isFocusable = true
        holder.btnSwipeFavoriteLeft.isEnabled = true
        holder.btnSwipeDeleteLeft.isClickable = true
        holder.btnSwipeDeleteLeft.isFocusable = true
        holder.btnSwipeDeleteLeft.isEnabled = true
        
        // 娉ㄦ剰锛氫笉瑕佸湪杩欓噷璁剧疆 onClickListener 鍜?onTouchListener
        // 鍥犱负鎴戜滑宸茬粡鍦ㄤ笂闈㈢殑浠ｇ爜涓缃簡瑙︽懜鐩戝惉鍣ㄦ潵闃绘浜嬩欢鎷︽埅
        // 如果需要点击背景区域关闭滑动，可以在触摸监听器中处理
    }
    
    /**
     * 澶勭悊婊戝姩鎿嶄綔锛堝彧鏀寔宸︽粦锛屽乏鎾囧瓙妯″紡涓嬪彸婊戯級
     * ItemTouchHelper涓細宸︽粦鏃禿x < 0锛屽彸婊戞椂dx > 0
     * 鍗＄墖绉诲姩閫昏緫锛氬乏婊戞椂鍗＄墖鍚戝彸绉诲姩锛坱ranslationX涓烘锛夛紝鏄剧ず宸︿晶鎸夐挳
     */
    fun handleSwipe(holder: ViewHolder, dx: Float, isActive: Boolean = false) {
        // 濡傛灉鏈夊叾浠栨鍦ㄦ粦鍔ㄧ殑璁板綍锛屽厛杩樺師瀹?        if (isActive && currentSwipedHolder != null && currentSwipedHolder != holder) {
            currentSwipedHolder?.let { oldHolder ->
                resetSwipeImmediate(oldHolder)
            }
        }
        
        // 濡傛灉寮€濮嬫柊鐨勬粦鍔紝璁剧疆涓哄綋鍓嶆粦鍔ㄧ殑璁板綍
        if (isActive && abs(dx) > 10f) {
            currentSwipedHolder = holder
        }
        
        val cardWidth = holder.cardContent.width.toFloat()
        if (cardWidth == 0f) return
        
        // 鏈€澶ф粦鍔ㄨ窛绂伙紙鎸夐挳鍖哄煙瀹藉害锛岀害120dp锛?        val maxSwipeDistance = 120f * holder.itemView.context.resources.displayMetrics.density
        
        // 鍒ゆ柇鏄惁涓烘湁鏁堢殑婊戝姩鏂瑰悜锛堟櫘閫氭ā寮忓彧鍏佽宸︽粦锛屽乏鎾囧瓙妯″紡鍙厑璁稿彸婊戯級
        val isValidSwipe = if (isLeftHandedMode) {
            dx > 0 // 宸︽拠瀛愭ā寮忥細鍙厑璁稿彸婊?        } else {
            dx < 0 // 鏅€氭ā寮忥細鍙厑璁稿乏婊?        }
        
        if (!isValidSwipe && abs(dx) > 10f) {
            // 鏃犳晥鏂瑰悜锛屼笉澶勭悊
            return
        }
        
        // 闄愬埗婊戝姩璺濈锛岄槻姝㈡粦鍑哄睆骞?        val limitedDx = if (isLeftHandedMode) {
            dx.coerceIn(0f, maxSwipeDistance) // 宸︽拠瀛愭ā寮忥細鍙厑璁稿悜鍙虫粦鍔紙姝ｆ暟锛?        } else {
            dx.coerceIn(-maxSwipeDistance, 0f) // 鏅€氭ā寮忥細鍙厑璁稿悜宸︽粦鍔紙璐熸暟锛?        }
        
        // 绉诲姩鍗＄墖锛氬乏婊戞椂鍗＄墖鍚戝乏绉诲姩鏄剧ず鍙充晶鎸夐挳锛屽彸婊戞椂鍗＄墖鍚戝彸绉诲姩鏄剧ず宸︿晶鎸夐挳
        // ItemTouchHelper涓細宸︽粦鏃禿x < 0锛堣礋鏁帮級锛屽彸婊戞椂dx > 0锛堟鏁帮級
        // translationX鏂瑰悜涓巇x鏂瑰悜涓€鑷达細宸︽粦鏃秚ranslationX涓鸿礋鏁帮紙鍗＄墖鍚戝乏锛夛紝鍙虫粦鏃秚ranslationX涓烘鏁帮紙鍗＄墖鍚戝彸锛?        val swipeDistance = abs(limitedDx)
        val showButtonThreshold = maxSwipeDistance * 0.1f // 10%鐨勬粦鍔ㄨ窛绂诲嵆鍙樉绀烘寜閽?        
        // 鍑忓皯涓嶅繀瑕佺殑visibility鏇存柊锛岄伩鍏嶉噸缁樺崱椤?        if (isLeftHandedMode) {
            // 宸︽拠瀛愭ā寮忥細鍙虫粦锛坉x > 0锛夛紝鍗＄墖鍚戝彸绉诲姩锛坱ranslationX涓烘鏁帮級锛屾樉绀哄乏渚ф寜閽?            holder.cardContent.translationX = abs(limitedDx)
            val shouldShowButtons = swipeDistance > showButtonThreshold
            val newVisibility = if (shouldShowButtons) View.VISIBLE else View.GONE
            if (holder.swipeBackgroundLeft.visibility != newVisibility) {
                holder.swipeBackgroundLeft.visibility = newVisibility
            }
            holder.swipeBackgroundRight.visibility = View.GONE
        } else {
            // 鏅€氭ā寮忥細宸︽粦锛坉x < 0锛夛紝鍗＄墖鍚戝乏绉诲姩锛坱ranslationX涓鸿礋鏁帮級锛屾樉绀哄彸渚ф寜閽?            holder.cardContent.translationX = limitedDx  // dx宸茬粡鏄礋鏁帮紝鐩存帴浣跨敤
            val shouldShowButtons = swipeDistance > showButtonThreshold
            val newVisibility = if (shouldShowButtons) View.VISIBLE else View.GONE
            if (holder.swipeBackgroundRight.visibility != newVisibility) {
                holder.swipeBackgroundRight.visibility = newVisibility
            }
            holder.swipeBackgroundLeft.visibility = View.GONE
        }
    }
    
    /**
     * 澶勭悊婊戝姩缁撴潫锛岃嚜鍔ㄥ埌浣嶆垨杩樺師
     */
    fun handleSwipeEnd(holder: ViewHolder, dx: Float, velocityX: Float) {
        val maxSwipeDistance = 120f * holder.itemView.context.resources.displayMetrics.density
        val swipeThreshold = maxSwipeDistance * 0.35f // 35%浣滀负闃堝€硷紝绋嶅井闄嶄綆浠ユ彁楂樻垚鍔熺巼
        val velocityThreshold = 400f // 闄嶄綆閫熷害闃堝€?        
        val currentDx = holder.cardContent.translationX
        val currentDistance = abs(currentDx)
        
        // 鍒ゆ柇鏄惁闇€瑕佽嚜鍔ㄥ埌浣嶏紙鎻愰珮绮惧噯搴︼級
        val shouldSnapToPosition = when {
            currentDistance > swipeThreshold -> true // 瓒呰繃闃堝€硷紝鑷姩鍒颁綅
            abs(velocityX) > velocityThreshold -> true // 蹇€熸粦鍔紝鑷姩鍒颁綅
            else -> false // 鍚﹀垯杩樺師
        }
        
        if (shouldSnapToPosition && currentDistance > maxSwipeDistance * 0.1f) {
            // 鑷姩婊戝姩鍒颁綅
            val targetX = if (isLeftHandedMode) {
                maxSwipeDistance // 宸︽拠瀛愭ā寮忥細鍗＄墖鍚戝彸绉诲姩鍒板簳锛屾樉绀哄乏渚ф寜閽?            } else {
                -maxSwipeDistance // 鏅€氭ā寮忥細鍗＄墖鍚戝乏绉诲姩鍒板簳锛屾樉绀哄彸渚ф寜閽?            }
            
            holder.cardContent.animate()
                .translationX(targetX)
                .setDuration(250) // 绋嶅井寤堕暱鍔ㄧ敾鏃堕棿锛屼娇鍥炲脊鏇村钩婊?                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    // 纭繚鎸夐挳鍦ㄥ姩鐢荤粨鏉熷悗姝ｇ‘鏄剧ず
                    if (isLeftHandedMode) {
                        holder.swipeBackgroundLeft.visibility = View.VISIBLE
                        holder.swipeBackgroundRight.visibility = View.GONE
                    } else {
                        holder.swipeBackgroundRight.visibility = View.VISIBLE
                        holder.swipeBackgroundLeft.visibility = View.GONE
                    }
                }
                .start()
            
            // 绔嬪嵆鏄剧ず瀵瑰簲鐨勬寜閽紙涓嶇瓑寰呭姩鐢伙級
            if (isLeftHandedMode) {
                holder.swipeBackgroundLeft.visibility = View.VISIBLE
                holder.swipeBackgroundRight.visibility = View.GONE
            } else {
                holder.swipeBackgroundRight.visibility = View.VISIBLE
                holder.swipeBackgroundLeft.visibility = View.GONE
            }
        } else {
            // 杩樺師浣嶇疆锛堢‘淇濆畬鍏ㄨ繕鍘燂級
            resetSwipeImmediate(holder)
            currentSwipedHolder = null
        }
    }
    
    /**
     * 绔嬪嵆杩樺師婊戝姩浣嶇疆锛堟棤鍔ㄧ敾锛?     */
    private fun resetSwipeImmediate(holder: ViewHolder) {
        holder.cardContent.translationX = 0f
        holder.swipeBackgroundLeft.visibility = View.GONE
        holder.swipeBackgroundRight.visibility = View.GONE
    }
    
    /**
     * 鎭㈠婊戝姩浣嶇疆锛堜紭鍖栧洖寮规晥鏋滐級
     */
    fun resetSwipe(holder: ViewHolder) {
        if (currentSwipedHolder == holder) {
            currentSwipedHolder = null
        }
        
        // 绔嬪嵆闅愯棌鎸夐挳
        holder.swipeBackgroundLeft.visibility = View.GONE
        holder.swipeBackgroundRight.visibility = View.GONE
        
        // 骞虫粦鍥炲脊鍒板師浣嶇疆
        holder.cardContent.animate()
            .translationX(0f)
            .setDuration(200) // 缂╃煭鍔ㄧ敾鏃堕棿锛屽噺灏戝崱椤?            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)) // 浣跨敤鏇村揩鐨勫噺閫熸彃鍊煎櫒
            .withEndAction {
                // 纭繚鏈€缁堜綅缃负0锛屾寜閽凡闅愯棌
                holder.cardContent.translationX = 0f
                holder.swipeBackgroundLeft.visibility = View.GONE
                holder.swipeBackgroundRight.visibility = View.GONE
            }
            .start()
    }

    override fun getItemCount(): Int = items.size

    fun updateEntries(newEntries: List<HistoryEntry>) {
        entries = newEntries
        items = newEntries.toMutableList()
        notifyDataSetChanged()
    }

    fun filterEntries(query: String) {
        val filtered = if (query.isBlank()) {
            items
        } else {
            items.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.url.contains(query, ignoreCase = true)
            }
        }
        updateEntries(filtered)
    }

    private fun removeAt(position: Int) {
        if (position < 0 || position >= items.size) return
        items.removeAt(position)
        notifyItemRemoved(position)
    }
}
