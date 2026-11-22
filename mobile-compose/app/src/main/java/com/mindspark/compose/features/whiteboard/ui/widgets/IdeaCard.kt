package com.mindspark.compose.features.whiteboard.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mindspark.compose.features.whiteboard.logic.CARD_HEIGHT
import com.mindspark.compose.features.whiteboard.logic.CARD_WIDTH
import com.mindspark.compose.features.whiteboard.logic.defaultIdeaCentre
import com.mindspark.compose.features.whiteboard.logic.ideaTopLeft
import com.mindspark.compose.features.whiteboard.model.Idea
import com.mindspark.compose.ui.theme.Ink

@Composable
fun IdeaCard(
    idea: Idea,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier,
    index: Int = 0
) {
    val center = if (idea.x != null && idea.y != null) {
        androidx.compose.ui.geometry.Offset(idea.x.toFloat(), idea.y.toFloat())
    } else {
        defaultIdeaCentre(index)
    }

    val topLeft = ideaTopLeft(center.x, center.y)

    Card(
        modifier = modifier
            .offset(x = topLeft.x.dp, y = topLeft.y.dp)
            .requiredSize(CARD_WIDTH.dp, CARD_HEIGHT.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Ink.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = idea.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (idea.category != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = idea.category.title,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val desc = idea.description ?: idea.text
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = "Votes",
                    tint = if (idea.voted) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${idea.voteCount}",
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "Comments",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
