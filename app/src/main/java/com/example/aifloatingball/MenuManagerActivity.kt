package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import com.example.aifloatingball.model.MenuItem
import com.example.aifloatingball.model.MenuCategory
import java.util.*

class MenuManagerActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu_manager)

        settingsManager = SettingsManager.getInstance(this)

        // 初始化视图
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 设置ViewPager适配器
        viewPager.adapter = MenuPagerAdapter(this)

        // 设置TabLayout
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "普通搜索"
                1 -> "AI搜索"
                2 -> "功能"
                else -> "未知"
            }
        }.attach()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onBackPressed() {
        // 发送广播通知更新菜单
        sendBroadcast(Intent("com.example.aifloatingball.ACTION_UPDATE_MENU"))
        super.onBackPressed()
    }
}

class MenuPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return MenuListFragment.newInstance(when (position) {
            0 -> MenuCategory.NORMAL_SEARCH
            1 -> MenuCategory.AI_SEARCH
            2 -> MenuCategory.FUNCTION
            else -> throw IllegalArgumentException("Invalid position")
        })
    }
}

class MenuListFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MenuAdapter
    private lateinit var settingsManager: SettingsManager
    private var menuItems = mutableListOf<MenuItem>()

    companion object {
        private const val ARG_CATEGORY = "category"

        fun newInstance(category: MenuCategory): MenuListFragment {
            return MenuListFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CATEGORY, category)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            recyclerView = this
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsManager = SettingsManager.getInstance(requireContext())
        
        // 获取当前类别
        val category = arguments?.getSerializable(ARG_CATEGORY) as MenuCategory
        
        // 获取并过滤菜单项
        menuItems = settingsManager.getMenuItems()
            .filter { it.category == category }
            .toMutableList()

        // 设置适配器
        adapter = MenuAdapter(menuItems) { position, isEnabled ->
            menuItems[position].isEnabled = isEnabled
            settingsManager.saveMenuItems(getAllMenuItems())
        }
        recyclerView.adapter = adapter

        // 设置拖拽排序
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                
                Collections.swap(menuItems, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)
                
                // 保存更新后的顺序
                settingsManager.saveMenuItems(getAllMenuItems())
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不实现滑动删除
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun getAllMenuItems(): List<MenuItem> {
        val allItems = settingsManager.getMenuItems().toMutableList()
        val category = arguments?.getSerializable(ARG_CATEGORY) as MenuCategory
        
        // 更新当前类别的项目
        menuItems.forEachIndexed { index, item ->
            val position = allItems.indexOfFirst { it.name == item.name && it.category == category }
            if (position != -1) {
                allItems[position] = item.copy(isEnabled = item.isEnabled)
            }
        }
        
        return allItems
    }
}

class MenuAdapter(
    private val items: List<MenuItem>,
    private val onSwitchChanged: (position: Int, isEnabled: Boolean) -> Unit
) : RecyclerView.Adapter<MenuAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.menuIcon)
        val name: TextView = view.findViewById(R.id.menuName)
        val switch: SwitchCompat = view.findViewById(R.id.switchEnabled)
        val dragHandle: ImageView = view.findViewById(R.id.dragHandle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_manager, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.icon.setImageResource(item.iconRes)
        holder.name.text = item.name
        holder.switch.isChecked = item.isEnabled
        
        holder.switch.setOnCheckedChangeListener { _, isChecked ->
            onSwitchChanged(position, isChecked)
        }
    }

    override fun getItemCount() = items.size
} 