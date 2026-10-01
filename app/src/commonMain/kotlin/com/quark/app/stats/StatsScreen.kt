package com.quark.app.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.quark.app.browse.SectionTitle
import com.quark.app.browse.TopBar
import com.quark.app.i18n.Strings
import com.quark.app.i18n.strings
import com.quark.app.shell.shell
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.CircleButton
import com.quark.app.ui.ConfirmDialog
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.PillButton
import com.quark.app.ui.Placeholder
import com.quark.app.ui.QText
import com.quark.app.ui.bottomInset
import com.quark.core.stats.ListeningReport

/**
 * What the listening log says (`listen_stats.dart`): time and plays, the top
 * tracks, artists and albums, when in the day and week the music plays, and
 * the streaks — for the last week, month, year or all of it.
 */
@Composable
fun StatsScreen() {
    val shell = shell
    val s = strings
    val model = remember(shell.app) { shell.app.stats }
    val state by model.state.collectAsState()
    LaunchedEffect(model) { model.load((state as? StatsState.Ready)?.period ?: StatsPeriod.Month) }

    Column(Modifier.fillMaxSize()) {
        TopBar(s.statistics, onBack = { shell.navigator.pop() }) {
            CircleButton(Icons.Filled.DeleteSweep, {
                shell.dialogs.show { dismiss ->
                    ConfirmDialog(s.clearHistory, s.clearHistoryConfirm, s.delete, dismiss, danger = true) { model.clear() }
                }
            }, diameter = 36.dp, iconSize = 18.dp)
        }
        val period = (state as? StatsState.Ready)?.period ?: StatsPeriod.Month
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsPeriod.entries.forEach { option ->
                PillButton(option.label(s), { model.load(option) }, accent = option == period, height = 34.dp)
            }
        }
        when (val current = state) {
            StatsState.Loading -> Placeholder(s.loading, Modifier.fillMaxSize())
            is StatsState.Failed -> Placeholder(current.message, Modifier.fillMaxSize(), s.retry) { model.load(period) }
            is StatsState.Ready -> if (current.report.totalPlays == 0) {
                Placeholder(s.noHistory, Modifier.fillMaxSize())
            } else {
                Report(current.report)
            }
        }
    }
}

@Composable
private fun Report(report: ListeningReport) {
    val s = strings
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomInset + 20.dp),
    ) {
        item(key = "totals") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 560.dp) 2 else 4
                val cards = listOf(
                    s.listeningTime to duration(s, report.totalSeconds),
                    s.plays to report.totalPlays.toString(),
                    s.uniqueTracks to report.uniqueTracks.toString(),
                    s.uniqueArtists to report.uniqueArtists.toString(),
                    s.completion to "${(report.completionRate * 100).toInt()}%",
                    s.skips to report.skips.toString(),
                    s.streak to s.days(report.currentStreakDays),
                    s.longestStreak to s.days(report.longestStreakDays),
                    s.sessions to report.sessions.toString(),
                    s.averageSession to duration(s, report.averageSessionSeconds),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cards.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f)) }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        item(key = "hours") {
            SectionTitle(s.byHour)
            Bars(report.hourly, labels = List(24) { if (it % 6 == 0) it.toString() else "" })
        }
        item(key = "weekdays") {
            SectionTitle(s.byWeekday)
            Bars(report.byDayOfWeek, labels = s.weekdays)
        }

        if (report.topTracksByPlays.isNotEmpty()) {
            item(key = "tracks-title") { SectionTitle(s.topTracks) }
            itemsIndexed(report.topTracksByPlays, key = { index, track -> "track:${track.key}#$index" }) { index, track ->
                RankRow(index, track.title, track.artists.joinToString(", "), s.playsCount(track.plays))
            }
        }
        if (report.topArtistsByPlays.isNotEmpty()) {
            item(key = "artists-title") { SectionTitle(s.topArtists) }
            itemsIndexed(report.topArtistsByPlays, key = { index, artist -> "artist:${artist.name}#$index" }) { index, artist ->
                RankRow(index, artist.name, duration(s, artist.seconds), s.playsCount(artist.plays))
            }
        }
        if (report.topAlbums.isNotEmpty()) {
            item(key = "albums-title") { SectionTitle(s.topAlbums) }
            itemsIndexed(report.topAlbums, key = { index, album -> "album:${album.album}#$index" }) { index, album ->
                RankRow(index, album.album, album.artist, s.playsCount(album.plays))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    GlassSurface(RoundedCornerShape(Radius.card), modifier, Glass.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QText(value, Quark.type.heading, maxLines = 1)
            QText(label, Quark.type.label, color = Quark.colors.textMuted, maxLines = 1)
        }
    }
}

/** A bar per bucket, the busiest full height. */
@Composable
private fun Bars(values: List<Int>, labels: List<String>) {
    val color = Quark.accent.primary
    val track = Quark.colors.control
    val peak = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val count = values.size.coerceAtLeast(1)
            val slot = size.width / count
            val barWidth = slot * 0.7f
            values.forEachIndexed { index, value ->
                val left = index * slot + (slot - barWidth) / 2
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), CornerRadius(4f, 4f))
                val height = size.height * value / peak
                drawRoundRect(color, Offset(left, size.height - height), Size(barWidth, height), CornerRadius(4f, 4f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    QText(label, Quark.type.label, color = Quark.colors.textMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RankRow(index: Int, title: String, subtitle: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QText((index + 1).toString(), Quark.type.panelTitle, color = Quark.accent.primary, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f).widthIn(min = 0.dp)) {
            QText(title, Quark.type.trackTitle, maxLines = 1)
            QText(subtitle, Quark.type.trackSubtitle, color = Quark.colors.textSecondary, maxLines = 1)
        }
        QText(value, Quark.type.label, color = Quark.colors.textMuted)
    }
}

private fun duration(s: Strings, seconds: Long): String {
    val minutes = seconds / 60
    return s.hoursMinutes(minutes / 60, minutes % 60)
}

private fun StatsPeriod.label(s: Strings): String = when (this) {
    StatsPeriod.Week -> s.week
    StatsPeriod.Month -> s.month
    StatsPeriod.Year -> s.year
    StatsPeriod.All -> s.allTime
}
