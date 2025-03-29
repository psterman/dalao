import 'package:your_package/utils/menu_layout.dart';
import 'package:provider/provider.dart';

class SettingsPage extends StatefulWidget {
  // ... existing code ...
}

class _SettingsPageState extends State<SettingsPage> {
  // ... existing code ...
  
  @override
  Widget build(BuildContext context) {
    final settingsProvider = Provider.of<SettingsProvider>(context);
    
    return Scaffold(
      appBar: AppBar(
        title: Text('设置'),
      ),
      body: ListView(
        children: [
          // ... existing code ...
          
          // 搜索设置展开面板
          ExpansionTile(
            title: Text('搜索设置'),
            children: [
              // 现有搜索设置项
              // ...
              
              // 悬浮菜单布局设置
              ListTile(
                title: Text('悬浮菜单布局'),
                subtitle: Text(settingsProvider.menuLayout.name),
                trailing: Icon(Icons.settings, size: 20),
                onTap: () {
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
                },
              ),
              
              // 其他搜索设置项
              // ...
            ],
          ),
          
          // 保留只一个搜索设置选项
          ListTile(
            title: Text('搜索设置'),
            trailing: Icon(Icons.arrow_forward_ios, size: 16),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => SearchSettingsPage(),
                ),
              );
            },
          ),
          
          // ... existing code ...
        ],
      ),
    );
  }
  
  // 可以保留这个方法，但不在此页面直接使用
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

// 移除此子页面，使用单独的 SearchSettingsPage 文件代替
// class SearchSettingsSubPage extends StatelessWidget {
//   @override
//   Widget build(BuildContext context) {
//     // ... existing code ...
//   }
// } 