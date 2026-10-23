package dev.hamweb.stayopen.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.hamweb.stayopen.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun gib(bytes: Long): String = String.format(Locale.getDefault(), "%.1f GiB", bytes.toDouble() / GIB)
fun time(timeMs: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMs))
fun dateTime(timeMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timeMs))

@Composable
fun Page(title: String, subtitle: String, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxHeight(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(Modifier.padding(top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineLarge)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
            }
            content()
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun Eyebrow(text: String) = Text(text, color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

@Composable
fun Badge(text: String, warning: Boolean = false) {
    val c = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(color = c.copy(alpha = .12f), shape = RoundedCornerShape(9.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = c, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Note(text: String, icon: ImageVector = Icons.Default.Info) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(19.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingAction(title: String, detail: String, icon: ImageVector = Icons.Default.Settings, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                Icon(icon, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MemoryRing(sample: MemorySample, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(sample.usedFraction, tween(500), label = "RAM reading")
    val ring = when (sample.pressure) {
        Pressure.HIGH -> MaterialTheme.colorScheme.error
        Pressure.ELEVATED -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(176.dp).semantics { contentDescription = "RAM in use: ${(sample.usedFraction * 100).toInt()} percent. Estimated pressure: ${sample.pressure.label}." }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            drawArc(track, -90f, 360f, false, style = Stroke(15.dp.toPx(), cap = StrokeCap.Round))
            if (fraction > 0) drawArc(ring, -90f, 360f * fraction, false, style = Stroke(15.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${(sample.usedFraction * 100).toInt()}%", style = MaterialTheme.typography.headlineLarge)
            Text("RAM in use", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun MemoryGraph(samples: List<MemorySample>, intervalSeconds: Int) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = if (samples.isEmpty()) "No RAM samples collected" else
        "${samples.size} real RAM samples. Latest usage ${(samples.last().usedFraction * 100).toInt()} percent. Gaps are unmonitored."
    if (samples.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
            Text("Start monitoring to collect your RAM history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Row(Modifier.fillMaxWidth().height(146.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("100%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp).semantics { contentDescription = description }) {
                for (i in 0..4) drawLine(grid.copy(alpha = .6f), Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1.dp.toPx())
                val start = samples.first().timeMs
                val duration = (samples.last().timeMs - start).coerceAtLeast(intervalSeconds * 1000L)
                fun point(s: MemorySample) = Offset(((s.timeMs - start).toFloat() / duration * size.width).coerceIn(0f, size.width), size.height * (1f - s.usedFraction))
                val path = Path()
                samples.forEachIndexed { i, s ->
                    val p = point(s)
                    if (i == 0 || s.timeMs - samples[i - 1].timeMs > intervalSeconds * 3_000L) path.moveTo(p.x, p.y)
                    else path.lineTo(p.x, p.y)
                }
                drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(line, 4.dp.toPx(), point(samples.last()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(time(samples.first().timeMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (samples.size == 1) "First reading" else time(samples.last().timeMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private object AppIconCache { val images = LruCache<String, ImageBitmap>(48) }

@Composable
fun AppIcon(packageName: String, label: String) {
    val context = LocalContext.current
    val icon by produceState(AppIconCache.images.get(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(72, 72, Bitmap.Config.ARGB_8888)
                    .asImageBitmap().also { AppIconCache.images.put(packageName, it) }
            }.getOrNull()
        }
    }
    Surface(Modifier.size(46.dp), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        if (icon != null) Image(icon!!, null, Modifier.fillMaxSize().padding(3.dp))
        else Box(contentAlignment = Alignment.Center) { Text(label.take(1).uppercase(), fontWeight = FontWeight.Bold) }
    }
}
