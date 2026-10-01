package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "migration_system_audit")
class MigrationSystemAudit(
  @Id
  @Column("id")
  val uuid: UUID,
  @Column(name = "created_at", nullable = false)
  var createdAt: LocalDateTime,
  @Column(name = "created_by", nullable = false)
  var createdBy: String,
  @Column(name = "modified_at")
  var modifiedAt: LocalDateTime?,
  @Column(name = "modified_by")
  var modifiedBy: String?,
) : Persistable<UUID> {
  override fun getId() = uuid

  @Transient
  var new: Boolean = true
  override fun isNew() = new

  @PostLoad
  fun onLoad() {
    new = false
  }
}

interface MigrationSystemAuditRepository : CrudRepository<MigrationSystemAudit, UUID>
