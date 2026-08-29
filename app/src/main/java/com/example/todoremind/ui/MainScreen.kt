@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.todoremind.ui

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.todoremind.R
import com.example.todoremind.data.DailyReminder
import com.example.todoremind.data.Todo
import com.example.todoremind.util.AppBus
import com.example.todoremind.util.Fmt
import java.util.Calendar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets

sealed interface Sheet {
    data class Todo(val todo: com.example.todoremind.data.Todo? = null) : Sheet
    data class Daily(val daily: DailyReminder? = null) : Sheet
}

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val todos by vm.todos.collectAsStateWithLifecycle()
    val dailies by vm.dailies.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var doneExpanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { AppBus.add.collect { sheet = Sheet.Todo() } }

    val active = todos.filter { !it.done }
        .sortedWith(compareBy<Todo, Long?>(nullsLast<Long>()) { it.dueTime }.thenBy { it.createdAt })
    val done = todos.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheet = Sheet.Todo() },
                expanded = true,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.new_todo)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { PermissionBanners() }

            item { SectionHeader(stringResource(R.string.section_tasks) + if (active.isNotEmpty()) " · ${active.size}" else "") }
            if (active.isEmpty()) item { EmptyHint(R.string.empty_tasks) }
            items(active, key = { it.id }) { t ->
                TodoCard(t, onCheck = { vm.toggleDone(t) }, onClick = { sheet = Sheet.Todo(t) })
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.section_daily)) }
                    FilledTonalIconButton(
                        onClick = { sheet = Sheet.Daily() },
                        shape = MaterialTheme.shapes.medium
                    ) { Icon(Icons.Rounded.Add, stringResource(R.string.add_daily)) }
                }
            }
            if (dailies.isEmpty()) item { EmptyHint(R.string.empty_daily) }
            items(dailies, key = { "d" + it.id }) { d ->
                DailyCard(d, onToggle = { vm.toggleDaily(d) }, onClick = { sheet = Sheet.Daily(d) })
            }

            if (done.isNotEmpty()) {
                item(key = "doneHeader") {
                    val angle by animateFloatAsState(
                        if (doneExpanded) 180f else 0f,
                        label = "chevron"
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .combinedClickable(onClick = { doneExpanded = !doneExpanded })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            stringResource(R.string.section_done) + " · ${done.size}",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(28.dp)
                                .rotate(angle)
                        )
                    }
                }
                if (doneExpanded) {
                    items(done, key = { "done" + it.id }) { t ->
                        TodoCard(
                            t,
                            onCheck = { vm.toggleDone(t) },
                            onClick = { sheet = Sheet.Todo(t) })
                    }
                }
            }
        }
    }

    when (val s = sheet) {
        is Sheet.Todo -> TodoSheet(
            s.todo,
            onSave = { text, due -> vm.saveTodo(s.todo?.id, text, due); sheet = null },
            onDelete = s.todo?.let { t -> { vm.deleteTodo(t); sheet = null } },
            onDismiss = { sheet = null }
        )

        is Sheet.Daily -> DailySheet(
            s.daily,
            onSave = { text, h, m, en -> vm.saveDaily(s.daily?.id, text, h, m, en); sheet = null },
            onDelete = s.daily?.let { d -> { vm.deleteDaily(d); sheet = null } },
            onDismiss = { sheet = null }
        )

        null -> {}
    }
}

/* ---------- мелкие компоненты ---------- */

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(top = 14.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun EmptyHint(res: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.NotificationsActive,
                null,
                Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                stringResource(res),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RoundCheck(checked: Boolean, onToggle: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.4f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "check"
    )
    Box(
        Modifier
            .size(27.dp)
            .clip(CircleShape)
            .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
            .then(
                if (!checked) Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                else Modifier
            )
            .combinedClickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(
            Icons.Rounded.Check, null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(18.dp)
                .scale(scale)
        )
    }
}

@Composable
private fun TodoCard(t: Todo, onCheck: () -> Unit, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val overdue = !t.done && t.dueTime != null && t.dueTime < System.currentTimeMillis()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                if (t.done) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoundCheck(checked = t.done, onToggle = onCheck)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    t.text, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        if (overdue) Icons.Rounded.NotificationsActive else Icons.Outlined.Event,
                        null, Modifier.size(15.dp),
                        tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when {
                            t.dueTime == null -> stringResource(R.string.no_time)
                            overdue -> stringResource(R.string.overdue) + " · " + Fmt.due(ctx, t.dueTime)
                            else -> Fmt.due(ctx, t.dueTime)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyCard(d: DailyReminder, onToggle: () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (d.enabled) 1f else 0.45f))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.Schedule, null, Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    d.text, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(
                        R.string.daily_meta,
                        String.format("%02d:%02d", d.hour, d.minute)
                    ) +
                            if (!d.enabled) " · " + stringResource(R.string.w_off) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            Switch(checked = d.enabled, onCheckedChange = { onToggle() })
        }
    }
}

