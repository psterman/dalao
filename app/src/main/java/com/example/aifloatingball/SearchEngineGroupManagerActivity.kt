package com.example.aifloatingball

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.model.SearchEngineCategory
import com.example.aifloatingball.settings.SearchEngineListFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * 搜索引擎组合管理活动
 * 允许用户查看、启用或禁用搜索引擎组合，并管理显示在FloatingWindowService中的快捷方式
 */
class SearchEngineGroupManagerActivity : AppCompatActivity() {

    private lateinit var categoryRecyclerView: RecyclerView
    private var selectedCategory: SearchEngineCategory? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_search_manager)

        supportActionBar?.apply {
            title = "自定义网页搜索引擎"
            setDisplayHomeAsUpEnabled(true)
        }

        categoryRecyclerView = findViewById(R.id.category_recycler_view)
        categoryRecyclerView.layoutManager = LinearLayoutManager(this)

        val categories = SearchEngineCategory.values().toList()
        val categoryAdapter = CategoryAdapter(categories) { category ->
            selectCategory(category)
        }
        categoryRecyclerView.adapter = categoryAdapter

        if (savedInstanceState == null && categories.isNotEmpty()) {
            selectCategory(categories.first())
        }
        
        findViewById<FloatingActionButton>(R.id.fab_add_engine).setOnClickListener {
            val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
            if (fragment is SearchEngineListFragment) {
                fragment.showEditDialog(null)
            } else {
                Toast.makeText(this, "请先选择一个分类", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectCategory(category: SearchEngineCategory) {
        if (selectedCategory == category && supportFragmentManager.findFragmentById(R.id.fragment_container) != null) {
            return // Avoid reloading the same fragment
        }
        selectedCategory = category

        val fragment = SearchEngineListFragment.newInstance(category)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, FRAGMENT_TAG)
            .commit()

        (categoryRecyclerView.adapter as? CategoryAdapter)?.setSelectedCategory(category)
    }

    private class CategoryAdapter(
        private val categories: List<SearchEngineCategory>,
        private val onCategoryClick: (SearchEngineCategory) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        private var selectedCategory: SearchEngineCategory? = null

        fun setSelectedCategory(category: SearchEngineCategory) {
            val oldSelectedPosition = categories.indexOf(selectedCategory)
            val newSelectedPosition = categories.indexOf(category)
            selectedCategory = category
            if (oldSelectedPosition != -1) {
                notifyItemChanged(oldSelectedPosition)
            }
            if (newSelectedPosition != -1) {
                notifyItemChanged(newSelectedPosition)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            holder.bind(category, category == selectedCategory)
            holder.itemView.setOnClickListener { onCategoryClick(category) }
        }

        override fun getItemCount() = categories.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textView: TextView = itemView.findViewById(android.R.id.text1)

            fun bind(category: SearchEngineCategory, isSelected: Boolean) {
                textView.text = category.displayName
                val context = itemView.context
                if (isSelected) {
                    itemView.setBackgroundColor(getColorFromAttr(context, com.google.android.material.R.attr.colorPrimaryContainer))
                    textView.setTextColor(getColorFromAttr(context, com.google.android.material.R.attr.colorOnPrimaryContainer))
                } else {
                    itemView.setBackgroundColor(getColorFromAttr(context, com.google.android.material.R.attr.colorSurface))
                    textView.setTextColor(getColorFromAttr(context, com.google.android.material.R.attr.colorOnSurface))
                }
            }

            private fun getColorFromAttr(context: Context, @AttrRes attrRes: Int): Int {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(attrRes, typedValue, true)
                return typedValue.data
            }
        }
    }

    companion object {
        private const val FRAGMENT_TAG = "SearchEngineListFragment"
    }
} 