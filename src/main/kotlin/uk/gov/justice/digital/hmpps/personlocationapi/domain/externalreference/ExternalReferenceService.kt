package uk.gov.justice.digital.hmpps.personlocationapi.domain.externalreference

enum class ExternalReferenceService(val code: String, val description: String) {
  COURT_APPEARANCE_SCHEDULER("court-appearance-scheduler", "Court Appearance Scheduler"),
  TEMPORARY_ABSENCE_SCHEDULER("temporary-absence-scheduler", "Temporary Absence Scheduler"),
  TRANSFER_SCHEDULER("transfer-scheduler", "Transfer Scheduler"),
  RELEASE_SCHEDULER("release-scheduler", "Release Scheduler"),
  ;

  companion object {
    @JvmStatic
    fun fromString(value: String): ExternalReferenceService = entries.firstOrNull { it.code.equals(value, ignoreCase = true) }
      ?: throw IllegalArgumentException("External reference service not recognised")
  }
}

enum class ExternalReferenceEntity(val code: String, val services: Set<ExternalReferenceService>) {
  COURT_APPEARANCE("court-appearance", setOf(ExternalReferenceService.COURT_APPEARANCE_SCHEDULER)),
  TEMPORARY_ABSENCE("temporary-absence", setOf(ExternalReferenceService.TEMPORARY_ABSENCE_SCHEDULER)),
  TRANSFER("transfer", setOf(ExternalReferenceService.TRANSFER_SCHEDULER)),
  RELEASE("release", setOf(ExternalReferenceService.RELEASE_SCHEDULER)),
  ;

  fun forService(service: ExternalReferenceService) = apply {
    check(service in services) { "External reference service and entity combination not valid" }
  }

  companion object {
    @JvmStatic
    fun fromString(value: String): ExternalReferenceEntity = entries.firstOrNull { it.code.equals(value, ignoreCase = true) }
      ?: throw IllegalArgumentException("External reference entity not recognised")
  }
}
