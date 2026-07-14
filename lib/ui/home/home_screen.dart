import 'package:flutter/material.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Luno')),
      body: const Center(child: Text('Device status, pairing, and settings will live here.')),
    );
  }
}
