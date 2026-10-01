package uk.gov.justice.digital.hmpps.personlocationapi.model.action

interface NotesAction {
  val notes: String?
  infix fun changes(notes: String?): Boolean {
    val truncationIndex = this.notes?.indexOf(TRUNCATION_IDENTIFIER) ?: -1
    val replacementNotes = if (truncationIndex > 0 && notes?.startsWith(this.notes!!.substring(0, truncationIndex)) == true) {
      notes
    } else {
      this.notes
    }
    return replacementNotes != notes
  }

  companion object {
    const val TRUNCATION_IDENTIFIER = "... see DPS for full text"
  }
}
