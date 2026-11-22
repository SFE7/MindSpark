package com.mindspark.compose.features.whiteboard.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import com.mindspark.compose.features.whiteboard.logic.colorFromHex
import com.mindspark.compose.features.whiteboard.model.DrawingElement
import com.mindspark.compose.features.whiteboard.model.ShapeElement
import com.mindspark.compose.features.whiteboard.model.StrokeElement
import com.mindspark.compose.features.whiteboard.model.UnknownElement

@Composable
fun DrawingLayer(
    elements: List<DrawingElement>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density

    Canvas(modifier = modifier.fillMaxSize()) {
        // Scale by density once so all DrawingElement coordinates operate in world units (dp)
        scale(scaleX = density, scaleY = density, pivot = Offset.Zero) {
            for (element in elements) {
                when (element) {
                    is StrokeElement -> {
                        val color = colorFromHex(element.color).copy(alpha = element.opacity)
                        val strokeStyle = Stroke(
                            width = element.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )

                        if (element.points.isEmpty()) continue

                        val ox = element.offsetX
                        val oy = element.offsetY

                        if (element.points.size == 1) {
                            // RF4: Single point tap draws a dot of stroke width
                            val p = element.points[0]
                            drawCircle(
                                color = color,
                                radius = element.width / 2f,
                                center = Offset(p.x + ox, p.y + oy)
                            )
                        } else {
                            val path = Path().apply {
                                val first = element.points[0]
                                moveTo(first.x + ox, first.y + oy)
                                for (i in 1 until element.points.size) {
                                    val pt = element.points[i]
                                    lineTo(pt.x + ox, pt.y + oy)
                                }
                            }
                            drawPath(path = path, color = color, style = strokeStyle)
                        }
                    }

                    is ShapeElement -> {
                        if (element.width <= 0f || element.height <= 0f) continue
                        val color = colorFromHex(element.color).copy(alpha = element.opacity)
                        val strokeStyle = Stroke(
                            width = element.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        val ox = element.offsetX
                        val oy = element.offsetY
                        val x = element.x + ox
                        val y = element.y + oy

                        when (element.kind) {
                            "rectangle" -> {
                                drawRect(
                                    color = color,
                                    topLeft = Offset(x, y),
                                    size = Size(element.width, element.height),
                                    style = strokeStyle
                                )
                            }
                            "circle" -> {
                                drawOval(
                                    color = color,
                                    topLeft = Offset(x, y),
                                    size = Size(element.width, element.height),
                                    style = strokeStyle
                                )
                            }
                            "triangle" -> {
                                val path = Path().apply {
                                    moveTo(x + element.width / 2f, y)
                                    lineTo(x, y + element.height)
                                    lineTo(x + element.width, y + element.height)
                                    close()
                                }
                                drawPath(path = path, color = color, style = strokeStyle)
                            }
                        }
                    }

                    is UnknownElement -> {
                        // RF4: safely skipped, never throws
                    }
                }
            }
        }
    }
}
