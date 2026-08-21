/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.widget;

import android.app.WallpaperColors;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.content.theming.ThemeStyle;
import android.util.SparseIntArray;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.monet.ColorScheme;

import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme;

import javax.inject.Inject;

/**
 * Produces {@link ColorsOverride} instances that retint the launcher preview surface to match a
 * previewing color palette from the picker.
 *
 * <p>The AOSP-side version of this class shipped as a stub (the real implementation lived in
 * Google's downstream {@code com.google.android.apps.nexuslauncher.widget.LocalWallpaperColorsExtractor}).
 * The stub meant preview color updates ({@code MESSAGE_ID_UPDATE_COLOR}) reached the launcher but
 * were discarded — the widget, themed icons and search bar only recolored after the user hit
 * "Apply", which mutates the real system theme.
 *
 * <p>This implementation restores the downstream behavior: it generates the full {@code system_*}
 * colour palette (both light and dark shades) using the same {@link ColorScheme} + tonal-palette
 * pipeline the picker's own {@code MaterialColorsGenerator} uses, then installs it via
 * {@link RemoteViews.ColorResources#createWithOverlay(Context, SparseIntArray)} — a Fabricated
 * Runtime Resource Overlay (FRRO) targeting {@code android}, which retints at the native asset
 * table so both direct {@code getColor()} lookups and attribute-based theme resolution pick up the
 * override.
 *
 * <ul>
 *     <li><b>Widgets</b> — retinted via {@link AppWidgetHostView#setColorResources}, which is the
 *         platform API for widget colour overrides inside remote-view host processes.</li>
 *     <li><b>Icons, search bar, folders, everything else the launcher preview inflates</b> —
 *         retinted by installing the FRRO loader on the preview context's {@code Resources} via
 *         {@link ColorsOverride#applyTo(Context)}.</li>
 * </ul>
 */
public class LocalColorExtractor {

    @Inject
    public LocalColorExtractor() {
    }

    /**
     * Builds a preview color override from {@link WallpaperColors}.
     */
    @Nullable
    public ColorsOverride applyColorsOverride(Context base, WallpaperColors colors) {
        if (base == null || colors == null) return null;
        SparseIntArray mapping = generateColorsOverride(colors);
        if (mapping == null || mapping.size() == 0) return null;
        return applyColorsOverride(base, mapping);
    }

    /**
     * Generates a {@link SparseIntArray} of {@code system_*} resource-id → ARGB color entries from
     * the given {@link WallpaperColors}.
     */
    @Nullable
    public SparseIntArray generateColorsOverride(WallpaperColors colors) {
        if (colors == null) return null;
        return buildColorMap(colors, /* seed= */ 0, /* hasSeed= */ false,
                /* style= */ ThemeStyle.TONAL_SPOT, /* isDarkMode= */ false);
    }

    /**
     * Wraps a pre-computed {@link SparseIntArray} of {@code system_*} resource-id → color mappings
     * so the preview surface uses those colors instead of the real system palette. Used by the
     * picker's legacy update path (before the theme-service seed+style protocol) which computed
     * the palette itself and shipped the raw table via {@code KEY_COLOR_RESOURCE_IDS} /
     * {@code KEY_COLOR_VALUES}.
     *
     * <p>The override is applied to {@code base} in-place (via {@link ColorsOverride#applyTo
     * (Context)}) so that the {@code ContextThemeWrapper} the caller hands to
     * {@code LauncherPreviewRenderer} resolves the overridden {@code system_*} colours for icons,
     * the search bar and folders.
     */
    @Nullable
    public ColorsOverride applyColorsOverride(Context base, SparseIntArray override) {
        if (base == null || override == null || override.size() == 0) return null;
        RemoteViews.ColorResources resources =
                RemoteViews.ColorResources.createWithOverlay(base, override);
        if (resources == null) return null;
        ColorsOverrideImpl colorsOverride = new ColorsOverrideImpl(resources, override);
        colorsOverride.applyTo(base);
        return colorsOverride;
    }

    /**
     * Builds a color override from a Material You seed + style. This is the seed+style path used by
     * the picker's theme-service protocol.
     *
     * <p>Delegates to the platform's {@link RemoteViews.ColorResources#createWithOverlay(Context,
     * int[], int)}, which asks {@link android.app.ThemeManager} to generate the dynamic color
     * overlay. That produces the correct {@code system_*_light}/{@code system_*_dark} day/night
     * variants, so themed icons resolve {@code themed_icon_color} (which aliases
     * {@code system_accent1_700} in light and {@code system_accent1_200} in dark) to the previewing
     * colour for whichever night mode the preview context is in.
     */
    @Nullable
    public ColorsOverride applyColorOverlay(@NonNull Context base, @NonNull int[] seedColors,
            int style) {
        if (base == null || seedColors.length == 0) return null;
        RemoteViews.ColorResources resources =
                RemoteViews.ColorResources.createWithOverlay(base, seedColors, style);
        if (resources == null) return null;
        ColorsOverrideImpl colorsOverride =
                new ColorsOverrideImpl(resources, resources.getColorMapping());
        colorsOverride.applyTo(base);
        return colorsOverride;
    }

