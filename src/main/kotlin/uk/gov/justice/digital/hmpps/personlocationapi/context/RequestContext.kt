package uk.gov.justice.digital.hmpps.personlocationapi.context

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.personlocationapi.domain.DataSource
import java.time.LocalDateTime

data class RequestContext(
  val username: String,
  val requestAt: LocalDateTime = LocalDateTime.now(),
  val reason: String? = null,
  val source: DataSource = DataSource.DPS,
  val caseloadId: String? = null,
  val migratingData: Boolean = false,
) {
  companion object {
    const val SYSTEM_USERNAME = "SYS"

    fun get(): RequestContext = RequestContextHolder.getContext()
    fun clear() {
      RequestContextHolder.clearContext()
    }
  }
}

@Component
class RequestContextHolder {
  companion object {
    private var context: ThreadLocal<RequestContext> =
      ThreadLocal.withInitial { RequestContext(SYSTEM_USERNAME) }

    internal fun getContext(): RequestContext = context.get()
    internal fun setContext(rc: RequestContext) {
      context.set(rc)
    }

    internal fun clearContext() {
      context.remove()
    }
  }
}
