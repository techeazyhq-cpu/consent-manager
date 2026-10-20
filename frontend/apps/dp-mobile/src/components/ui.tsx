/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

import type { ReactNode } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View, useColorScheme, type ViewStyle } from "react-native";
import type { CnsLcySts } from "api-client";
import { STATUS } from "dp-kit";

/** The portal's palette (ui-kit's tokens) for dark mode, and its light counterpart; both meet WCAG AA for text. */
const DARK = {
  bg: "#0f1720",
  surface: "#1a2432",
  surface2: "#212d3d",
  border: "#2c3a4d",
  text: "#e8edf3",
  dim: "#a3b1c4",
  primary: "#4f8cff",
  onPrimary: "#0b1220",
  success: "#35c98f",
  warning: "#f0a93a",
  danger: "#ef5a6f",
};
const LIGHT = {
  bg: "#f5f7fa",
  surface: "#ffffff",
  surface2: "#eef2f7",
  border: "#d5dde8",
  text: "#111827",
  dim: "#4b5563",
  primary: "#1f5fd6",
  onPrimary: "#ffffff",
  success: "#11744f",
  warning: "#8a5300",
  danger: "#b42336",
};
export type Palette = typeof DARK;

export function useColors(): Palette {
  return useColorScheme() === "dark" ? DARK : LIGHT;
}

/** Touch targets at least 48 by 48 (WCAG 2.5.8, Material and Apple guidance). */
const TARGET = 48;

export function Button({
  label,
  onPress,
  kind = "secondary",
  disabled,
  busy,
  hint,
}: {
  label: string;
  onPress: () => void;
  kind?: "primary" | "secondary" | "danger";
  disabled?: boolean;
  busy?: boolean;
  /** What happens, for screen readers, when the label alone doesn't say. */
  hint?: string;
}) {
  const c = useColors();
  const bg = kind === "primary" ? c.primary : c.surface2;
  const fg = kind === "primary" ? c.onPrimary : kind === "danger" ? c.danger : c.text;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={hint}
      accessibilityState={{ disabled: !!disabled || !!busy, busy: !!busy }}
      disabled={disabled || busy}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg, borderColor: kind === "danger" ? c.danger : c.border, opacity: disabled ? 0.5 : pressed ? 0.8 : 1 },
      ]}
    >
      {busy ? <ActivityIndicator color={fg} /> : <Text style={[styles.buttonText, { color: fg }]}>{label}</Text>}
    </Pressable>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  const c = useColors();
  return <View style={[styles.card, { backgroundColor: c.surface, borderColor: c.border }, style]}>{children}</View>;
}

export function Title({ children }: { children: ReactNode }) {
  const c = useColors();
  return (
    <Text accessibilityRole="header" style={[styles.title, { color: c.text }]}>
      {children}
    </Text>
  );
}

export function Body({ children, dim, style }: { children: ReactNode; dim?: boolean; style?: object }) {
  const c = useColors();
  return <Text style={[styles.body, { color: dim ? c.dim : c.text }, style]}>{children}</Text>;
}

export function ErrorText({ message }: { message: string | null }) {
  const c = useColors();
  if (!message) return null;
  return (
    <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={[styles.body, { color: c.danger, marginVertical: 8 }]}>
      {message}
    </Text>
  );
}

/** Where a consent stands, in words and colour; the words alone carry it. */
export function StatusPill({ sts }: { sts: CnsLcySts }) {
  const c = useColors();
  const { label, badge } = STATUS[sts];
  const tone = badge === "ACTIVE" ? c.success : badge === "PENDING" ? c.warning : c.dim;
  return (
    <View style={[styles.pill, { borderColor: tone }]}>
      <Text style={[styles.pillText, { color: tone }]}>{label}</Text>
    </View>
  );
}

export function Checkbox({
  checked,
  onChange,
  label,
  detail,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  detail?: string;
}) {
  const c = useColors();
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
      accessibilityLabel={label}
      onPress={() => onChange(!checked)}
      style={styles.checkRow}
    >
      <View style={[styles.box, { borderColor: checked ? c.primary : c.dim, backgroundColor: checked ? c.primary : "transparent" }]}>
        {checked ? <Text style={{ color: c.onPrimary, fontWeight: "700" }}>✓</Text> : null}
      </View>
      <View style={{ flex: 1 }}>
        <Text style={[styles.body, { color: c.text }]}>{label}</Text>
        {detail ? <Text style={[styles.small, { color: c.dim }]}>{detail}</Text> : null}
      </View>
    </Pressable>
  );
}

