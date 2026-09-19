package com.owlcoders.chitti.documents

/** The kinds of personal document Chitti recognises. */
enum class DocumentKind(val label: String) {
    AADHAAR("Aadhaar card"),
    PAN("PAN card"),
    RATION("Ration card"),
    BIRTH("Birth certificate"),
    OTHER("Document")
}

/** What was read off a document. Every field is optional: the user reviews and edits before saving. */
data class ExtractedFields(
    val kind: DocumentKind = DocumentKind.OTHER,
    val name: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val fatherName: String = "",
    val address: String = "",
    val aadhaarNumber: String = "",
    val panNumber: String = "",
    val rationCardNumber: String = ""
) {
    val isEmpty: Boolean
        get() = listOf(name, dateOfBirth, gender, fatherName, address, aadhaarNumber, panNumber, rationCardNumber).all { it.isBlank() }
}

/**
 * Reads Indian ID documents from OCR text, entirely on the phone.
 *
 * Numbers are validated, not just pattern-matched: an Aadhaar number must pass its Verhoeff
 * checksum (so one misread digit is rejected rather than saved), and the 16-digit Virtual ID
 * printed under it is never mistaken for it. Labels are matched in English; the Hindi / regional
 * text on the same cards is ignored rather than guessed at.
 */
object IdParser {

    private val AADHAAR = Regex("(?<![\\d ])([2-9]\\d{3})\\s?(\\d{4})\\s?(\\d{4})(?!\\s?\\d)")
    private val PAN = Regex("\\b([A-Z]{5}[0-9]{4}[A-Z])\\b")
    private val DATE = Regex("\\b(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.]((?:19|20)\\d{2})\\b")
    private val YEAR_OF_BIRTH = Regex("(?i)year\\s*of\\s*birth\\s*[:\\-]?\\s*((?:19|20)\\d{2})")
    private val GENDER = Regex("(?i)\\b(male|female|transgender)\\b")
    private val RELATION = Regex("(?i)\\b(?:s/o|d/o|w/o|c/o|son of|daughter of|wife of)\\s*[:,]?\\s*([A-Za-z][A-Za-z .]{2,60}?)(?=,|\\n|$)")
    private val RATION_NO = Regex(
        "(?i)(?:ration\\s*card\\s*(?:no|number)|r\\.?\\s*c\\.?\\s*no|card\\s*no|fsc\\s*ref(?:erence)?\\s*no)\\.?\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9/\\-]{5,24})"
    )
    private val PINCODE = Regex("\\b[1-9]\\d{5}\\b")
    private val NAME_LINE = Regex("^[A-Za-z][A-Za-z .']{2,60}$")

    private val NOT_A_NAME = Regex(
        "(?i)government|india|income tax|department|permanent account|account number|signature|" +
            "unique identification|authority|aadhaar|enrol|download|issue|date|birth|male|female|address|" +
            "father|mother|name|card|certificate|ration|family|help|www|govt"
    )

    fun parse(text: String): ExtractedFields {
        val kind = detectKind(text)
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return when (kind) {
            DocumentKind.AADHAAR -> parseAadhaar(text, lines)
            DocumentKind.PAN -> parsePan(text, lines)
            DocumentKind.RATION -> parseRation(text, lines)
            DocumentKind.BIRTH -> parseBirth(text, lines)
            DocumentKind.OTHER -> ExtractedFields(
                kind = DocumentKind.OTHER,
                aadhaarNumber = findAadhaar(text),
                panNumber = PAN.find(text)?.value.orEmpty(),
                dateOfBirth = findDate(text)
            )
        }
    }

    fun detectKind(text: String): DocumentKind {
        val t = text.lowercase()
        return when {
            "permanent account number" in t || "income tax department" in t || (PAN.containsMatchIn(text) && "income tax" in t) -> DocumentKind.PAN
            "ration" in t || "civil supplies" in t || "food security" in t -> DocumentKind.RATION
            "birth certificate" in t || "certificate of birth" in t || "registration of births" in t -> DocumentKind.BIRTH
            "aadhaar" in t || "aadhar" in t || "unique identification" in t || findAadhaar(text).isNotEmpty() -> DocumentKind.AADHAAR
            else -> DocumentKind.OTHER
        }
    }

