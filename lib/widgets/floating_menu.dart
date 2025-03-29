import 'package:your_package/utils/menu_layout.dart';
import 'package:provider/provider.dart';
import 'package:your_package/providers/settings_provider.dart';

class FloatingMenu extends StatefulWidget {
  // ... existing code ...
}

class _FloatingMenuState extends State<FloatingMenu> {
  bool _isMenuVisible = false;
  String? _selectedLetter; // 存储用户选中的字母
  
  @override
  Widget build(BuildContext context) {
    final settingsProvider = Provider.of<SettingsProvider>(context, listen: true);
    
    return Stack(
      children: [
        // 当菜单可见时，添加一个全屏透明层用于检测点击空白区域
        if (_isMenuVisible)
          Positioned.fill(
            child: GestureDetector(
              onTap: () {
                setState(() {
                  _isMenuVisible = false;
                  _selectedLetter = null; // 重置选中的字母
                });
              },
              // 使用透明层
              child: Container(
                color: Colors.transparent,
              ),
            ),
          ),
        
        // 悬浮球
        Positioned(
          bottom: widget.bottom,
          right: widget.right,
          child: GestureDetector(
            onTap: () {
              setState(() {
                _isMenuVisible = !_isMenuVisible;
                _selectedLetter = null; // 重置选中的字母
              });
            },
            child: Container(
              // ... 悬浮球样式 ...
            ),
          ),
        ),
        
        // 悬浮菜单
        if (_isMenuVisible)
          Positioned(
            bottom: widget.bottom + 60, // 适当调整位置
            right: widget.right,
            child: _buildMenuByLayout(settingsProvider.menuLayout),
          ),
      ],
    );
  }
  
  // 根据布局类型构建不同样式的菜单
  Widget _buildMenuByLayout(MenuLayout layout) {
    switch (layout) {
      case MenuLayout.mixed:
        return _buildMixedLayout();
      case MenuLayout.alphabetical:
        return _buildAlphabeticalLayout();
    }
  }
  
  // 混合排列布局
  Widget _buildMixedLayout() {
    return Container(
      constraints: BoxConstraints(
        maxWidth: MediaQuery.of(context).size.width * 0.8, // 限制最大宽度
        maxHeight: MediaQuery.of(context).size.height * 0.6, // 限制最大高度
      ),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black26,
            blurRadius: 10,
          ),
        ],
      ),
      child: SingleChildScrollView(
        child: Padding(
          padding: EdgeInsets.all(8),
          child: Wrap(
            spacing: 8, // 水平间距
            runSpacing: 8, // 垂直间距
            alignment: WrapAlignment.center,
            children: _buildMenuItems(),
          ),
        ),
      ),
    );
  }
  
  // 字母索引排列布局
  Widget _buildAlphabeticalLayout() {
    // 按照首字母对菜单项进行分组
    Map<String, List<MenuItem>> groupedItems = {};
    
    for (var item in menuItems) {
      // 确保标题非空
      if (item.title.isNotEmpty) {
        String firstLetter = item.title.substring(0, 1).toUpperCase();
        if (!groupedItems.containsKey(firstLetter)) {
          groupedItems[firstLetter] = [];
        }
        groupedItems[firstLetter]!.add(item);
      }
    }
    
    // 获取所有字母并排序
    List<String> allLetters = groupedItems.keys.toList()..sort();
    
    // 如果没有数据，显示提示
    if (allLetters.isEmpty) {
      return Container(
        padding: EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          boxShadow: [BoxShadow(color: Colors.black26, blurRadius: 10)],
        ),
        child: Text('没有可用的菜单项'),
      );
    }
    
    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.6, // 限制最大高度
      ),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black26,
            blurRadius: 10,
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 左侧字母索引列表
          Container(
            padding: EdgeInsets.symmetric(vertical: 8, horizontal: 4),
            decoration: BoxDecoration(
              color: Colors.grey[200],
              borderRadius: BorderRadius.only(
                topLeft: Radius.circular(12),
                bottomLeft: Radius.circular(12),
              ),
            ),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: allLetters.map((letter) {
                  return GestureDetector(
                    onTap: () {
                      setState(() {
                        _selectedLetter = _selectedLetter == letter ? null : letter;
                      });
                    },
                    child: Container(
                      width: 36,
                      height: 36,
                      margin: EdgeInsets.symmetric(vertical: 2),
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        color: _selectedLetter == letter ? Colors.blue.withOpacity(0.3) : Colors.transparent,
                        borderRadius: BorderRadius.circular(18),
                      ),
                      child: Text(
                        letter,
                        style: TextStyle(
                          fontWeight: _selectedLetter == letter ? FontWeight.bold : FontWeight.normal,
                          color: _selectedLetter == letter ? Colors.blue : Colors.black,
                          fontSize: 16,
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),
          
          // 右侧显示选中字母对应的图标或提示
          Container(
            padding: EdgeInsets.all(8),
            constraints: BoxConstraints(
              maxWidth: 200,
              maxHeight: 300,
            ),
            child: _selectedLetter != null && groupedItems.containsKey(_selectedLetter)
              ? SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: groupedItems[_selectedLetter]!.map((item) {
                      return _buildMenuItem(item);
                    }).toList(),
                  ),
                )
              : Container(
                  padding: EdgeInsets.all(16),
                  alignment: Alignment.center,
                  child: Text(
                    _selectedLetter == null ? '请选择一个字母' : '没有以 $_selectedLetter 开头的项目',
                    textAlign: TextAlign.center,
                  ),
                ),
          ),
        ],
      ),
    );
  }
  
  // 构建菜单项
  List<Widget> _buildMenuItems() {
    return menuItems.map((item) {
      return _buildMenuItem(item);
    }).toList();
  }
  
  // 构建单个菜单项
  Widget _buildMenuItem(MenuItem item) {
    return GestureDetector(
      onTap: () {
        // 点击菜单项后关闭菜单
        setState(() {
          _isMenuVisible = false;
          _selectedLetter = null;
        });
        // 执行菜单项操作
        item.onTap();
      },
      child: Container(
        padding: EdgeInsets.all(12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(item.icon, size: 24),
            SizedBox(height: 4),
            Text(item.title, style: TextStyle(fontSize: 12)),
          ],
        ),
      ),
    );
  }
}

// 圆形布局代理
class CircularFlowDelegate extends FlowDelegate {
  final int itemCount;
  
  CircularFlowDelegate(this.itemCount);
  
  @override
  void paintChildren(FlowPaintingContext context) {
    final radius = context.size.shortestSide / 2;
    final centerX = context.size.width / 2;
    final centerY = context.size.height / 2;
    
    for (int i = 0; i < context.childCount; i++) {
      final angle = 2 * 3.14159 * i / itemCount;
      final x = centerX + radius * 0.6 * cos(angle);
      final y = centerY + radius * 0.6 * sin(angle);
      
      context.paintChild(
        i,
        transform: Matrix4.translationValues(
          x - context.getChildSize(i)!.width / 2,
          y - context.getChildSize(i)!.height / 2,
          0,
        ),
      );
    }
  }
  
  @override
  bool shouldRepaint(covariant FlowDelegate oldDelegate) {
    return true;
  }
} 