/**
 * A number of days from today, between `min` and `max`, with the date it gives: steppers and a few common choices,
 * since a native date picker isn't in Expo Go. The screen reader hears it as an adjustable value.
 */
export function DaysPicker({
  value,
  onChange,
  min,
  max,
  presets,
  label,
}: {
  value: number;
  onChange: (days: number) => void;
  min: number;
  max: number;
  presets: number[];
  label: string;
}) {
  const c = useColors();
  const clamp = (n: number) => Math.min(max, Math.max(min, n));
  const until = new Date(Date.now() + value * 86_400_000).toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
  return (
    <View>
      <View
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={label}
        accessibilityValue={{ min, max, now: value, text: `${value} day${value === 1 ? "" : "s"}, until ${until}` }}
        accessibilityActions={[{ name: "increment" }, { name: "decrement" }]}
        onAccessibilityAction={(e) => onChange(clamp(value + (e.nativeEvent.actionName === "increment" ? 1 : -1)))}
        style={styles.stepper}
      >
        <Pressable
          accessibilityElementsHidden
          importantForAccessibility="no"
          onPress={() => onChange(clamp(value - 1))}
          style={[styles.step, { borderColor: c.border }]}
        >
          <Text style={[styles.stepText, { color: c.text }]}>−</Text>
        </Pressable>
        <View style={{ flex: 1, alignItems: "center" }}>
          <Text style={[styles.title, { color: c.text, marginBottom: 0 }]}>{value} day{value === 1 ? "" : "s"}</Text>
          <Text style={[styles.small, { color: c.dim }]}>until {until}</Text>
        </View>
        <Pressable
          accessibilityElementsHidden
          importantForAccessibility="no"
          onPress={() => onChange(clamp(value + 1))}
          style={[styles.step, { borderColor: c.border }]}
        >
          <Text style={[styles.stepText, { color: c.text }]}>+</Text>
        </Pressable>
      </View>
      <View style={styles.presets}>
        {Array.from(new Set(presets.map(clamp))).map((d) => (
          <Pressable
            key={d}
            accessibilityRole="button"
            accessibilityLabel={`${d} day${d === 1 ? "" : "s"}`}
            accessibilityState={{ selected: d === value }}
            onPress={() => onChange(d)}
            style={[styles.preset, { borderColor: d === value ? c.primary : c.border, backgroundColor: d === value ? c.surface2 : "transparent" }]}
          >
            <Text style={{ color: c.text }}>{`${d} day${d === 1 ? "" : "s"}${d === max ? " (most)" : ""}`}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

export const styles = StyleSheet.create({
  button: {
    minHeight: TARGET,
    minWidth: TARGET,
    paddingHorizontal: 16,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  buttonText: { fontSize: 16, fontWeight: "600" },
  card: { borderWidth: 1, borderRadius: 12, padding: 16, marginBottom: 12 },
  title: { fontSize: 18, fontWeight: "700", marginBottom: 6 },
  body: { fontSize: 16, lineHeight: 22 },
  small: { fontSize: 14, lineHeight: 20 },
  pill: { alignSelf: "flex-start", borderWidth: 1, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 2 },
  pillText: { fontSize: 13, fontWeight: "600" },
  checkRow: { flexDirection: "row", gap: 12, alignItems: "flex-start", minHeight: TARGET, paddingVertical: 8 },
  box: { width: 26, height: 26, borderWidth: 2, borderRadius: 6, alignItems: "center", justifyContent: "center", marginTop: 1 },
  stepper: { flexDirection: "row", alignItems: "center", gap: 12, marginVertical: 8 },
  step: { width: TARGET, height: TARGET, borderWidth: 1, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  stepText: { fontSize: 24, fontWeight: "600" },
  presets: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 },
  preset: { minHeight: 44, paddingHorizontal: 12, borderWidth: 1, borderRadius: 999, justifyContent: "center" },
  row: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 },
});