    // ---------------------------------------------------------------- per document

    private fun parseAadhaar(text: String, lines: List<String>): ExtractedFields {
        val dobIndex = lines.indexOfFirst { DATE.containsMatchIn(it) || YEAR_OF_BIRTH.containsMatchIn(it) }
        // On the front of the card the name is the line just above the date of birth.
        val name = if (dobIndex > 0) {
            (dobIndex - 1 downTo maxOf(0, dobIndex - 3)).map { lines[it] }.firstOrNull { isName(it) }.orEmpty()
        } else ""
        return ExtractedFields(
            kind = DocumentKind.AADHAAR,
            name = titleCase(name),
            dateOfBirth = findDate(text).ifBlank { YEAR_OF_BIRTH.find(text)?.groupValues?.get(1).orEmpty() },
            gender = findGender(text),
            fatherName = titleCase(RELATION.find(text)?.groupValues?.get(1)?.trim().orEmpty()),
            address = findAddress(text),
            aadhaarNumber = findAadhaar(text)
        )
    }

    private fun parsePan(text: String, lines: List<String>): ExtractedFields {
        val name = valueAfterLabel(lines, Regex("(?i)^name\\b(?!.*father)")) ?: run {
            // Older PAN cards: name is the first name-like line under the department heading.
            val start = lines.indexOfFirst { it.contains("department", ignoreCase = true) }
            lines.drop(start + 1).firstOrNull { isName(it) }
        }
        val father = valueAfterLabel(lines, Regex("(?i)father")) ?: run {
            val nameIdx = lines.indexOf(name)
            if (nameIdx >= 0) lines.drop(nameIdx + 1).firstOrNull { isName(it) } else null
        }
        return ExtractedFields(
            kind = DocumentKind.PAN,
            name = titleCase(name.orEmpty()),
            fatherName = titleCase(father.orEmpty()),
            dateOfBirth = findDate(text),
            panNumber = PAN.find(text)?.value.orEmpty()
        )
    }

    private fun parseRation(text: String, lines: List<String>): ExtractedFields {
        val head = valueAfterLabel(lines, Regex("(?i)head\\s*of\\s*(?:the\\s*)?family|hof\\s*name|card\\s*holder|name\\s*of\\s*(?:the\\s*)?head"))
            ?: valueAfterLabel(lines, Regex("(?i)^name\\b"))
        return ExtractedFields(
            kind = DocumentKind.RATION,
            name = titleCase(head.orEmpty()),
            rationCardNumber = RATION_NO.find(text)?.groupValues?.get(1)?.uppercase().orEmpty(),
            address = findAddress(text)
        )
    }

    private fun parseBirth(text: String, lines: List<String>): ExtractedFields {
        val name = valueAfterLabel(lines, Regex("(?i)^name(?!\\s*of\\s*(?:the\\s*)?(?:father|mother))"))
        val father = valueAfterLabel(lines, Regex("(?i)name\\s*of\\s*(?:the\\s*)?father|father'?s\\s*name"))
        val sex = valueAfterLabel(lines, Regex("(?i)^(?:sex|gender)\\b"))
        return ExtractedFields(
            kind = DocumentKind.BIRTH,
            name = titleCase(name.orEmpty()),
            dateOfBirth = findDate(text),
            gender = sex?.let { findGender(it) }?.takeIf { it.isNotBlank() } ?: findGender(text),
            fatherName = titleCase(father.orEmpty())
        )
    }

    // ---------------------------------------------------------------- helpers

