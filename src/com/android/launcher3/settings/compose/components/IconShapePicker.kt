/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.android.launcher3.shapes.IconShapeModel
import com.android.launcher3.shapes.ShapesProvider

/** The pref ThemeManager reads the icon shape from; "" keeps the system icon mask. */
const val ICON_SHAPE_PREF_KEY = "icon_shape_model"

/** A Compose shape from a ShapesProvider path, which is drawn in a 100 x 100 box. */
private class PathShape(private val pathString: String) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = PathParser.createPathFromPathData(pathString)
        path.transform(android.graphics.Matrix().apply {
            setScale(size.width / 100f, size.height / 100f)
        })
        return Outline.Generic(path.asComposePath())
    }
}

fun iconShapeFromPath(pathString: String): Shape = PathShape(pathString)

/** The shape of [key], the circle for the system default or an unknown key. */
fun iconShapeForKey(key: String): Shape =
    ShapesProvider.iconShapes.firstOrNull { it.key == key }
        ?.let { iconShapeFromPath(it.pathString) } ?: CircleShape

@Composable
fun IconShapePicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val colors = MaterialTheme.colorScheme
    // null is the system default (no override)
    val options = remember {
        ArrayList<IconShapeModel?>().apply {
            add(null)
            for (model in ShapesProvider.iconShapes) add(model)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { model ->
            val key = model?.key ?: ""
            val isSelected = key == selected
            val shape = remember(key) {
                model?.let { iconShapeFromPath(it.pathString) } ?: CircleShape
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (isSelected) Modifier.border(
                            2.dp, colors.primary, RoundedCornerShape(16.dp)
                        ) else Modifier
                    )
                    .clickable {
                        haptic(Haptic.TICK)
                        onSelect(key)
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(shape)
                        .background(
                            if (isSelected) colors.primary else colors.primaryContainer
                        )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = model?.let { stringResource(it.titleId) } ?: "Default",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