/* ---------- баннеры надёжности ---------- */

@Composable
private fun <T> rememberOnResume(value: () -> T): T {
    var v by remember { mutableStateOf(value()) }
    val observer = remember {
        LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) v = value()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return v
}

@Composable
private fun PermissionBanners() {
    val ctx = LocalContext.current
    val notifEnabled =
        rememberOnResume { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val batteryOk = rememberOnResume {
        val pm = ctx.getSystemService(PowerManager::class.java)
        Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val fsiOk = rememberOnResume {
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java)
            .canUseFullScreenIntent()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (Build.VERSION.SDK_INT >= 33 && !notifEnabled) Banner(R.string.banner_notif) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                )
            } catch (_: Exception) {
            }
        }
        if (!batteryOk) Banner(R.string.banner_battery) {
            try {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        "package:${ctx.packageName}".toUri()
                    )
                )
            } catch (_: Exception) {
            }
        }
        if (!fsiOk) Banner(R.string.banner_fsi) {
            try {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        "package:${ctx.packageName}".toUri()
                    )
                )
            } catch (_: Exception) {
            }
        }
    }
}

@Composable
private fun Banner(textRes: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
            .combinedClickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Outlined.Warning,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                stringResource(textRes), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/* ---------- лист задачи ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoSheet(
    initial: Todo?,
    onSave: (String, Long?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var remind by remember { mutableStateOf(initial?.dueTime != null) }
    var due by remember { mutableStateOf(initial?.dueTime ?: Fmt.nextFullHour()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val past = remind && due <= System.currentTimeMillis()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (initial == null) stringResource(R.string.new_todo) else stringResource(R.string.edit_todo),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(stringResource(R.string.field_text)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .heightIn(min = 90.dp),
                shape = MaterialTheme.shapes.medium, minLines = 2
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Event,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.reminder),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(checked = remind, onCheckedChange = { remind = it })
            }
            AnimatedVisibility(
                remind,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 22.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InputChip(
                            selected = true, onClick = { showDate = true },
                            label = { Text(Fmt.dueDate(due)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Event,
                                    null,
                                    Modifier.size(18.dp)
                                )
                            },
                            shape = MaterialTheme.shapes.medium
                        )
                        InputChip(
                            selected = true, onClick = { showTime = true },
                            label = { Text(Fmt.time(due)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    null,
                                    Modifier.size(18.dp)
                                )
                            },
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(
                            onClick = { due = System.currentTimeMillis() + 3_600_000L },
                            label = { Text(stringResource(R.string.in_hour)) }
                        )
                        SuggestionChip(
                            onClick = { due = Fmt.tomorrowAt(9, 0) },
                            label = { Text(stringResource(R.string.tomorrow_9)) }
                        )
                    }
                    if (past) Text(
                        stringResource(R.string.time_passed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 8.dp)
                    .imePadding()  // <-- imePadding только здесь
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete, shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(text.trim(), if (remind) due else null) },
                    enabled = text.isNotBlank() && !past,
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = due)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { due = Fmt.withDate(due, it) }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDate = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = dateState) }
    }
    if (showTime) {
        val c = Calendar.getInstance().apply { timeInMillis = due }
        val timeState = rememberTimePickerState(
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            is24Hour = true
        )
        TimePickerDialog(
            onDismiss = { showTime = false },
            onConfirm = {
                due = Fmt.withTime(due, timeState.hour, timeState.minute); showTime = false
            }
        ) { TimePicker(state = timeState) }
    }
}

/* ---------- лист ежедневного напоминания ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySheet(
    initial: DailyReminder?,
    onSave: (String, Int, Int, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var hour by remember { mutableStateOf(initial?.hour ?: 9) }
    var minute by remember { mutableStateOf(initial?.minute ?: 0) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var showTime by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (initial == null) stringResource(R.string.new_daily) else stringResource(R.string.edit_daily),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(stringResource(R.string.field_text)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                shape = MaterialTheme.shapes.medium
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.daily_time),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                FilledTonalButton(
                    onClick = { showTime = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(String.format("%02d:%02d", hour, minute))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.daily_enabled),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 8.dp)
                    .imePadding()  // <-- imePadding только здесь
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete, shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(text.trim(), hour, minute, enabled) },
                    enabled = text.isNotBlank(),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    if (showTime) {
        val timeState =
            rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        TimePickerDialog(
            onDismiss = { showTime = false },
            onConfirm = { hour = timeState.hour; minute = timeState.minute; showTime = false }
        ) { TimePicker(state = timeState) }
    }
}

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) } },
        text = content
    )
}