    /** A label either carries its value on the same line ("Name: Ravi") or on the next line. */
    private fun valueAfterLabel(lines: List<String>, label: Regex): String? {
        for ((i, line) in lines.withIndex()) {
            val m = label.find(line) ?: continue
            val rest = line.substring(m.range.last + 1).trim().trimStart(':', '-', '/', ' ').trim()
            val inline = rest.substringAfter("/ ").trim().takeIf { it.isNotBlank() && isName(it) }
            if (inline != null) return inline
            val next = lines.getOrNull(i + 1)?.let { it.substringAfterLast("/").trim() }
            if (next != null && isName(next)) return next
        }
        return null
    }

    private fun isName(line: String): Boolean =
        NAME_LINE.matches(line) && !NOT_A_NAME.containsMatchIn(line) && line.count { it.isLetter() } >= 3

    fun findAadhaar(text: String): String =
        AADHAAR.findAll(text)
            .map { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            .firstOrNull { isValidAadhaar(it) }
            ?.let { formatAadhaar(it) }
            .orEmpty()

    private fun findDate(text: String): String {
        val m = DATE.find(text) ?: return ""
        val (d, mo, y) = m.destructured
        val day = d.toInt()
        val month = mo.toInt()
        if (day !in 1..31 || month !in 1..12) return ""
        return "%02d/%02d/%s".format(day, month, y)
    }

    private fun findGender(text: String): String =
        GENDER.find(text)?.value?.lowercase()?.replaceFirstChar { it.uppercase() }.orEmpty()

    private fun findAddress(text: String): String {
        val idx = text.indexOf("address", ignoreCase = true)
        if (idx < 0) return ""
        val after = text.substring(idx + "address".length).trimStart(':', ' ', '\n')
        // Up to and including the PIN code, which ends every Indian postal address.
        val pin = PINCODE.find(after)
        val raw = if (pin != null) after.substring(0, pin.range.last + 1) else after.lines().take(4).joinToString(" ")
        return raw.replace(Regex("\\s+"), " ").replace(RELATION, "").trim(' ', ',', '.')
    }

    private fun titleCase(s: String): String =
        s.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { w ->
            if (w.length <= 2 && w.endsWith(".")) w.uppercase() else w.lowercase().replaceFirstChar { it.uppercase() }
        }

    // ---------------------------------------------------------------- Aadhaar numbers

    fun formatAadhaar(digits: String): String {
        val d = digits.filter { it.isDigit() }
        return if (d.length == 12) "${d.substring(0, 4)} ${d.substring(4, 8)} ${d.substring(8)}" else digits
    }

    /** How Aadhaar is shown anywhere it is not being edited: only the last four digits. */
    fun maskAadhaar(number: String): String {
        val d = number.filter { it.isDigit() }
        return if (d.length == 12) "XXXX XXXX ${d.substring(8)}" else number
    }

    /** PAN shown with the middle hidden, e.g. ABXXXXX34F. */
    fun maskPan(pan: String): String =
        if (pan.length == 10) pan.substring(0, 2) + "XXXXX" + pan.substring(7) else pan

    private val D = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6), intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8), intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2), intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4), intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )
    private val P = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2), intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0), intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5), intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )
    private val INV = intArrayOf(0, 4, 3, 2, 1, 5, 6, 7, 8, 9)

    /** Verhoeff checksum, used by UIDAI for the last digit of every Aadhaar number. */
    fun isValidAadhaar(number: String): Boolean {
        val d = number.filter { it.isDigit() }
        if (d.length != 12 || d[0] < '2') return false
        var c = 0
        d.reversed().forEachIndexed { i, ch -> c = D[c][P[i % 8][ch - '0']] }
        return c == 0
    }

    /** The check digit that makes [first11] a valid Aadhaar number. */
    fun aadhaarCheckDigit(first11: String): Int {
        var c = 0
        (first11.filter { it.isDigit() } ).reversed().forEachIndexed { i, ch -> c = D[c][P[(i + 1) % 8][ch - '0']] }
        return INV[c]
    }
}
