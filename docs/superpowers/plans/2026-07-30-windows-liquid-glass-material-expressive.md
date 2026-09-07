# Windows Liquid Glass and Material Expressive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Upgrade the Windows `nimbo` style into a complete Liquid Glass visual system and refresh the `material_you` style with expressive shapes, tonal hierarchy, and rounded floating navigation.

**Architecture:** Keep the existing persisted `nimbo` and `material_you` values for backward compatibility. Compute style-aware opacity and backdrop variables in TypeScript, then apply global component and navigation treatments through scoped CSS so every existing page inherits the new design without page-by-page duplication.

**Tech Stack:** React 19, TypeScript 5.8, CSS, Vite 7, Tauri 2

---

### Task 1: Make visual variables style-aware

**Files:**
- Modify: `apps/ui/src/lib/visualTheme.ts`

- [x] **Step 1: Resolve the effective UI style once**

Resolve the provider override or user preference into `effectiveUiStyle`, set it on `body.dataset.uiStyle`, and use it for visual calculations.

- [x] **Step 2: Give Liquid Glass a translucent baseline**

Map the existing transparency slider to a Liquid Glass panel range instead of making its default fully opaque. Increase background saturation and contrast for glass while keeping Material surfaces opaque and tonal.

- [x] **Step 3: Build TypeScript**

Run: `npm run build`

Expected: TypeScript passes and Vite emits production assets.

### Task 2: Implement the full Liquid Glass system

**Files:**
- Modify: `apps/ui/src/styles.css`

- [x] **Step 1: Add Liquid Glass tokens**

Add scoped refractive rim, specular highlight, depth shadow, translucent controls, stronger background auras, and smooth spring-like transitions under `body[data-ui-style="nimbo"]`.

- [x] **Step 2: Apply glass to shared surfaces**

Style `.glass`, `.panel`, `.liquid-card`, `.settings-card`, page surfaces, server/statistics/routing cards, dialogs, and controls with the same layered material.

- [x] **Step 3: Add accessible fallbacks**

Under `prefers-reduced-transparency`-equivalent browser support constraints and `prefers-reduced-motion`, disable expensive movement and increase opacity while preserving contrast.

### Task 3: Refresh Material You Expressive

**Files:**
- Modify: `apps/ui/src/styles.css`

- [x] **Step 1: Update shape and tonal hierarchy**

Use larger asymmetric-friendly rounded containers, distinct low/high surface tones, expressive state layers, and clearer primary containers.

- [x] **Step 2: Update navigation**

Make the desktop rail a floating rounded container with capsule destinations. Preserve the compact responsive bottom navigation and give its selected destination a wide rounded pill.

- [x] **Step 3: Keep modes visually distinct**

Ensure Material mode contains no refractive glass rim or moving highlights, while Liquid Glass keeps those effects.

### Task 4: Update settings labels and previews

**Files:**
- Modify: `apps/ui/src/lib/i18n.ts`
- Modify: `apps/ui/src/pages/Settings.tsx`
- Modify: `apps/ui/src/styles.css`

- [x] **Step 1: Rename Nimbo Glass**

Use `Liquid Glass` / `Жидкое стекло` with an iOS-inspired subtitle, while keeping the internal `nimbo` value.

- [x] **Step 2: Refresh both miniature previews**

Show a refractive layered panel for Liquid Glass and a rounded tonal navigation/panel composition for Material You Expressive.

### Task 5: Verify Windows UI

**Files:**
- Modify if needed: files changed in Tasks 1–4

- [x] **Step 1: Build production frontend**

Run: `npm run build`

Expected: TypeScript and Vite complete successfully.

- [x] **Step 2: Run Rust application tests**

Run: `cargo test -p nimbo-ui`

Expected: all Tauri backend tests pass.

- [x] **Step 3: Check Rust formatting**

Run: `cargo fmt --all -- --check`

Expected: no formatting differences.


