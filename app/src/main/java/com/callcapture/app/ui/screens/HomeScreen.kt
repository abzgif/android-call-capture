package com.callcapture.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callcapture.app.data.db.RecordingEntity
import com.callcapture.app.ui.components.CallTypeBadge
import com.callcapture.app.ui.components.PlayerControlsBar
import com.callcapture.app.ui.theme.AccentOrange
import com.callcapture.app.ui.viewmodel.FilterTab
import com.callcapture.app.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val currentPlaying by viewModel.currentPlayingRecording.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosMs by viewModel.currentPositionMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var selectedRecordingForDetail by remember { mutableStateOf<RecordingEntity?>(null) }

    // Multi-selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // Intercept Back Press: dismiss selection mode or search box first before closing the app
    BackHandler(enabled = isSelectionMode || isSearchActive) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedIds.clear()
        } else if (isSearchActive) {
            isSearchActive = false
            viewModel.updateSearchQuery("")
        }
    }

    // Auto-exit selection mode if no recordings remain
    if (isSelectionMode && recordings.isEmpty()) {
        isSelectionMode = false
        selectedIds.clear()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
            if (isSelectionMode) {
                // Multi-select Action Bar
                val allSelected = recordings.isNotEmpty() && selectedIds.size == recordings.size
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds.clear()
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedIds.size} selected",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        // Select All / Deselect All Button
                        IconButton(onClick = {
                            if (allSelected) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(recordings.map { it.id })
                            }
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All"
                            )
                        }

                        // Share selected recordings
                        IconButton(
                            onClick = {
                                val selectedRecordings = recordings.filter { it.id in selectedIds }
                                viewModel.createMultipleShareIntent(selectedRecordings)?.let { shareIntent ->
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Selected Recordings"))
                                }
                            },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share selected")
                        }

                        // Delete selected recordings
                        IconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    showBatchDeleteConfirm = true
                                }
                            },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search by name, number, or app...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.updateSearchQuery("")
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close search")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Call Capture",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    actions = {
                        var topMenuExpanded by remember { mutableStateOf(false) }

                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onNavigateToPermissions) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = "Permissions")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                        }
                        Box {
                            IconButton(onClick = { topMenuExpanded = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = topMenuExpanded,
                                onDismissRequest = { topMenuExpanded = false }
                            ) {
                                if (recordings.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Select Recordings") },
                                        leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                        onClick = {
                                            topMenuExpanded = false
                                            isSelectionMode = true
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Clean Empty / Short Recordings") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        topMenuExpanded = false
                                        viewModel.deleteAllEmptyRecordings()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // Filter Tabs
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FilterTab.entries.toTypedArray()) { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(tab.title) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Recordings List or Empty View
            if (recordings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No recordings match your search"
                            else "No calls recorded yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Calls from Phone, WhatsApp, and Messenger will automatically appear here once active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomListPadding by animateDpAsState(
                    targetValue = if (currentPlaying != null) 210.dp + bottomNavPadding else bottomNavPadding + 16.dp,
                    label = "bottomListPadding"
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = bottomListPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recordings, key = { it.id }) { rec ->
                        val isSelected = selectedIds.contains(rec.id)
                        RecordingCardItem(
                            recording = rec,
                            isPlayingThis = currentPlaying?.id == rec.id && isPlaying,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelection = {
                                if (isSelected) {
                                    selectedIds.remove(rec.id)
                                    if (selectedIds.isEmpty()) {
                                        isSelectionMode = false
                                    }
                                } else {
                                    selectedIds.add(rec.id)
                                }
                            },
                            onLongPressSelect = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedIds.clear()
                                    selectedIds.add(rec.id)
                                }
                            },
                            onPlayPause = { viewModel.playRecording(rec) },
                            onToggleStar = { viewModel.toggleStar(rec) },
                            onDetailClick = { selectedRecordingForDetail = rec },
                            onShare = {
                                viewModel.createShareIntent(rec)?.let { shareIntent ->
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Call Recording"))
                                }
                            },
                            onDelete = { viewModel.deleteRecording(rec) }
                        )
                    }
                }
            }
        }
    }

    // Mini Player Overlay (anchored directly to bottom without reserving an empty black space in Scaffold)
    AnimatedVisibility(
        visible = currentPlaying != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        currentPlaying?.let { rec ->
            PlayerControlsBar(
                recording = rec,
                isPlaying = isPlaying,
                currentPositionMs = currentPosMs,
                totalDurationMs = rec.durationMs,
                playbackSpeed = playbackSpeed,
                onPlayPause = { viewModel.playRecording(rec) },
                onSeek = { viewModel.seekTo(it) },
                onReplay5s = { viewModel.replay5s() },
                onChangeSpeed = { viewModel.togglePlaybackSpeed() },
                onClose = { viewModel.stopPlayback() }
            )
        }
    }

    // Confirmation dialog for batch deletion
    if (showBatchDeleteConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Delete $count Recording${if (count > 1) "s" else ""}?") },
            text = { Text("Are you sure you want to delete $count selected recording${if (count > 1) "s" else ""}? This will permanently remove the audio file${if (count > 1) "s" else ""} from your device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = recordings.filter { it.id in selectedIds }
                        viewModel.deleteSelectedRecordings(toDelete)
                        selectedIds.clear()
                        isSelectionMode = false
                        showBatchDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedRecordingForDetail?.let { rec ->
        RecordingDetailDialog(
            recording = rec,
            onDismiss = { selectedRecordingForDetail = null },
            onSaveNotes = { newNotes ->
                viewModel.updateNotes(rec.id, newNotes)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingCardItem(
    recording: RecordingEntity,
    isPlayingThis: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onLongPressSelect: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleStar: () -> Unit,
    onDetailClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val dateStr = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        .format(Date(recording.timestamp))
    val durationSeconds = (recording.durationMs / 1000).toInt()
    val durationStr = String.format(Locale.US, "%02d:%02d", durationSeconds / 60, durationSeconds % 60)
    val sizeMb = String.format(Locale.US, "%.1f MB", recording.fileSizeBytes / (1024f * 1024f))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else {
                        onDetailClick()
                    }
                },
                onLongClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else {
                        onLongPressSelect()
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection Checkbox or Play/Pause Button
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else {
                FilledIconButton(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlayingThis) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingThis) "Pause" else "Play",
                        tint = if (isPlayingThis) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                val displayTitle = when {
                    !recording.contactName.isNullOrBlank() -> recording.contactName
                    !recording.phoneNumber.isNullOrBlank() -> recording.phoneNumber
                    recording.title.contains("Phone Call Call") -> "Unknown Caller"
                    else -> recording.title
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CallTypeBadge(callType = recording.callType)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$dateStr • $durationStr • $sizeMb",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (recording.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📝 ${recording.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isSelectionMode) {
                // Star indicator if starred
                if (recording.isStarred) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Starred",
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp).padding(end = 4.dp)
                    )
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (recording.isStarred) "Unstar Recording" else "Star Recording") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (recording.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    tint = if (recording.isStarred) AccentOrange else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleStar()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Details & Notes") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDetailClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Recording") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
