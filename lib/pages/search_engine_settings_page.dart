import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:your_package/providers/settings_provider.dart';
import 'package:your_package/utils/menu_layout.dart';

class SearchEngineSettingsPage extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final settingsProvider = Provider.of<SettingsProvider>(context);
    
    return Scaffold(
      appBar: AppBar(
        title: Text('搜索引擎设置'),
      ),
      body: ListView(
        children: [
          // ... 现有的搜索引擎设置选项 ...
          
          // 添加悬浮菜单布局设置部分
          ListTile(
            title: Text('悬浮菜单布局'),
            subtitle: Text(settingsProvider.menuLayout.name),
            trailing: Icon(Icons.arrow_forward_ios, size: 16),
            onTap: () {
              _showLayoutSelector(context, settingsProvider);
            },
          ),
          
          Divider(),
          
          // ... 其他搜索引擎设置选项 ...
        ],
      ),
    );
  }
  
  // 显示布局选择对话框
  void _showLayoutSelector(BuildContext context, SettingsProvider settingsProvider) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('选择悬浮菜单布局'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: MenuLayout.values.map((layout) {
              return RadioListTile<MenuLayout>(
                title: Text(layout.name),
                value: layout,
                groupValue: settingsProvider.menuLayout,
                onChanged: (value) {
                  if (value != null) {
                    settingsProvider.setMenuLayout(value);
                    Navigator.pop(context);
                  }
                },
              );
            }).toList(),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text('取消'),
            ),
          ],
        );
      },
    );
  }
} 