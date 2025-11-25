package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.BookmarkAdapter
import com.example.aifloatingball.manager.BookmarkManager
import com.example.aifloatingball.model.Bookmark
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import java.util.*

class BookmarkActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BookmarkAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var addFolderButton: FloatingActionButton
    private lateinit var bookmarkManager: BookmarkManager
    
    private var currentFolder = "默认"
    private var bookmarks: List<Bookmark> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmark)
        
        // 初始化书签管理器
        bookmarkManager = BookmarkManager.getInstance(this)
        
        // 设置标题栏
        supportActionBar?.apply {
            title = "收藏夹"
            setDisplayHomeAsUpEnabled(true)
        }
        
        // 初始化视图
        initViews()
        
        // 加载书签数据
        loadBookmarks()
    }
    
    private fun initViews() {
        // 初始化 RecyclerView
        recyclerView = findViewById(R.id.bookmarks_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // 初始化适配器
        adapter = BookmarkAdapter(
            context = this,
            bookmarks = emptyList(),
            onItemClick = { bookmark -> openBookmark(bookmark) },
            onItemLongClick = { bookmark, view -> showBookmarkOptions(bookmark, view) }
        )
        recyclerView.adapter = adapter
        
        // 初始化标签栏
        tabLayout = findViewById(R.id.folder_tabs)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentFolder = tab.text.toString()
                filterBookmarksByFolder()
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        // 初始化添加文件夹按钮
        addFolderButton = findViewById(R.id.add_folder_button)
        addFolderButton.setOnClickListener {
            showAddFolderDialog()
        }
    }
    
    private fun loadBookmarks() {
        // 获取所有书签
        bookmarks = bookmarkManager.getAllBookmarks()
        
        // 更新文件夹标签
        updateFolderTabs()
        
        // 过滤并显示当前文件夹的书签
        filterBookmarksByFolder()
    }
    
    private fun updateFolderTabs() {
        val folders = bookmarkManager.getAllFolders()
        
        // 清空现有标签
        tabLayout.removeAllTabs()
        
        // 添加文件夹标签
        folders.forEach { folder ->
            tabLayout.addTab(tabLayout.newTab().setText(folder))
        }
        
        // 选中当前文件夹对应的标签
        for (i in 0 until tabLayout.tabCount) {
            if (tabLayout.getTabAt(i)?.text.toString() == currentFolder) {
                tabLayout.selectTab(tabLayout.getTabAt(i))
                break
            }
        }
    }
    
    private fun filterBookmarksByFolder() {
        // 过滤出当前文件夹的书签
        val filteredBookmarks = bookmarks.filter { it.folder == currentFolder }
        
        // 更新适配器数据
        adapter.updateBookmarks(filteredBookmarks)
        
        // 显示空视图
        val emptyView = findViewById<View>(R.id.empty_view)
        if (filteredBookmarks.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun openBookmark(bookmark: Bookmark) {
        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra("OPEN_URL", bookmark.url)
        startActivity(intent)
        finish()
    }
    
    private fun showBookmarkOptions(bookmark: Bookmark, view: View): Boolean {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.bookmark_options, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit_bookmark -> {
                    showEditBookmarkDialog(bookmark)
                    true
                }
                R.id.action_delete_bookmark -> {
                    showDeleteBookmarkDialog(bookmark)
                    true
                }
                R.id.action_move_bookmark -> {
                    showMoveBookmarkDialog(bookmark)
                    true
                }
                else -> false
            }
        }
        
        popup.show()
        return true
    }
    
    private fun showAddFolderDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val inputField = dialogView.findViewById<TextInputEditText>(R.id.input_field)
        
        AlertDialog.Builder(this)
            .setTitle("新建文件夹")
            .setView(dialogView)
            .setPositiveButton("确定") { dialog, which ->
                val folderName = inputField.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    // 检查文件夹是否已存在
                    val folders = bookmarkManager.getAllFolders()
                    if (folderName in folders) {
                        Toast.makeText(this, "文件夹已存在", Toast.LENGTH_SHORT).show()
                    } else {
                        // 创建一个空书签用于创建文件夹
                        val bookmark = Bookmark(
                            title = "新建文件夹",
                            url = "https://example.com",
                            folder = folderName
                        )
                        bookmarkManager.addBookmark(bookmark)
                        
                        // 删除这个临时书签，但保留文件夹结构
                        bookmarkManager.deleteBookmark(bookmark.id)
                        
                        // 更新UI
                        loadBookmarks()
                        currentFolder = folderName
                        updateFolderTabs()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 创建支持暗色/亮色模式的对话框构建器
     */
    private fun createThemedDialogBuilder(): AlertDialog.Builder {
        return AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
    }
    
    private fun showEditBookmarkDialog(bookmark: Bookmark) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_bookmark, null)
        val titleField = dialogView.findViewById<TextInputEditText>(R.id.bookmark_title_field)
        val urlField = dialogView.findViewById<TextInputEditText>(R.id.bookmark_url_field)
        
        titleField.setText(bookmark.title)
        urlField.setText(bookmark.url)
        
        createThemedDialogBuilder()
            .setTitle("编辑书签")
            .setView(dialogView)
            .setPositiveButton("保存") { dialog, which ->
                val newTitle = titleField.text.toString().trim()
                val newUrl = urlField.text.toString().trim()
                
                if (newTitle.isNotEmpty() && newUrl.isNotEmpty()) {
                    val updatedBookmark = bookmark.copy(
                        title = newTitle,
                        url = newUrl
                    )
                    
                    bookmarkManager.updateBookmark(updatedBookmark)
                    loadBookmarks()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteBookmarkDialog(bookmark: Bookmark) {
        createThemedDialogBuilder()
            .setTitle("删除书签")
            .setMessage("确定要删除\"${bookmark.title}\"吗？")
            .setPositiveButton("删除") { dialog, which ->
                bookmarkManager.deleteBookmark(bookmark.id)
                loadBookmarks()
                Toast.makeText(this, "已删除书签", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showMoveBookmarkDialog(bookmark: Bookmark) {
        val folders = bookmarkManager.getAllFolders().toTypedArray()
        
        createThemedDialogBuilder()
            .setTitle("移动到文件夹")
            .setItems(folders) { dialog, which ->
                val selectedFolder = folders[which]
                if (selectedFolder != bookmark.folder) {
                    val updatedBookmark = bookmark.copy(folder = selectedFolder)
                    bookmarkManager.updateBookmark(updatedBookmark)
                    loadBookmarks()
                    Toast.makeText(this, "已移动到\"$selectedFolder\"", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bookmark_activity_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_rename_folder -> {
                if (currentFolder != "默认") {
                    showRenameFolderDialog()
                } else {
                    Toast.makeText(this, "无法重命名默认文件夹", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_delete_folder -> {
                if (currentFolder != "默认") {
                    showDeleteFolderDialog()
                } else {
                    Toast.makeText(this, "无法删除默认文件夹", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showRenameFolderDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val inputField = dialogView.findViewById<TextInputEditText>(R.id.input_field)
        inputField.setText(currentFolder)
        
        createThemedDialogBuilder()
            .setTitle("重命名文件夹")
            .setView(dialogView)
            .setPositiveButton("确定") { dialog, which ->
                val newName = inputField.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentFolder) {
                    // 检查文件夹是否已存在
                    val folders = bookmarkManager.getAllFolders()
                    if (newName in folders) {
                        Toast.makeText(this, "文件夹名已存在", Toast.LENGTH_SHORT).show()
                    } else {
                        // 重命名文件夹
                        bookmarkManager.renameFolder(currentFolder, newName)
                        currentFolder = newName
                        loadBookmarks()
                        Toast.makeText(this, "已重命名文件夹", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteFolderDialog() {
        createThemedDialogBuilder()
            .setTitle("删除文件夹")
            .setMessage("确定要删除\"$currentFolder\"文件夹吗？\n文件夹中的所有书签将被移动到\"默认\"文件夹。")
            .setPositiveButton("删除") { dialog, which ->
                // 将该文件夹中的所有书签移动到默认文件夹
                bookmarkManager.renameFolder(currentFolder, "默认")
                
                // 更新当前文件夹
                currentFolder = "默认"
                
                // 重新加载数据
                loadBookmarks()
                
                Toast.makeText(this, "已删除文件夹", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        loadBookmarks()
    }
} 