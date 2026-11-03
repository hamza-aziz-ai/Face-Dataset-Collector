package com.example.facedatasetcollector.data

/** The server's person_id rule (CCTV 8c): it becomes a folder name on a Windows host, so staff see a refusal before anything is sent. */
private val PERSON_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
private val WINDOWS_DEVICE = Regex("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?", RegexOption.IGNORE_CASE)

const val MAX_NAME_LENGTH = 120

fun isValidPersonId(id: String): Boolean = PERSON_ID.matches(id) && !WINDOWS_DEVICE.matches(id)

/** Trimmed, with inner runs of spaces kept as typed. Null when it is empty or too long. */
fun cleanPersonName(name: String): String? = name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }
