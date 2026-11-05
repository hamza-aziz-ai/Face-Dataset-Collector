package com.example.facedatasetcollector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.facedatasetcollector.data.EnrolmentStatus
import com.example.facedatasetcollector.data.Notice
import com.example.facedatasetcollector.data.Person
import com.example.facedatasetcollector.data.ReEnrolReason
import com.example.facedatasetcollector.data.RemovalReason
import com.example.facedatasetcollector.data.cleanPersonName
import com.example.facedatasetcollector.data.isValidPersonId
import com.example.facedatasetcollector.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay

private const val SEARCH_DELAY_MS = 300L
private val StandardPageWidth = 840.dp
private val WidePageWidth = 1400.dp

// How much of a tall tablet screen the notice fills, leaving room for the heading and controls.
private const val NOTICE_HEIGHT_FRACTION = 0.56f

// Heading and page padding above the panes when side by side.
private val WideHeaderHeight = 230.dp

@Composable
private fun StatusChip(status: EnrolmentStatus) {
    val colors = LocalStatusColors.current
    val (label, fill, text) = when (status) {
        EnrolmentStatus.NONE -> Triple("Not enrolled", colors.warningFill, colors.warningText)
        EnrolmentStatus.PENDING -> Triple("Checking", MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.onSurface)
        EnrolmentStatus.ENROLLED -> Triple("Enrolled", colors.successFill, colors.successText)
    }
    Text(
        label,
        color = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(fill, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun PersonRow(person: Person, onSelect: () -> Unit, onReEnrol: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    // Pending stays blocked: one open job per person. Enrolled needs the explicit action, so a stray tap cannot start a replacement.
    val selectable = person.status == EnrolmentStatus.NONE
    Row(
        modifier
            .heightIn(min = 76.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(enabled = selectable, role = Role.Button, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The ID large and the name beneath, as on the display (owner, 2026-11-03).
        Column(Modifier.weight(1f)) {
            Text(person.personId, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(person.name, person.department).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusChip(person.status)
            if (person.status == EnrolmentStatus.ENROLLED) {
                TextButton(
                    onClick = onReEnrol,
                    modifier = Modifier.semantics { contentDescription = "Re-enrol ${person.personId}, ${person.name}" },
                ) { Text("Re-enrol") }
            }
            // Any status: a registered person with no photos yet can leave too, and an open check is cancelled.
            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.semantics { contentDescription = "Remove ${person.personId}, ${person.name}" },
            ) { Text("Remove") }
        }
    }
}

/** Removal cannot be undone, so no reason is preselected and the button says what it does. */
@Composable
fun RemoveDialog(person: Person, deletionDays: Int?, onRemove: (RemovalReason) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf<RemovalReason?>(null) }
    val within = deletionDays?.let { " within $it days" }.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${person.personId}, ${person.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Their consent ends, and their photos and face data are deleted$within. Attendance records stay. This cannot be undone.")
                Text("Why are they being removed?", style = MaterialTheme.typography.labelMedium)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemovalReason.entries.forEach { choice ->
                        ChoiceButton(choice.label, reason == choice, { reason = choice }, Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reason?.let(onRemove) },
                enabled = reason != null,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The confirm step. No choice is preselected, so the reason on record is one staff actually made. */
@Composable
fun ReEnrolDialog(person: Person, onContinue: (ReEnrolReason) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf<ReEnrolReason?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Re-enrol ${person.personId}, ${person.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The current enrolment stays in use until the new photos are accepted. The person sees the notice again and agrees again.")
                Text("Why are you re-enrolling them?", style = MaterialTheme.typography.labelMedium)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReEnrolReason.entries.forEach { choice ->
                        ChoiceButton(choice.label, reason == choice, { reason = choice }, Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { reason?.let(onContinue) }, enabled = reason != null) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PersonScreen(
    persons: List<Person>,
    hasMore: Boolean,
    isMock: Boolean,
    busy: Boolean,
    error: String?,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (Person) -> Unit,
    onReEnrol: (Person, ReEnrolReason) -> Unit,
    deletionDays: Int?,
    onRemove: (Person, RemovalReason) -> Unit,
    onRegister: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // By id, so the dialog survives rotation and closes if the person leaves the list.
    var confirmingId by rememberSaveable { mutableStateOf<String?>(null) }
    persons.firstOrNull { it.personId == confirmingId }?.let { person ->
        ReEnrolDialog(
            person,
            onContinue = { reason ->
                confirmingId = null
                onReEnrol(person, reason)
            },
            onDismiss = { confirmingId = null },
        )
    }
    var removingId by rememberSaveable { mutableStateOf<String?>(null) }
    persons.firstOrNull { it.personId == removingId }?.let { person ->
        RemoveDialog(
            person,
            deletionDays,
            onRemove = { reason ->
                removingId = null
                onRemove(person, reason)
            },
            onDismiss = { removingId = null },
        )
    }
    // Waits for a pause in typing, so each keystroke does not hit the server.
    LaunchedEffect(query) {
        delay(SEARCH_DELAY_MS)
        onSearch(query)
    }

    BoxWithConstraints(modifier) {
        val wide = isWideLayout(maxWidth, maxHeight)
        StaffPage(maxContentWidth = if (wide) WidePageWidth else StandardPageWidth) {
        MockChip(isMock)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Select person", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            TextButton(onClick = onSignOut, enabled = !busy) { Text("Sign out") }
        }
        TextButton(onClick = onRegister, enabled = !busy) { Text("Register a new person") }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search your roster") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorMessage(error)
        Text(
            if (persons.size == 1) "1 person shown" else "${persons.size} people shown",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            persons.chunked(if (wide) 2 else 1).forEach { row ->
                // IntrinsicSize, so a row with a Re-enrol button does not stand taller than its neighbour.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { person ->
                        PersonRow(person, { onSelect(person) }, { confirmingId = person.personId }, { removingId = person.personId }, Modifier.weight(1f).fillMaxHeight())
                    }
                    // Keeps a lone last person at the same width as the others.
                    if (wide && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (hasMore) TextButton(onClick = onLoadMore, enabled = !busy) { Text("Show more") }
        Text(
            "Only people on your organisation's roster appear here. Register anyone missing with their organisation ID.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
    }
}

/**
 * Adds a person by their organisational or institutional ID (D-023). A typo would enrol photos under the wrong ID, so it ends on a confirm step that shows the ID large.
 */
@Composable
fun RegisterScreen(
    isMock: Boolean,
    busy: Boolean,
    error: String?,
    onRegister: (personId: String, name: String, department: String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var department by rememberSaveable { mutableStateOf("") }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val cleanId = id.trim()
    val idOk = isValidPersonId(cleanId)
    val cleanName = cleanPersonName(name)
    val cleanDepartment = department.trim().ifEmpty { null }

    if (confirming && idOk && cleanName != null) {
        RegisterConfirmDialog(
            cleanId, cleanName, cleanDepartment,
            onConfirm = {
                confirming = false
                onRegister(cleanId, cleanName, cleanDepartment)
            },
            onDismiss = { confirming = false },
        )
    }

    StaffPage(modifier, centred = true) {
        MockChip(isMock)
        Text("Register a new person", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        OutlinedTextField(
            value = id,
            onValueChange = { id = it },
            label = { Text("Organisation ID") },
            supportingText = { Text("Letters, digits, dot, underscore or hyphen, starting with a letter or digit. Up to 64.") },
            isError = cleanId.isNotEmpty() && !idOk,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full name") },
            isError = name.isNotBlank() && cleanName == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = department,
            onValueChange = { department = it },
            label = { Text("Department (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorMessage(error)
        PrimaryButton("Continue", busy, enabled = idOk && cleanName != null) { confirming = true }
        TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

/** The ID large, because a wrong ID is the mistake that matters, and it cannot be changed in the app. */
@Composable
fun RegisterConfirmDialog(personId: String, name: String, department: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register this person?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(personId, style = MaterialTheme.typography.headlineMedium)
                Text(name, style = MaterialTheme.typography.bodyLarge)
                department?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Check the ID against their ID card. It cannot be changed in the app.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Register") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Edit") } },
    )
}

@Composable
fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val fill = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val text = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val outline: Color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Text(
        label,
        color = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(fill, RoundedCornerShape(8.dp))
            .border(1.dp, outline, RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
fun ConsentScreen(
    person: Person,
    notice: Notice,
    busy: Boolean,
    error: String?,
    onRecord: (wearsGlasses: Boolean) -> Unit,
    onDeclined: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    var wearsGlasses by rememberSaveable { mutableStateOf<Boolean?>(null) }

    val header = @Composable {
        Column {
            Text("Notice and consent", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                "${person.personId}, ${person.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Show this to the person, or read it aloud.", style = MaterialTheme.typography.labelMedium)
    }
    val controls = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ConsentChoices(agreed, { agreed = it }, wearsGlasses, { wearsGlasses = it })
        }
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ConsentActions(agreed, wearsGlasses, busy, error, onRecord, onDeclined)
        }
    }

    BoxWithConstraints(modifier) {
        val wide = isWideLayout(maxWidth, maxHeight)
        val tablet = isTabletLayout(maxWidth)
        val paneHeight = maxHeight - WideHeaderHeight
        val tallNotice = maxHeight * NOTICE_HEIGHT_FRACTION
        StaffPage(maxContentWidth = if (wide) WidePageWidth else StandardPageWidth) {
            header()
            if (wide) {
                // Side by side, so staff never scroll past the notice to reach the controls.
                // heightIn, not a fixed height: a long notice grows and the page scrolls.
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    NoticeCard(notice, Modifier.weight(1f).heightIn(min = paneHeight), roomy = true)
                    Column(
                        Modifier.weight(1f).heightIn(min = paneHeight),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) { controls() }
                }
            } else {
                NoticeCard(notice, Modifier.fillMaxWidth().heightIn(min = if (tablet) tallNotice else 0.dp), roomy = tablet)
                controls()
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: Notice, modifier: Modifier, roomy: Boolean) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(if (roomy) 18.dp else 14.dp),
        // A fixed gap after every section, so a long notice never runs its paragraphs together (owner, 2026-11-03).
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        notice.sections.forEach { section ->
            Column {
                Text(section.heading, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(section.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column {
            Text("Responsible", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(notice.controller, notice.processor?.let { "$it processes data on its behalf" }).joinToString(". "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column {
            Text("Contact", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(notice.contact.name, notice.contact.email, notice.contact.phone).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "Notice version ${notice.version}, effective ${notice.effectiveDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConsentChoices(
    agreed: Boolean,
    onAgreed: (Boolean) -> Unit,
    wearsGlasses: Boolean?,
    onGlasses: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(value = agreed, role = Role.Checkbox, onValueChange = onAgreed),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The Row is the control, so screen readers announce one checkbox with its full text.
        Checkbox(checked = agreed, onCheckedChange = null)
        Text("This person saw the notice and agreed. I am recording this under my staff account.", style = MaterialTheme.typography.bodyMedium)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Does this person wear glasses?", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceButton("No, 5 photos", wearsGlasses == false, { onGlasses(false) }, Modifier.weight(1f))
            ChoiceButton("Yes, 10 photos", wearsGlasses == true, { onGlasses(true) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConsentActions(
    agreed: Boolean,
    wearsGlasses: Boolean?,
    busy: Boolean,
    error: String?,
    onRecord: (wearsGlasses: Boolean) -> Unit,
    onDeclined: () -> Unit,
) {
    ErrorMessage(error)
    PrimaryButton("Record consent and continue", busy, enabled = agreed && wearsGlasses != null) {
        if (wearsGlasses != null) onRecord(wearsGlasses)
    }
    TextButton(onClick = onDeclined, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Person declined") }
}
