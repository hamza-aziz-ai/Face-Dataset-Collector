package com.example.facedatasetcollector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.facedatasetcollector.data.EnrolmentStatus
import com.example.facedatasetcollector.data.Notice
import com.example.facedatasetcollector.data.Person
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
private fun PersonRow(person: Person, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    // A pending or enrolled person cannot start a new enrolment: the contract defines neither case.
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
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(person.employeeCode, person.department).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(person.status)
    }
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
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
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
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { person -> PersonRow(person, { onSelect(person) }, Modifier.weight(1f)) }
                    // Keeps a lone last person at the same width as the others.
                    if (wide && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (hasMore) TextButton(onClick = onLoadMore, enabled = !busy) { Text("Show more") }
        Text(
            "Only people on your organisation's roster appear here. Ask your admin to add anyone who is missing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
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
                "${person.name}, ${person.employeeCode}",
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
            .padding(if (roomy) 24.dp else 14.dp),
        // Roomy: the sections share the extra height, so the card fills its space evenly.
        verticalArrangement = if (roomy) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
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
    TextButton(onClick = onDeclined, modifier = Modifier.fillMaxWidth()) { Text("Person declined") }
}
