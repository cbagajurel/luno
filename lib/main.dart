import 'package:flutter/material.dart';

import 'ui/home/home_screen.dart';

void main() {
  runApp(const LunoApp());
}

class LunoApp extends StatelessWidget {
  const LunoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Luno',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      home: const HomeScreen(),
    );
  }
}
