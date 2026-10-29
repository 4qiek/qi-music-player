package com.qi.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qi.player.data.Song
import com.qi.player.ui.Group
import com.qi.player.ui.LibraryTab
import com.qi.player.ui.component.GroupRow
import com.qi.player.ui.component.NeuActionButton
import com.qi.player.ui.component.NeuInsetBox
import com.qi.player.ui.component.SectionLabel
import com.qi.player.ui.component.SongRow
import com.qi.player.ui.component.neuInset
import com.qi.player.ui.component.neuRaised
import com.qi.player.ui.component.tapGestures
import com.qi.player.ui.theme.QiColors
import com.qi.player.ui.theme.QiTheme
import com.qi.player.ui.theme.QiType

/**
 * 下拉曲库面板。
 *
 * 四个分类页签与草图一致：歌曲 / 歌手 / 专辑 / 歌单。
 * 歌手、专辑点进去会「下钻」成该分组下的歌曲列表，
 * 而不是跳到新页面——单面板内的层级切换，返回手势更短。
 */
@Composable
fun LibraryPanel(
    songs: List<Song>,
    totalCount: Int,
    scanning: Boolean,
    probing: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    tab: LibraryTab,
    onTabChange: (LibraryTab) -> Unit,
    albums: List<Group>,
    artists: List<Group>,
    playlists: List<Group>,
    favorites: Set<String>,
    currentUri: String?,
    onPlay: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onImportFolder: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QiTheme.colors
    var drilldown by remember { mutableStateOf<Group?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        // ---------------- 头部 ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("曲库", style = QiType.brand, color = colors.textMain)
                Text(
                    text = when {
                        scanning -> "正在扫描…"
                        probing -> "已收录 $totalCount 首 · 解析音频参数中"
                        else -> "已收录 $totalCount 首"
                    },
                    style = QiType.mono,
                    color = colors.textSub,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "收起曲库", tint = colors.textSub, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---------------- 搜索 ----------------
        NeuInsetBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSub, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = QiType.body.copy(color = colors.textMain),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isEmpty()) {
                        Text("搜索本地音乐…", style = QiType.body, color = colors.textSub.copy(alpha = 0.7f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---------------- 分类页签 ----------------
        if (drilldown == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryTab.entries.forEach { item ->
                    TabButton(
                        label = item.label,
                        active = item == tab,
                        modifier = Modifier.weight(1f),
                    ) { onTabChange(item) }
                }
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { drilldown = null },
                ) {
                    Text("← 返回", style = QiType.mono, color = colors.accent, modifier = Modifier.padding(4.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = drilldown!!.title,
                    style = QiType.listTitle,
                    color = colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---------------- 列表 ----------------
        val haptic = LocalHapticFeedback.current

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            val drill = drilldown
            when {
                drill != null -> {
                    itemsIndexed(drill.songs) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.uri == currentUri,
                            modifier = Modifier.tapGestures(
                                onTap = { onPlay(drill.songs, index) },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleFavorite(song)
                                },
                            ),
                        )
                    }
                }

                tab == LibraryTab.Songs -> {
                    if (songs.isEmpty() && !scanning) {
                        item { EmptyHint(onImportFolder) }
                    }
                    itemsIndexed(songs) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.uri == currentUri,
                            modifier = Modifier.tapGestures(
                                onTap = { onPlay(songs, index) },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleFavorite(song)
                                },
                            ),
                        )
                    }
                }

                tab == LibraryTab.Artists -> {
                    items(artists.size) { i ->
                        val g = artists[i]
                        GroupRow(
                            title = g.title,
                            subtitle = g.subtitle,
                            leading = Icons.Rounded.Person,
                            onClick = { drilldown = g },
                        )
                    }
                }

                tab == LibraryTab.Albums -> {
                    items(albums.size) { i ->
                        val g = albums[i]
                        GroupRow(
                            title = g.title,
                            subtitle = g.subtitle,
                            leading = Icons.Rounded.Album,
                            onClick = { drilldown = g },
                        )
                    }
                }

                else -> {
                    item { SectionLabel("我的歌单") }
                    items(playlists.size) { i ->
                        val g = playlists[i]
                        GroupRow(
                            title = g.title,
                            subtitle = g.subtitle,
                            leading = when {
                                g.key == "fav" -> Icons.Rounded.Favorite
                                g.key == "recent" -> Icons.Rounded.History
                                else -> Icons.Rounded.PlaylistPlay
                            },
                            onClick = { drilldown = g },
                        )
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        NeuActionButton(
                            text = if (favorites.isEmpty()) "长按歌曲可加入喜欢" else "已喜欢 ${favorites.size} 首",
                            leading = Icons.Rounded.Favorite,
                            accent = false,
                            onClick = { },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = QiTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(36.dp)
            .then(
                if (active) {
                    Modifier
                        .clip(shape)
                        .background(colors.inset)
                        .neuInset(colors)
                } else {
                    Modifier.neuRaised(shape, colors, 4.dp)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (active) colors.textMain else colors.textSub,
            style = QiType.body,
        )
    }
}

@Composable
private fun EmptyHint(onImportFolder: () -> Unit) {
    val colors = QiTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("没有找到音乐", style = QiType.lyric, color = colors.textSub)
        Spacer(Modifier.height(8.dp))
        Text(
            "如果音乐在系统未收录的目录里，可以手动指定文件夹",
            fontSize = 11.sp,
            color = colors.textSub.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(16.dp))
        NeuActionButton(
            text = "选择音乐文件夹",
            leading = Icons.Rounded.FolderOpen,
            accent = true,
            modifier = Modifier.width(220.dp),
            onClick = onImportFolder,
        )
    }
}
