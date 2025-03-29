import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:your_package/utils/menu_layout.dart';

class SettingsProvider with ChangeNotifier {
  late SharedPreferences _prefs;
  
  // 菜单布局设置
  MenuLayout _menuLayout = MenuLayout.mixed;
  
  MenuLayout get menuLayout => _menuLayout;
  
  // 构造函数
  SettingsProvider() {
    loadSettings();
  }
  
  // 设置菜单布局
  Future<void> setMenuLayout(MenuLayout layout) async {
    _menuLayout = layout;
    await _prefs.setInt('menu_layout', layout.index);
    notifyListeners();
  }
  
  // 加载设置
  Future<void> loadSettings() async {
    _prefs = await SharedPreferences.getInstance();
    
    // 加载菜单布局设置
    final layoutIndex = _prefs.getInt('menu_layout') ?? 0;
    if (layoutIndex < MenuLayout.values.length) {
      _menuLayout = MenuLayout.values[layoutIndex];
    } else {
      _menuLayout = MenuLayout.mixed; // 默认为混合排列
    }
    
    notifyListeners();
  }
} 