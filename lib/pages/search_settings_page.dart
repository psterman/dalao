import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:your_app_name/providers/settings_provider.dart';
import 'package:your_app_name/utils/menu_layout.dart';

class SearchSettingsPage extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final settingsProvider = Provider.of<SettingsProvider>(context);
    
    // 添加调试信息
    print('当前菜单布局: ${settingsProvider.menuLayout.name}');
    print('可用的布局选项: ${MenuLayout.values.map((e) => e.name).join(', ')}');
    
    return Scaffold(
      appBar: AppBar(
        title: Text('搜索设置'),
      ),
      body: Padding(
        padding: EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '搜索设置',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            SizedBox(height: 16),
            
            // 现有的两个选项（根据图片中显示的）
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                // 左侧搜索选项
                Container(
                  width: MediaQuery.of(context).size.width * 0.4,
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.grey),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Padding(
                    padding: EdgeInsets.all(16.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.search),
                        Text("搜索选项1"),
                      ],
                    ),
                  ),
                ),
                
                // 右侧搜索选项
                Container(
                  width: MediaQuery.of(context).size.width * 0.4,
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.grey),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Padding(
                    padding: EdgeInsets.all(16.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.sync),
                        Text("搜索选项2"),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            
            SizedBox(height: 24),
            
            // 新增第三行：悬浮菜单布局选项
            Text(
              '悬浮菜单布局',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            SizedBox(height: 16),
            
            // 布局选择卡片
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                // 混合排列选项
                _buildLayoutOption(
                  context, 
                  settingsProvider, 
                  MenuLayout.mixed, 
                  Icons.grid_view, 
                  "混合排列"
                ),
                
                // 字母索引排列选项
                _buildLayoutOption(
                  context, 
                  settingsProvider, 
                  MenuLayout.alphabetical, 
                  Icons.sort_by_alpha, 
                  "字母索引排列"
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  // 抽取布局选项构建逻辑为一个方法
  Widget _buildLayoutOption(
    BuildContext context, 
    SettingsProvider provider, 
    MenuLayout layout, 
    IconData icon, 
    String title
  ) {
    bool isSelected = provider.menuLayout == layout;
    
    return GestureDetector(
      onTap: () {
        provider.setMenuLayout(layout);
      },
      child: Container(
        width: MediaQuery.of(context).size.width * 0.4,
        decoration: BoxDecoration(
          border: Border.all(
            color: isSelected ? Colors.blue : Colors.grey,
            width: isSelected ? 2 : 1,
          ),
          borderRadius: BorderRadius.circular(8),
          color: isSelected ? Colors.blue.withOpacity(0.1) : Colors.transparent,
        ),
        padding: EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              color: isSelected ? Colors.blue : Colors.grey,
            ),
            SizedBox(height: 8),
            Text(
              title,
              style: TextStyle(
                color: isSelected ? Colors.blue : Colors.black,
              ),
            ),
          ],
        ),
      ),
    );
  }
} 