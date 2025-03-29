import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:your_package/providers/settings_provider.dart';
import 'package:your_package/utils/menu_layout.dart';

class MenuLayoutSettingsPage extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final settingsProvider = Provider.of<SettingsProvider>(context);
    
    return Scaffold(
      appBar: AppBar(
        title: Text('悬浮菜单布局'),
      ),
      body: ListView.builder(
        itemCount: MenuLayout.values.length,
        itemBuilder: (context, index) {
          final layout = MenuLayout.values[index];
          return RadioListTile<MenuLayout>(
            title: Text(layout.name),
            value: layout,
            groupValue: settingsProvider.menuLayout,
            onChanged: (MenuLayout? value) {
              if (value != null) {
                settingsProvider.setMenuLayout(value);
              }
            },
          );
        },
      ),
    );
  }
} 