    /**
     * Builds the complete {@code system_*} resource-id → ARGB color mapping, mirroring the picker's
     * {@code MaterialColorsGenerator}. Produces light and dark schemes and writes out every neutral
     * / accent / named dynamic / fixed / custom color (plus the {@code _dark} and {@code _light}
     * variants for the non-fixed palettes), so that any resource name the preview inflates —
     * regardless of its night-mode variant — resolves to the previewing palette.
     */
    private static SparseIntArray buildColorMap(WallpaperColors colors, int seed, boolean hasSeed,
            int style, boolean isDarkMode) {
        ColorScheme light;
        ColorScheme dark;
        if (hasSeed) {
            light = new ColorScheme(seed, /* darkTheme= */ false, style);
            dark = new ColorScheme(seed, /* darkTheme= */ true, style);
        } else {
            light = new ColorScheme(colors, /* darkTheme= */ false, style);
            dark = new ColorScheme(colors, /* darkTheme= */ true, style);
        }
        DynamicScheme lightMaterial = light.getMaterialScheme();
        DynamicScheme darkMaterial = dark.getMaterialScheme();
        android.content.res.Resources sysRes = android.content.res.Resources.getSystem();

        SparseIntArray out = new SparseIntArray();
        addPalette(out, sysRes, com.android.systemui.monet.DynamicColors.getAllNeutralPalette(),
                lightMaterial, darkMaterial, /* isFixed= */ false, isDarkMode);
        addPalette(out, sysRes, com.android.systemui.monet.DynamicColors.getAllAccentPalette(),
                lightMaterial, darkMaterial, /* isFixed= */ false, isDarkMode);
        addPalette(out, sysRes, com.android.systemui.monet.DynamicColors.getAllDynamicColorsMapped(),
                lightMaterial, darkMaterial, /* isFixed= */ false, isDarkMode);
        addPalette(out, sysRes, com.android.systemui.monet.DynamicColors.getFixedColorsMapped(),
                lightMaterial, darkMaterial, /* isFixed= */ true, isDarkMode);
        addPalette(out, sysRes, com.android.systemui.monet.DynamicColors.getCustomColorsMapped(),
                lightMaterial, darkMaterial, /* isFixed= */ false, isDarkMode);
        return out;
    }

    private static void addPalette(SparseIntArray out, android.content.res.Resources sysRes,
            java.util.List<android.util.Pair<String, com.google.ux.material.libmonet.dynamiccolor.DynamicColor>> palette,
            DynamicScheme lightMaterial, DynamicScheme darkMaterial,
            boolean isFixed, boolean isDarkMode) {
        for (android.util.Pair<String, com.google.ux.material.libmonet.dynamiccolor.DynamicColor> entry : palette) {
            String name = entry.first;
            com.google.ux.material.libmonet.dynamiccolor.DynamicColor color = entry.second;
            if (isFixed) {
                putSystemColor(out, sysRes, name, 0xff000000 | color.getArgb(lightMaterial));
            } else {
                putSystemColor(out, sysRes, name,
                        0xff000000 | color.getArgb(isDarkMode ? darkMaterial : lightMaterial));
                putSystemColor(out, sysRes, name + "_dark",
                        0xff000000 | color.getArgb(darkMaterial));
                putSystemColor(out, sysRes, name + "_light",
                        0xff000000 | color.getArgb(lightMaterial));
            }
        }
    }

    private static void putSystemColor(SparseIntArray out, android.content.res.Resources sysRes,
            String name, int value) {
        int resId = sysRes.getIdentifier("android:color/system_" + name, null, null);
        if (resId != 0) out.put(resId, value);
    }

    /**
     * {@link ColorsOverride} backed by a {@link RemoteViews.ColorResources} (a
     * {@link android.content.res.loader.ResourcesLoader} overriding the {@code system_*} palette at
     * the native asset table). Applies to widgets via {@link AppWidgetHostView#setColorResources}
     * and to any other context by installing the loader on its Resources.
     */
    private static final class ColorsOverrideImpl implements ColorsOverride {

        private final RemoteViews.ColorResources mColorResources;
        private final SparseIntArray mColorMapping;

        ColorsOverrideImpl(RemoteViews.ColorResources colorResources,
                SparseIntArray colorMapping) {
            mColorResources = colorResources;
            mColorMapping = colorMapping;
        }

        @Override
        public void applyTo(Context context) {
            if (context == null || mColorResources == null) return;
            mColorResources.apply(context);
        }

        @Override
        public void applyTo(AppWidgetHostView widget) {
            if (widget == null || mColorMapping == null) return;
            widget.setColorResources(mColorMapping);
        }
    }
}
