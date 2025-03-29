import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:your_app_name/providers/settings_provider.dart';
// ... other imports ...

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => SettingsProvider()),
        // ... other providers ...
      ],
      child: MaterialApp(
        title: '您的应用名称',
        theme: ThemeData(
          primarySwatch: Colors.blue,
          // ... other theme settings ...
        ),
        home: HomePage(),
      ),
    );
  }
} 