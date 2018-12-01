package com.lankydanblog.tutorial.states

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

data class StockState(
  val sender: Party,
  val recipient: Party,
  val contents: String,
  val type: String,
  override val linearId: UniqueIdentifier,
  override val participants: List<Party> = listOf(sender, recipient)
) : LinearState, QueryableState {

  override fun generateMappedObject(schema: MappedSchema): PersistentState =
    MessageSchema.MessageEntity(
      sender = sender.name.toString(),
      type = type,
      id = linearId.id.toString()
    )

  override fun supportedSchemas(): Iterable<MappedSchema> = listOf(MessageSchema)
}

@CordaSerializable
object MessageSchema : MappedSchema(
  schemaFamily = MessageSchema::class.java,
  version = 1,
  mappedTypes = listOf(MessageEntity::class.java)
) {

  @Entity
  @Table(name = "messages")
  data class MessageEntity(
    @Column(name = "sender")
    val sender: String,
    @Column(name = "type")
    val type: String,
    @Column(name = "id")
    val id: String
  ) : PersistentState()
}