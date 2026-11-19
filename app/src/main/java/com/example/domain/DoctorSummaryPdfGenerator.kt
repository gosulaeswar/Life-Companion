package com.example.domain

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.HealthDocumentEntity
import com.example.data.local.entity.LabResultEntity
import com.example.data.local.entity.MedicalHistoryEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Renders a portable, Doctor-Ready PDF Health Summary using Android's native
 * [PdfDocument] API, combining profile info, allergies, medical history, active medicines,
 * laboratory comparisons, and recent consultations with a mandatory non-diagnostic disclaimer.
 */
object DoctorSummaryPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 40f
    private const val TOP_Y = 44f
    private const val BOTTOM_Y = 795f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 80f

    fun buildSummaryPdfBytes(
        profile: UserProfileEntity?,
        allergies: List<AllergyRecordEntity>,
        medicalHistory: List<MedicalHistoryEntity>,
        medicines: List<MedicineEntity>,
        labComparisons: List<LabComparisonSummary>,
        consultations: List<DoctorConsultationEntity>,
        documents: List<HealthDocumentEntity>,
        todayEpochDay: Long = DateEpochUtils.todayEpochDay()
    ): ByteArray {
        return runCatching {
            renderWithAndroidPdfDocument(
                profile = profile,
                allergies = allergies,
                medicalHistory = medicalHistory,
                medicines = medicines,
                labComparisons = labComparisons,
                consultations = consultations,
                documents = documents,
                todayEpochDay = todayEpochDay
            )
        }.getOrElse {
            buildPortablePdf14Bytes(
                profile = profile,
                allergies = allergies,
                medicalHistory = medicalHistory,
                medicines = medicines,
                labComparisons = labComparisons,
                consultations = consultations,
                documents = documents,
                todayEpochDay = todayEpochDay
            )
        }
    }

    private fun renderWithAndroidPdfDocument(
        profile: UserProfileEntity?,
        allergies: List<AllergyRecordEntity>,
        medicalHistory: List<MedicalHistoryEntity>,
        medicines: List<MedicineEntity>,
        labComparisons: List<LabComparisonSummary>,
        consultations: List<DoctorConsultationEntity>,
        documents: List<HealthDocumentEntity>,
        todayEpochDay: Long
    ): ByteArray {
        val pdf = PdfDocument()
        try {
            var pageNum = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
            var canvas = page.canvas
            var y = TOP_Y

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F5B58")
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = 10.5f
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F5B58")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                textSize = 10.5f
            }
            val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val disclaimerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#92400E")
                textSize = 9.5f
            }
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
            }

            fun ensureSpace(neededHeight: Float) {
                if (y + neededHeight > BOTTOM_Y) {
                    canvas.drawText(
                        "LifeWeave Local Vault • Page $pageNum • Informational summary only",
                        MARGIN_X,
                        PAGE_HEIGHT - 22f,
                        subtitlePaint
                    )
                    pdf.finishPage(page)
                    pageNum += 1
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                    canvas = page.canvas
                    y = TOP_Y
                }
            }

            fun drawWrappedLine(text: String, paint: Paint, indent: Float = 0f, lineHeight: Float = 14f) {
                val maxW = CONTENT_WIDTH - indent
                val words = text.split(" ")
                val current = StringBuilder()
                for (word in words) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (paint.measureText(candidate) > maxW && current.isNotEmpty()) {
                        ensureSpace(lineHeight)
                        canvas.drawText(current.toString(), MARGIN_X + indent, y, paint)
                        y += lineHeight
                        current.clear()
                        current.append(word)
                    } else {
                        current.clear()
                        current.append(candidate)
                    }
                }
                if (current.isNotEmpty()) {
                    ensureSpace(lineHeight)
                    canvas.drawText(current.toString(), MARGIN_X + indent, y, paint)
                    y += lineHeight
                }
            }

            fun drawSectionHeader(title: String) {
                ensureSpace(32f)
                y += 6f
                canvas.drawText(title, MARGIN_X, y, sectionPaint)
                y += 5f
                canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, dividerPaint)
                y += 14f
            }

            canvas.drawText("LifeWeave — Doctor-Ready Health Summary", MARGIN_X, y, titlePaint)
            y += 18f
            val profileLine = buildString {
                append("Patient: ${profile?.fullName ?: "Local User"}")
                if (profile != null) {
                    append(" • Age: ${profile.age}")
                    append(" • ${profile.genderIdentity}")
                    profile.heightCm?.let { append(" • ${it}cm") }
                    profile.weightKg?.let { append(" • ${it}kg") }
                }
            }
            drawWrappedLine(profileLine, boldBodyPaint)
            drawWrappedLine("Generated: ${DateEpochUtils.formatEpochDayFull(todayEpochDay)} • Stored 100% locally", subtitlePaint)
            y += 4f
            drawWrappedLine(
                "NOTICE: ${HealthVaultAndRefillEngine.STANDARD_AI_MEDICAL_DISCLAIMER}",
                disclaimerPaint
            )
            y += 6f

            drawSectionHeader("1. Allergies & Adverse Reactions (${allergies.size})")
            if (allergies.isEmpty()) {
                drawWrappedLine("No allergies recorded.", subtitlePaint, indent = 8f)
            } else {
                allergies.forEach { a ->
                    val detail = buildString {
                        append("• ${a.allergen} [${a.severity}]")
                        if (a.reaction.isNotBlank()) append(" — Reaction: ${a.reaction}")
                        if (a.notes.isNotBlank()) append(" (${a.notes})")
                    }
                    drawWrappedLine(detail, bodyPaint, indent = 8f)
                }
            }

            drawSectionHeader("2. Medical History & Conditions (${medicalHistory.size})")
            if (medicalHistory.isEmpty()) {
                drawWrappedLine("No medical history entries recorded.", subtitlePaint, indent = 8f)
            } else {
                medicalHistory.forEach { h ->
                    val line = buildString {
                        append("• ${h.conditionOrEvent} (${h.category} • ${h.status})")
                        if (h.diagnosedYearOrDate.isNotBlank()) append(" — ${h.diagnosedYearOrDate}")
                        if (h.notes.isNotBlank()) append(": ${h.notes}")
                    }
                    drawWrappedLine(line, bodyPaint, indent = 8f)
                }
            }

            val activeMeds = medicines.filter { it.isActive }
            drawSectionHeader("3. Active Medicines & Inventory (${activeMeds.size})")
            if (activeMeds.isEmpty()) {
                drawWrappedLine("No active medicines listed.", subtitlePaint, indent = 8f)
            } else {
                activeMeds.forEach { med ->
                    val inv = HealthVaultAndRefillEngine.evaluateMedicineInventory(med, todayEpochDay)
                    val stockInfo = if (med.stockQuantity > 0f) {
                        val daysStr = inv.estimatedDaysRemaining?.let { " (~${it.toInt()}d left)" }.orEmpty()
                        " • Stock: ${HealthVaultAndRefillEngine.formatStockQuantity(med.stockQuantity, med.stockUnit)}$daysStr"
                    } else ""
                    val expiryInfo = med.expiryEpochDay?.let {
                        " • Exp: ${DateEpochUtils.formatEpochDayShort(it)}"
                    }.orEmpty()
                    val line = "• ${med.name} (${med.dosage}) — ${med.frequency}, ${med.mealRelation}$stockInfo$expiryInfo"
                    drawWrappedLine(line, bodyPaint, indent = 8f)
                }
            }

            drawSectionHeader("4. Laboratory Measurements & Trends (${labComparisons.size})")
            if (labComparisons.isEmpty()) {
                drawWrappedLine("No laboratory measurements recorded.", subtitlePaint, indent = 8f)
            } else {
                labComparisons.take(20).forEach { comp ->
                    val latestVal = comp.latest.valueText.ifBlank { comp.latest.valueNumeric?.toString() ?: "—" }
                    val prevInfo = comp.previous?.let { prev ->
                        val prevVal = prev.valueText.ifBlank { prev.valueNumeric?.toString() ?: "—" }
                        val deltaStr = comp.numericDelta?.let {
                            " (${comp.directionSymbol} ${HealthVaultAndRefillEngine.formatDelta(it, comp.latest.unit)})"
                        }.orEmpty()
                        " | Prev: $prevVal on ${DateEpochUtils.formatEpochDayShort(prev.recordedEpochDay)}$deltaStr"
                    }.orEmpty()
                    val refInfo = if (comp.latest.referenceRangeText.isNotBlank()) {
                        " [Ref: ${comp.latest.referenceRangeText}]"
                    } else ""
                    val line = "• ${comp.displayTestName}: $latestVal ${comp.latest.unit} (${comp.latest.statusFlag}) " +
                        "on ${DateEpochUtils.formatEpochDayShort(comp.latest.recordedEpochDay)}$refInfo$prevInfo"
                    drawWrappedLine(line, bodyPaint, indent = 8f)
                }
            }

            drawSectionHeader("5. Recent Consultations & Prescriptions (${consultations.size} visits, ${documents.size} vault docs)")
            if (consultations.isEmpty() && documents.isEmpty()) {
                drawWrappedLine("No consultations or documents logged.", subtitlePaint, indent = 8f)
            } else {
                consultations.take(8).forEach { c ->
                    val line = "• ${DateEpochUtils.formatEpochDayAndTime(c.consultationEpochMs)} — Dr. ${c.doctorName} (${c.specialty}): ${c.summary.ifBlank { "Consultation logged" }}"
                    drawWrappedLine(line, bodyPaint, indent = 8f)
                }
                documents.take(8).forEach { d ->
                    val line = "• [${d.category}] ${d.title} (${DateEpochUtils.formatEpochDayShort(d.documentEpochDay)})" +
                        if (d.providerOrLabName.isNotBlank()) " — ${d.providerOrLabName}" else ""
                    drawWrappedLine(line, subtitlePaint, indent = 8f)
                }
            }

            canvas.drawText(
                "LifeWeave Local Vault • Page $pageNum • Verify all entries with your clinician",
                MARGIN_X,
                PAGE_HEIGHT - 22f,
                subtitlePaint
            )
            pdf.finishPage(page)

            return ByteArrayOutputStream().use { out ->
                pdf.writeTo(out)
                out.toByteArray()
            }
        } finally {
            runCatching { pdf.close() }
        }
    }

    private fun buildPortablePdf14Bytes(
        profile: UserProfileEntity?,
        allergies: List<AllergyRecordEntity>,
        medicalHistory: List<MedicalHistoryEntity>,
        medicines: List<MedicineEntity>,
        labComparisons: List<LabComparisonSummary>,
        consultations: List<DoctorConsultationEntity>,
        documents: List<HealthDocumentEntity>,
        todayEpochDay: Long
    ): ByteArray {
        val lines = buildList {
            add("LifeWeave - Doctor-Ready Health Summary")
            add("Patient: ${profile?.fullName ?: "Local User"} | Generated: ${DateEpochUtils.formatEpochDayFull(todayEpochDay)}")
            add("NOTICE: ${HealthVaultAndRefillEngine.STANDARD_AI_MEDICAL_DISCLAIMER}")
            add("1. Allergies (${allergies.size}): " + allergies.joinToString("; ") { "${it.allergen} [${it.severity}]" }.ifBlank { "None" })
            add("2. Medical History (${medicalHistory.size}): " + medicalHistory.joinToString("; ") { "${it.conditionOrEvent} (${it.status})" }.ifBlank { "None" })
            val activeMeds = medicines.filter { it.isActive }
            add("3. Active Medicines (${activeMeds.size}): " + activeMeds.joinToString("; ") { "${it.name} ${it.dosage} (${it.frequency})" }.ifBlank { "None" })
            add("4. Labs (${labComparisons.size}): " + labComparisons.take(12).joinToString("; ") { "${it.displayTestName}: ${it.latest.valueText} ${it.latest.unit} (${it.directionSymbol})" }.ifBlank { "None" })
            add("5. Consultations (${consultations.size}), Vault Docs (${documents.size})")
        }
        val streamContent = buildString {
            appendLine("BT")
            appendLine("/F1 11 Tf")
            var yPos = 780
            lines.forEach { rawLine ->
                val escaped = rawLine
                    .replace("\\", "\\\\")
                    .replace("(", "\\(")
                    .replace(")", "\\)")
                    .filter { it.code in 32..126 }
                    .take(92)
                appendLine("1 0 0 1 40 $yPos Tm ($escaped) Tj")
                yPos -= 18
            }
            appendLine("ET")
        }
        val obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
        val obj2 = "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
        val obj3 = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n"
        val obj4 = "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
        val streamBytes = streamContent.toByteArray(Charsets.US_ASCII)
        val obj5 = "5 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n$streamContent\nendstream\nendobj\n"

        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        writeAscii("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        listOf(obj1, obj2, obj3, obj4, obj5).forEach { obj ->
            offsets += out.size()
            writeAscii(obj)
        }
        val xrefStart = out.size()
        writeAscii("xref\n0 6\n0000000000 65535 f \n")
        offsets.forEach { off ->
            writeAscii(String.format("%010d 00000 n \n", off))
        }
        writeAscii("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        return out.toByteArray()
    }

    suspend fun writeSummaryPdfToUri(
        context: Context,
        uri: Uri,
        profile: UserProfileEntity?,
        allergies: List<AllergyRecordEntity>,
        medicalHistory: List<MedicalHistoryEntity>,
        medicines: List<MedicineEntity>,
        labComparisons: List<LabComparisonSummary>,
        consultations: List<DoctorConsultationEntity>,
        documents: List<HealthDocumentEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = buildSummaryPdfBytes(
                profile = profile,
                allergies = allergies,
                medicalHistory = medicalHistory,
                medicines = medicines,
                labComparisons = labComparisons,
                consultations = consultations,
                documents = documents
            )
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: error("Unable to open destination file for PDF export.")
            bytes.size
        }
    }
}
