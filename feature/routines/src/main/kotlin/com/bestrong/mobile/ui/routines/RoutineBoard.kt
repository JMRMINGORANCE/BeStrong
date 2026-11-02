package com.bestrong.mobile.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.Routine
import com.bestrong.core.training.RoutineTime
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.components.uiLanguage
import kotlin.math.roundToInt

/** Ejercicio que se arrastra desde la biblioteca y dónde está el dedo (en coordenadas de la ventana). */
data class BoardDrag(val exercise: Exercise, val position: Offset)

/**
 * Estado del tablero: lo que se arrastra y dónde está la rutina, para saber si se suelta encima.
 */
class BoardState {
    var drag by mutableStateOf<BoardDrag?>(null)
    var dropZone by mutableStateOf(Rect.Zero)
    val overDrop: Boolean get() = drag?.let { dropZone.contains(it.position) } == true
}

/**
 * Biblioteca del editor en tablero (Fold desplegado): búsqueda sin tildes, filtros por músculo y equipo y la lista.
 * Tocar «+» añade el ejercicio a la rutina; mantenerlo pulsado y arrastrarlo a la derecha también.
 */
@Composable
fun BoardLibrary(
    exercises: List<Exercise>,
    routine: Routine,
    board: BoardState,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalContext.current.uiLanguage()
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }
    val shown = remember(exercises, query, muscle, equipment, lang) {
        ExerciseLibrary.search(exercises, query, muscle, equipment) { ExerciseLibrary.displayName(it, lang) }
    }
    val inRoutine = routine.exercises.groupingBy { it.exercise.id }.eachCount()
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.board_library).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            query,
            { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text(stringResource(R.string.board_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BOARD_MUSCLES.forEach { m ->
                FilterChip(selected = muscle == m, onClick = { muscle = if (muscle == m) null else m }, label = { Text(m.label()) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Equipment.entries.forEach { e ->
                FilterChip(selected = equipment == e, onClick = { equipment = if (equipment == e) null else e }, label = { Text(e.label()) })
            }
        }
        Text(
            stringResource(R.string.board_hint, shown.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { ex -> LibraryRow(ex, inRoutine[ex.id] ?: 0, board, onAdd) }
        }
    }
}

@Composable
private fun LibraryRow(ex: Exercise, count: Int, board: BoardState, onAdd: (String) -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val name = ex.displayName()
    val dragging = board.drag?.exercise?.id == ex.id
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (count > 0) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), MaterialTheme.shapes.medium) else Modifier)
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Mantener pulsado y arrastrar a la rutina
            .pointerInput(ex.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { at -> board.drag = BoardDrag(ex, origin + at) },
                    onDragEnd = {
                        if (board.overDrop) onAdd(ex.id)
                        board.drag = null
                    },
                    onDragCancel = { board.drag = null },
                ) { change, delta ->
                    change.consume()
                    board.drag = board.drag?.let { it.copy(position = it.position + delta) }
                }
            }
            .clickable(onClickLabel = stringResource(R.string.board_add, name)) { onAdd(ex.id) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        com.bestrong.mobile.ui.components.ExerciseThumb(ex, 40.dp, enlargeOnTap = false)
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (dragging) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text(ex.primaryMuscle.label() + " · " + ex.equipment.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (count > 0) {
            Text("×$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { onAdd(ex.id) }) { Icon(Icons.Default.Add, stringResource(R.string.board_add, name), tint = MaterialTheme.colorScheme.primary) }
    }
}

/** La tarjeta que sigue al dedo mientras se arrastra (encima de todo el tablero). */
@Composable
fun BoardDragGhost(board: BoardState, rootOffset: Offset) {
    val drag = board.drag ?: return
    val density = LocalDensity.current
    val halfW = with(density) { (GHOST_WIDTH / 2).toPx() }
    val halfH = with(density) { 28.dp.toPx() }
    Box(
        Modifier.offset { IntOffset((drag.position.x - rootOffset.x - halfW).roundToInt(), (drag.position.y - rootOffset.y - halfH).roundToInt()) }
            .width(GHOST_WIDTH)
            .shadow(12.dp, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(if (board.overDrop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(14.dp),
    ) {
        Text(
            drag.exercise.displayName(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (board.overDrop) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Cifras de la rutina que cambian al momento: ejercicios, series y duración estimada. */
@Composable
fun BoardSummary(routine: Routine, modifier: Modifier = Modifier) {
    val sets = routine.exercises.sumOf { it.sets.size }
    val minutes = remember(routine) { if (routine.exercises.isEmpty()) 0 else RoutineTime.estimateMinutes(routine) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.bestrong.mobile.ui.components.StatTile(stringResource(R.string.board_exercises), "${routine.exercises.size}", Modifier.weight(1f))
        com.bestrong.mobile.ui.components.StatTile(stringResource(R.string.board_sets), "$sets", Modifier.weight(1f))
        com.bestrong.mobile.ui.components.StatTile(stringResource(R.string.board_minutes), "≈ $minutes min", Modifier.weight(1f))
    }
}

/** Zona donde se suelta: borde de acento mientras hay un ejercicio encima. */
fun Modifier.boardDropZone(board: BoardState): Modifier = this.onGloballyPositioned { c ->
    val p = c.positionInRoot()
    board.dropZone = Rect(p, androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat()))
}

private val BOARD_MUSCLES = MuscleGroup.entries - MuscleGroup.FULL_BODY
private val GHOST_WIDTH = 240.dp
