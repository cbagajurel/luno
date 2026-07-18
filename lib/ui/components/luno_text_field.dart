import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A themed form field. Thin wrapper over [TextFormField] that carries the
/// design-system input styling and the common knobs the forms need, so the
/// pairing and compose fields stop re-declaring `OutlineInputBorder` by hand.
class LunoTextField extends StatelessWidget {
  const LunoTextField({
    super.key,
    required this.label,
    this.controller,
    this.hint,
    this.prefixIcon,
    this.keyboardType,
    this.inputFormatters,
    this.textInputAction,
    this.autofocus = false,
    this.minLines,
    this.maxLines = 1,
    this.validator,
    this.onSubmitted,
    this.enabled = true,
  });

  final String label;
  final TextEditingController? controller;
  final String? hint;
  final IconData? prefixIcon;
  final TextInputType? keyboardType;
  final List<TextInputFormatter>? inputFormatters;
  final TextInputAction? textInputAction;
  final bool autofocus;
  final int? minLines;
  final int? maxLines;
  final String? Function(String?)? validator;
  final ValueChanged<String>? onSubmitted;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      keyboardType: keyboardType,
      inputFormatters: inputFormatters,
      textInputAction: textInputAction,
      autofocus: autofocus,
      minLines: minLines,
      maxLines: maxLines,
      validator: validator,
      enabled: enabled,
      onFieldSubmitted: onSubmitted,
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        prefixIcon: prefixIcon == null ? null : Icon(prefixIcon, size: 20),
      ),
    );
  }
}
