package com.airware.loader.ui

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/*
 * Экспрессивные формы.
 *
 * Material 3 Expressive вводит набор «материальных форм» (MaterialShapes) — печенья,
 * клеверы, солнца и т.п. — и морфинг между ними. Здесь:
 *  - мост RoundedPolygon -> Compose Shape (чтобы клипать контейнеры этими формами);
 *  - ExpressiveActivityIndicator — индикатор, который непрерывно перетекает между
 *    двумя формами и вращается (фирменное «живое» движение Expressive вместо скучного
 *    крутящегося колечка).
 */

// --- RoundedPolygon -> Compose Shape ----------------------------------------

/** Превращает материальную форму (RoundedPolygon) в Compose [Shape] для clip/background. */
fun RoundedPolygon.asComposeShape(): Shape = PolygonShape(this)

private class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toPath()
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val scale = minOf(
            size.width / bounds.width().coerceAtLeast(0.0001f),
            size.height / bounds.height().coerceAtLeast(0.0001f),
        )
        val matrix = Matrix().apply {
            postTranslate(-bounds.centerX(), -bounds.centerY())
            postScale(scale, scale)
            postTranslate(size.width / 2f, size.height / 2f)
        }
        path.transform(matrix)
        return Outline.Generic(path.asComposePath())
    }
}

/** Удобные пресеты материальных форм. */
object ExpressiveShapeTokens {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val cookie: Shape get() = MaterialShapes.Cookie9Sided.asComposeShape()

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val sunny: Shape get() = MaterialShapes.Sunny.asComposeShape()

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val clover: Shape get() = MaterialShapes.Clover4Leaf.asComposeShape()

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val cookie4: Shape get() = MaterialShapes.Cookie4Sided.asComposeShape()

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val cookie6: Shape get() = MaterialShapes.Cookie6Sided.asComposeShape()
}

// --- Морфящийся индикатор активности ----------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveActivityIndicator(
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.onPrimary,
    morphMillis: Int = 850,
    spinMillis: Int = 4000,
) {
    val morph = remember { Morph(MaterialShapes.Sunny, MaterialShapes.Cookie9Sided) }
    val transition = rememberInfiniteTransition(label = "indicator")

    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(morphMillis), RepeatMode.Reverse),
        label = "morph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(spinMillis, easing = LinearEasing)),
        label = "spin",
    )

    androidx.compose.foundation.Canvas(modifier = modifier.size(diameter)) {
        val path = morph.toPath(progress)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val scale = minOf(
            this.size.width / bounds.width().coerceAtLeast(0.0001f),
            this.size.height / bounds.height().coerceAtLeast(0.0001f),
        )
        val matrix = Matrix().apply {
            postTranslate(-bounds.centerX(), -bounds.centerY())
            postScale(scale, scale)
            postTranslate(this@Canvas.size.width / 2f, this@Canvas.size.height / 2f)
        }
        path.transform(matrix)
        rotate(rotation) {
            drawPath(path.asComposePath(), color = color)
        }
    }
}
