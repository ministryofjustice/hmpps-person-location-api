package uk.gov.justice.digital.hmpps.personlocationapi.model.timeline

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "The timeline of all periods of custody in chronological order.")
data class Timeline(val periods: List<TimelinePeriod>)
data class TimelinePeriod(
  @Schema(description = "The date and time when the period in custody started.")
  val commencedAt: LocalDateTime,
  @Schema(description = "The date and time when the period in custody ended, null if current.")
  val concludedAt: LocalDateTime?,
  @Schema(description = "A list of all the prisons stayed at in chronological order.")
  val stays: List<TimelineStay>,
)

@Schema(description = "Represents a period of time at a given prison.")
data class TimelineStay(
  @Schema(description = "The prison where the person was resident for the period.")
  val prison: TimelinePrison,
  @Schema(description = "Information about the arrival.")
  val arrival: TimeLineActivity,
  @Schema(description = "Information about the departure. Null if this is where the person is currently in custody.")
  val departure: TimeLineActivity?,
)

@Schema(description = "The prison where the person was resident for the period.")
data class TimelinePrison(@Schema(description = "The unique code identifier of the prison.") val code: String)

@Schema(description = "Represents an activity such as an arrival or departure.")
data class TimeLineActivity(
  @Schema(description = "The date and time the activity took place.")
  val at: LocalDateTime,
  @Schema(description = "The reason the activity was recorded.")
  val reason: String,
)
